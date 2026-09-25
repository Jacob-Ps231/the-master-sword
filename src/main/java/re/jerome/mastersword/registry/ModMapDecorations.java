package re.jerome.mastersword.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import re.jerome.mastersword.MasterSwordMod;

public final class ModMapDecorations {
	/**
	 * The target mark of the cartographer's shrine map: the sword in its stone.
	 * No client code: the map_decorations atlas reads map/decorations in every
	 * namespace, so textures/map/decorations/shrine.png becomes the sprite
	 * mastersword:shrine on its own. Same flags as the mansion: shown in item
	 * frames, and not counted.
	 *
	 * <p>26.3 cut the record down to three components. The map tint went with
	 * them -- each explorer map is now an item of its own, and it is the item that
	 * carries the colour -- so our map is a plain filled map, named in the trade.
	 */
	public static final Holder<MapDecorationType> SHRINE = Registry.registerForHolder(
			BuiltInRegistries.MAP_DECORATION_TYPE,
			ResourceKey.create(Registries.MAP_DECORATION_TYPE, MasterSwordMod.id("shrine")),
			new MapDecorationType(MasterSwordMod.id("shrine"), true, false));

	private ModMapDecorations() {
	}

	public static void register() {
		// Registration happens in the field initialiser above. It has to happen
		// before any datapack is read: the cartographer trade names this type.
	}
}
