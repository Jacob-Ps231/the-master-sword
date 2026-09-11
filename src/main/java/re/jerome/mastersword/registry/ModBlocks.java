package re.jerome.mastersword.registry;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import re.jerome.mastersword.MasterSwordMod;
import re.jerome.mastersword.block.PedestalBlock;

public final class ModBlocks {
	private static final ResourceKey<CreativeModeTab> COMBAT = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));

	public static final ResourceKey<Block> PEDESTAL_ID = ResourceKey.create(
			Registries.BLOCK, MasterSwordMod.id("master_sword_pedestal"));
	public static final ResourceKey<Item> PEDESTAL_ITEM_ID = ResourceKey.create(
			Registries.ITEM, MasterSwordMod.id("master_sword_pedestal"));

	// Obsidian-hard on purpose: the pedestal stands in a structure players will
	// find long before they can mine it comfortably, and it should not be knocked
	// out with a wooden pickaxe on the way past.
	public static final Block PEDESTAL = Registry.register(
			BuiltInRegistries.BLOCK,
			PEDESTAL_ID,
			new PedestalBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.DEEPSLATE)
					.strength(25.0F, 1200.0F)
					.requiresCorrectToolForDrops()
					.sound(SoundType.DEEPSLATE)
					// The shape is two boxes, not a full cube: without this the faces
					// of the blocks around it would be culled and the world would show
					// through the gaps.
					.noOcclusion()
					.setId(PEDESTAL_ID)));

	public static final Item PEDESTAL_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			PEDESTAL_ITEM_ID,
			new BlockItem(PEDESTAL, new Item.Properties().useBlockDescriptionPrefix().setId(PEDESTAL_ITEM_ID)));

	private ModBlocks() {
	}

	public static void register() {
		// Next to the sword rather than in the building blocks: the two are one
		// feature, and this is where they will be looked for.
		CreativeModeTabEvents.modifyOutputEvent(COMBAT)
				.register(output -> output.insertAfter(ModItems.MASTER_SWORD, PEDESTAL_ITEM));
	}
}
