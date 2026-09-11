package re.jerome.mastersword.registry;

import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntityType;
import re.jerome.mastersword.MasterSwordMod;
import re.jerome.mastersword.block.PedestalBlockEntity;

// Minecraft 26.2 dropped BlockEntityType.Builder: the constructor is public and
// takes the supplier and the set of blocks the type is valid for. There is no
// FabricBlockEntityTypeBuilder to reach for either.
//
// Note the dependency on ModBlocks -- reading ModBlocks.PEDESTAL here forces that
// class to initialise first, which is the order the registries need anyway.
public final class ModBlockEntities {
	public static final BlockEntityType<PedestalBlockEntity> PEDESTAL = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, MasterSwordMod.id("master_sword_pedestal")),
			new BlockEntityType<>(PedestalBlockEntity::new, Set.of(ModBlocks.PEDESTAL)));

	private ModBlockEntities() {
	}

	public static void register() {
		// Registration happens in the field initialiser above; this only forces the
		// class to load, at a point in onInitialize we control.
	}
}
