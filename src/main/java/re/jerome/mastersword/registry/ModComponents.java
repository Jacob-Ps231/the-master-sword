package re.jerome.mastersword.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import re.jerome.mastersword.MasterSwordMod;

public final class ModComponents {
	/**
	 * World-clock instant of the last blow the sword landed, from which durability
	 * regeneration is credited. Absent means the sword is at rest.
	 */
	public static final DataComponentType<Long> LAST_COMBAT_USE = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			MasterSwordMod.id("last_combat_use"),
			DataComponentType.<Long>builder()
					.persistent(Codec.LONG)
					.networkSynchronized(ByteBufCodecs.VAR_LONG)
					// Without this, ItemInHandRenderer replays the lower/raise animation
					// every time the value changes while the sword is held: it compares
					// stacks with matchesIgnoringComponents(.., ignoreSwapAnimation).
					// Vanilla flags DAMAGE for the same reason.
					.ignoreSwapAnimation()
					.build());

	private ModComponents() {
	}

	public static void register() {
		// Registration happens in the field initialiser above; this only forces the
		// class to load, at a point in onInitialize we control.
	}
}
