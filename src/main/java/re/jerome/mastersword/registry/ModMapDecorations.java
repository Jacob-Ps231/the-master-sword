package re.jerome.mastersword.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import re.jerome.mastersword.MasterSwordMod;

public final class ModMapDecorations {
	/** The woodland mansion's map tint, so our map reads as the same kind of treasure. */
	private static final int MANSION_MAP_COLOR = 5393476;

	/**
	 * The target mark of the cartographer's shrine map: the sword in its stone.
	 * No client code: the map_decorations atlas reads map/decorations in every
	 * namespace, so textures/map/decorations/shrine.png becomes the sprite
	 * mastersword:shrine on its own. Same flags as the mansion: shown in item
	 * frames, not counted, and marked as an exploration target.
	 */
	public static final Holder<MapDecorationType> SHRINE = Registry.registerForHolder(
			BuiltInRegistries.MAP_DECORATION_TYPE,
			ResourceKey.create(Registries.MAP_DECORATION_TYPE, MasterSwordMod.id("shrine")),
			new MapDecorationType(MasterSwordMod.id("shrine"), true, MANSION_MAP_COLOR, true, false));

	private ModMapDecorations() {
	}

	public static void register() {
		// Registration happens in the field initialiser above. It has to happen
		// before any datapack is read: the cartographer trade names this type.
	}
}
