package re.jerome.mastersword.block;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import re.jerome.mastersword.item.MasterSwordItem;
import re.jerome.mastersword.registry.ModBlockEntities;

// What the pedestal remembers. Two pieces of state, and they are independent on
// purpose (SPEC 4.3): whether a sword is currently planted, and whether the fog
// has already been spent. The first goes back and forth as the sword is taken and
// returned; the second only ever flips once, and never back.
public class PedestalBlockEntity extends BlockEntity {
	private ItemStack sword = ItemStack.EMPTY;
	private boolean fogConsumed;

	public PedestalBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.PEDESTAL, pos, state);
	}

	public ItemStack getSword() {
		return this.sword;
	}

	public boolean hasSword() {
		return !this.sword.isEmpty();
	}

	public boolean isFogConsumed() {
		return this.fogConsumed;
	}

	/** Plants a sword. The stack is taken as-is, enchantments and damage included. */
	public void setSword(ItemStack stack) {
		this.sword = stack;
		this.sync();
	}

	/**
	 * Takes the sword out and spends the fog for good.
	 *
	 * Returning the stack rather than a boolean keeps the caller from having to
	 * read it first and clear it second, which is the shape that loses items.
	 */
	public ItemStack removeSword() {
		ItemStack taken = this.sword;
		this.sword = ItemStack.EMPTY;
		// Only drawing the Master Sword spends the fog. A pedestal used as
		// decoration, with an iron sword in it, has nothing to do with the legend.
		if (MasterSwordItem.isMasterSword(taken)) {
			this.fogConsumed = true;
		}

		this.sync();
		return taken;
	}

	// setChanged marks the chunk for saving; sendBlockUpdated is what actually
	// pushes getUpdatePacket to the clients watching this position. One without the
	// other gives either a sword that vanishes on reload or one that never appears.
	private void sync() {
		this.setChanged();
		if (this.level != null) {
			BlockState state = this.getBlockState();
			this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
		}
	}

	// The sword mends in the pedestal too. Item.inventoryTick never fires for a
	// stack held by a block entity, so the healing has to be driven from here --
	// otherwise resting the sword on its pedestal would be the one place it does
	// not recover.
	//
	// Only resynchronised when the durability actually moved. Regeneration converts
	// whole points, roughly one every 24 seconds at the defaults, so this is quiet
	// the rest of the time; pushing a packet every tick instead would cost one per
	// loaded pedestal per tick for nothing.
	//
	// Guarded on the item, not merely on emptiness: the pedestal takes any sword,
	// and regenerate would happily stamp its timestamp component onto an iron sword
	// and start mending that too.
	public static void serverTick(Level level, BlockPos pos, BlockState state, PedestalBlockEntity pedestal) {
		if (!MasterSwordItem.isMasterSword(pedestal.sword) || !(level instanceof ServerLevel serverLevel)) {
			return;
		}

		if (MasterSwordItem.regenerate(pedestal.sword, serverLevel)) {
			pedestal.sync();
		}
	}

	// Called by LevelChunk.setBlockState while this block entity is still readable,
	// which is why the sword is dropped from here rather than from the block's
	// affectNeighborsAfterRemoval -- that one runs after the block is already gone.
	// It is the same hook the lectern drops its book from.
	//
	// removeSword is deliberately not used: breaking the pedestal must not count as
	// drawing the sword, so the fog flag stays as it was.
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);

		if (this.level instanceof ServerLevel && !this.sword.isEmpty()) {
			ItemStack dropped = this.sword;
			this.sword = ItemStack.EMPTY;
			Block.popResource(this.level, pos, dropped);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		// OPTIONAL_CODEC rather than CODEC: CODEC rejects an empty stack outright,
		// which would make the empty pedestal the special case on both sides.
		output.store("sword", ItemStack.OPTIONAL_CODEC, this.sword);
		output.putBoolean("fog_consumed", this.fogConsumed);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.sword = input.read("sword", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		this.fogConsumed = input.getBooleanOr("fog_consumed", false);
	}

	// The client needs both: the stack to draw it, and the fog flag for step 9.
	// saveCustomOnly sends exactly what saveAdditional wrote, so there is no custom
	// packet to write and no second format to keep in step with the first.
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return this.saveCustomOnly(provider);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
