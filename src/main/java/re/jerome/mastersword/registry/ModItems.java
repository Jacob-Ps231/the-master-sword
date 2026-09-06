package re.jerome.mastersword.registry;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.enchantment.Repairable;

public final class ModItems {
	// CreativeModeTabs keeps its tab keys private, so the vanilla one is rebuilt here.
	private static final ResourceKey<CreativeModeTab> COMBAT = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));

	// Same stats as the netherite sword: sword() sets attack damage (3.0 + 4.0),
	// attack speed (-2.4), durability (2031), enchantability and the TOOL/WEAPON
	// components in one call.
	public static final Item MASTER_SWORD = Registry.register(
			BuiltInRegistries.ITEM,
			ModItemIds.MASTER_SWORD,
			new Item(new Item.Properties()
					.sword(ToolMaterial.NETHERITE, 3.0F, -2.4F)
					.fireResistant()
					.rarity(Rarity.EPIC)
					// sword() also set REPAIRABLE to the netherite ingot tag, which the
					// spec forbids. component() chains a map set, so the last call wins:
					// an empty holder set makes isValidRepairItem false for every
					// material. Combining two Master Swords is unaffected -- anvil,
					// grindstone and the repair_item recipe never look at REPAIRABLE.
					.component(DataComponents.REPAIRABLE, new Repairable(HolderSet.empty()))
					.setId(ModItemIds.MASTER_SWORD)));

	private ModItems() {
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(COMBAT)
				.register(output -> output.insertAfter(Items.NETHERITE_SWORD, MASTER_SWORD));
	}
}
