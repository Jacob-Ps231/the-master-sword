package re.jerome.mastersword.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import re.jerome.mastersword.MasterSwordMod;
import re.jerome.mastersword.entity.LightWaveEntity;

public final class ModEntityTypes {
	// Tracking values follow the vanilla fast projectiles: LLAMA_SPIT uses range 4
	// and interval 10, ARROW range 4 and interval 20. The wave lives about ten
	// ticks, so it is tracked a little further and updated every other tick.
	public static final EntityType<LightWaveEntity> LIGHT_WAVE = create(
			"light_wave",
			EntityType.Builder.<LightWaveEntity>of(LightWaveEntity::new, MobCategory.MISC)
					.noLootTable()
					.noSummon()
					.fireImmune()
					.sized(0.6F, 0.6F)
					.clientTrackingRange(8)
					.updateInterval(2));

	private ModEntityTypes() {
	}

	public static void register() {
		// Registration happens in the field initialiser above; this only forces the
		// class to load, at a point in onInitialize we control.
	}

	private static <T extends Entity> EntityType<T> create(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MasterSwordMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}
}
