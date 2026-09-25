package re.jerome.mastersword.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import re.jerome.mastersword.item.MasterSwordItem;
import re.jerome.mastersword.registry.ModBlockEntities;
import re.jerome.mastersword.registry.ModCriteria;

// The pedestal the Master Sword is planted in.
//
// No condition to take the sword and none to put it back (SPEC 4.3): the block is
// a holder, not a lock. What it does gate is *what* it holds -- anything in
// #minecraft:swords and nothing else, so the pedestal never becomes a one-slot
// chest while still being useful as decoration with an iron or diamond sword.
public class PedestalBlock extends BaseEntityBlock {
	// Borrowed from HorizontalDirectionalBlock rather than declared afresh: that
	// class extends Block, so a BaseEntityBlock cannot inherit from it, but the
	// property itself is public and shared by every vanilla facing block.
	//
	// The stone is symmetrical, so the facing shows only in the sword: it is what
	// lets a row of pedestals point their blades different ways.
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	// A thick slab with a narrower shaft on top. Two boxes rather than one so the
	// silhouette reads as a pedestal from a distance, and so a player can stand
	// beside it without being pushed by a full-width block.
	private static final VoxelShape SHAPE = Shapes.or(
			Block.box(1.0, 0.0, 1.0, 15.0, 4.0, 15.0),
			Block.box(3.0, 4.0, 3.0, 13.0, 12.0, 13.0));

	public PedestalBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	// Opposite of where the player is looking, so the blade faces them as they
	// place it -- the same convention as furnaces and chests.
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	// Without these two a pedestal inside a structure would ignore the structure's
	// own rotation, which matters from step 8 on.
	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PedestalBlockEntity(pos, state);
	}

	// Server side only: the ticker heals the sword, and durability written on a
	// client copy would be overwritten by the next sync anyway.
	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
			Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide()
				? null
				: createTickerHelper(type, ModBlockEntities.PEDESTAL, PedestalBlockEntity::serverTick);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	// Right-click with something in hand. Runs *before* useWithoutItem, and the two
	// are chained by exactly one value: ServerPlayerGameMode only calls
	// useWithoutItem when this returns an InteractionResult.TryEmptyHandInteraction.
	// PASS does *not* fall through -- it would end the interaction here and make an
	// occupied pedestal impossible to empty while holding anything at all.
	//
	// TRY_WITH_EMPTY_HAND is also the vanilla default for a block that does not
	// override this, so handing back the cases we do not claim keeps the pedestal
	// behaving like a chest: the block's own interaction wins over placing a block
	// against it, and sneaking still bypasses both.
	@Override
	protected InteractionResult useItemOn(
			ItemStack stack,
			BlockState state,
			Level level,
			BlockPos pos,
			Player player,
			InteractionHand hand,
			BlockHitResult hit) {
		// Any sword, not just the Master Sword: a pedestal holding an iron or a
		// diamond sword is worth having as decoration. #minecraft:swords is the
		// vanilla tag, and the Master Sword was added to it back at step 2 -- so
		// this covers it without a special case, and covers other mods' swords too.
		if (!stack.is(ItemTags.SWORDS)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		if (!(level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal) || pedestal.hasSword()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		if (level.isClientSide()) {
			// CONSUME, not SUCCESS: SUCCESS carries SwingSource.CLIENT and SUCCESS_SERVER
			// carries SwingSource.SERVER, so returning both would swing the arm twice --
			// once predicted locally, once broadcast back. Vanilla pairs CONSUME on the
			// client with SUCCESS_SERVER on the server, as CampfireBlock does.
			return InteractionResult.CONSUME;
		}

		// In creative the sword stays in hand, the way an item frame leaves the
		// creative player's stack alone; in survival it leaves the hand for good.
		pedestal.setSword(player.hasInfiniteMaterials() ? stack.copyWithCount(1) : stack.split(1));
		// The same stone sound for every sword, the Master Sword included. A second
		// note layered on top for the legend was tried and dropped: putting the
		// sword back is not an event, and the chime read as a glitch (Jérôme,
		// 12/09/2026). Drawing it is where the ceremony belongs.
		level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
		return InteractionResult.SUCCESS_SERVER;
	}

	// Right-click bare-handed: take the sword. No level required, no quest, no
	// prerequisite -- and this is the moment the fog is spent for good.
	@Override
	protected InteractionResult useWithoutItem(
			BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal) || !pedestal.hasSword()) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			// CONSUME, not SUCCESS: SUCCESS carries SwingSource.CLIENT and SUCCESS_SERVER
			// carries SwingSource.SERVER, so returning both would swing the arm twice --
			// once predicted locally, once broadcast back. Vanilla pairs CONSUME on the
			// client with SUCCESS_SERVER on the server, as CampfireBlock does.
			return InteractionResult.CONSUME;
		}

		// Not placeItemBackInInventory: its no-room path is Player.drop, which spawns
		// the item at the player's feet -- in tall grass, behind the camera, easy to
		// read as the sword having simply vanished. popResource puts it on the
		// pedestal instead, where the player is already looking. The sword is never
		// destroyed either way; this only decides where it lands.
		ItemStack taken = pedestal.removeSword();
		// Read before handing the stack over. Inventory.add EMPTIES what it is
		// given -- copyAndClear on the normal path, setCount(0) in creative -- and
		// ItemStack.getItem() answers AIR as soon as the count reaches zero. Asking
		// afterwards whether this was the Master Sword therefore always says no, and
		// the ceremony below, advancement included, is silently skipped.
		boolean legendary = MasterSwordItem.isMasterSword(taken);
		ItemStack drawn = taken.copy();

		if (!player.getInventory().add(taken)) {
			Block.popResource(level, pos, taken);
		}

		level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 1.4F);

		// Only the Master Sword gets the ceremony. The pedestal takes any sword
		// (SPEC 4.3), and an iron one leaving it is furniture being moved, not a
		// legend waking: it keeps the plain stone sound above and nothing else.
		if (legendary) {
			level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
			if (level instanceof ServerLevel serverLevel) {
				PedestalBlockEntity.drawnBurst(serverLevel, pos);
			}

			// The copy, not `taken`: the advancement's item predicate has to see a
			// real stack, and by now the original has been emptied into the inventory.
			if (player instanceof ServerPlayer serverPlayer) {
				ModCriteria.DRAWN_FROM_STONE.trigger(serverPlayer, drawn);
			}
		}

		return InteractionResult.SUCCESS_SERVER;
	}

	// Dropping the sword when the pedestal is broken lives in
	// PedestalBlockEntity.preRemoveSideEffects, not here. affectNeighborsAfterRemoval
	// runs once the block is already gone -- vanilla only updates neighbours from
	// it -- whereas preRemoveSideEffects is called by LevelChunk.setBlockState while
	// the block entity is still readable, which is where the lectern drops its book
	// and the jukebox its record.
}
