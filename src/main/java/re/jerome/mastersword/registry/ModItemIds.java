package re.jerome.mastersword.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import re.jerome.mastersword.MasterSwordMod;

// Minecraft stores item ids separately from the items themselves; vanilla does
// the same in net.minecraft.references.ItemIds. The key is needed twice: once for
// Registry.register, once for Item.Properties.setId.
public final class ModItemIds {
	public static final ResourceKey<Item> MASTER_SWORD = create("master_sword");
	public static final ResourceKey<Item> SHRINE_MAP = create("shrine_map");

	private ModItemIds() {
	}

	private static ResourceKey<Item> create(String name) {
		return ResourceKey.create(Registries.ITEM, MasterSwordMod.id(name));
	}
}
