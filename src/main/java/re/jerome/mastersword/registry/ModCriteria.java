package re.jerome.mastersword.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import re.jerome.mastersword.MasterSwordMod;
import re.jerome.mastersword.advancement.DrawnFromStoneTrigger;

public final class ModCriteria {
	/**
	 * Registered into the same registry vanilla bootstraps its own triggers into.
	 * CriteriaTriggers.register is private, but the registry behind it is public,
	 * so no mixin is needed.
	 */
	public static final DrawnFromStoneTrigger DRAWN_FROM_STONE = Registry.register(
			BuiltInRegistries.TRIGGER_TYPES,
			MasterSwordMod.id("drawn_from_stone"),
			new DrawnFromStoneTrigger());

	private ModCriteria() {
	}

	public static void register() {
		// Registration happens in the field initialiser above; this only forces the
		// class to load, at a point in onInitialize we control. It has to happen
		// before any datapack is read, or the advancement would name a trigger the
		// registry does not know yet.
	}
}
