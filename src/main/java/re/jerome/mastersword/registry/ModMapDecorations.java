package re.jerome.mastersword.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import re.jerome.mastersword.MasterSwordMod;

public final class ModMapDecorations {
	/** The woodland mansion's map tint, so our map reads as the same kind of treasure. */
	private static final int MANSION_MAP_COLOR = 5393476;

	/**
	 * The target mark of the cartographer's shrine map. Its own type only for the
	 * tint: vanilla's red_x has none. The sprite is vanilla's red cross until the
	 * shrine gets its own icon; the map_decorations atlas reads map/decorations in
	 * every namespace, so that swap is a PNG and this identifier, no client code.
	 * Same flags as the mansion: shown in item frames, not counted, and marked as
	 * an exploration target.
	 */
	public static final Holder<MapDecorationType> SHRINE = Registry.registerForHolder(
			BuiltInRegistries.MAP_DECORATION_TYPE,
			ResourceKey.create(Registries.MAP_DECORATION_TYPE, MasterSwordMod.id("shrine")),
			new MapDecorationType(Identifier.withDefaultNamespace("red_x"), true, MANSION_MAP_COLOR, true, false));

	private ModMapDecorations() {
	}

	public static void register() {
		// Registration happens in the field initialiser above. It has to happen
		// before any datapack is read: the cartographer trade names this type.
	}
}
