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
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.enchantment.Repairable;
import re.jerome.mastersword.config.MasterSwordConfig;
import re.jerome.mastersword.item.MasterSwordItem;

public final class ModItems {
	// CreativeModeTabs keeps its tab keys private, so the vanilla ones are rebuilt here.
	private static final ResourceKey<CreativeModeTab> COMBAT = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));
	private static final ResourceKey<CreativeModeTab> TOOLS = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));

	// Same shape as the netherite sword: sword() sets attack damage, attack speed,
	// durability, enchantability and the TOOL/WEAPON components in one call.
	//
	// The config values are read here, at class initialisation, which is why
	// MasterSwordMod.onInitialize loads the config before touching this class.
	// They are therefore frozen until the next restart -- unlike max durability,
	// which ItemStackMixin re-reads on every call so /mastersword reload can move
	// it live.
	public static final Item MASTER_SWORD = Registry.register(
			BuiltInRegistries.ITEM,
			ModItemIds.MASTER_SWORD,
			new MasterSwordItem(masterSwordProperties()));

	/**
	 * The map the cartographer sells. 26.3 made every explorer map an item of its
	 * own -- woodland_mansion_map, ocean_monument_map and the rest -- and
	 * ExplorationMapFunction now writes the map data onto whatever item the trade
	 * gives, instead of turning a blank map into a filled one. So the shrine map
	 * is an item too, with its own name and its own picture.
	 *
	 * <p>Built like vanilla's, read off Items: a MapItem whose properties carry an
	 * empty MAP_DECORATIONS component.
	 */
	public static final Item SHRINE_MAP = Registry.register(
			BuiltInRegistries.ITEM,
			ModItemIds.SHRINE_MAP,
			new MapItem(new Item.Properties()
					.component(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY)
					.setId(ModItemIds.SHRINE_MAP)));

	private static Item.Properties masterSwordProperties() {
		MasterSwordConfig.ItemConfig cfg = MasterSwordConfig.get().item();

		// sword() computes attack damage as baseline + the material's bonus, so the
		// bonus is subtracted here to let the config state the total instead.
		Item.Properties properties = new Item.Properties()
				.sword(ToolMaterial.NETHERITE,
						cfg.attackDamage() - ToolMaterial.NETHERITE.attackDamageBonus(),
						cfg.attackSpeed())
				.durability(cfg.maxDurability())
				.fireResistant()
				.rarity(Rarity.EPIC);

		// sword() also set REPAIRABLE to the netherite ingot tag, which the spec
		// forbids by default. component() chains a map set, so the last call wins:
		// an empty holder set makes isValidRepairItem false for every material.
		// Combining two Master Swords is unaffected either way -- anvil, grindstone
		// and the repair_item recipe never look at REPAIRABLE.
		if (!cfg.repairableWithNetheriteIngot()) {
			properties = properties.component(DataComponents.REPAIRABLE, new Repairable(HolderSet.empty()));
		}

		return properties.setId(ModItemIds.MASTER_SWORD);
	}

	private ModItems() {
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(COMBAT)
				.register(output -> output.insertAfter(Items.NETHERITE_SWORD, MASTER_SWORD));
		// Beside the explorer maps it belongs with.
		CreativeModeTabEvents.modifyOutputEvent(TOOLS)
				.register(output -> output.insertAfter(Items.WOODLAND_MANSION_MAP, SHRINE_MAP));
	}
}
