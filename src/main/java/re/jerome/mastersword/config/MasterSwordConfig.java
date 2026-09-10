package re.jerome.mastersword.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.StrictJsonParser;
import re.jerome.mastersword.MasterSwordMod;

// Gameplay values live here rather than scattered as constants.
//
// A Codec rather than plain Gson: it tells a missing key apart from a key set to
// zero, which Gson cannot do on a primitive field. That matters for a file people
// edit by hand -- an omitted max_durability must fall back to the default, not to
// zero.
//
// Reading and writing use two codecs built from the same field list. The reading
// one uses optionalFieldOf so a partial file still loads; the writing one uses
// fieldOf, because optionalFieldOf *omits* a field whose value equals the default
// and would hand the player an empty file with nothing to edit.
public record MasterSwordConfig(ItemConfig item, LightWaveConfig lightWave) {
	public static final MasterSwordConfig DEFAULTS = new MasterSwordConfig(ItemConfig.DEFAULTS, LightWaveConfig.DEFAULTS);

	public static final Codec<MasterSwordConfig> READ_CODEC = codec(false);
	private static final Codec<MasterSwordConfig> WRITE_CODEC = codec(true);

	private static Codec<MasterSwordConfig> codec(boolean everyField) {
		return RecordCodecBuilder.create(
				i -> i.group(
								field(ItemConfig.codec(everyField), "item", ItemConfig.DEFAULTS, everyField)
										.forGetter(MasterSwordConfig::item),
								field(LightWaveConfig.codec(everyField), "light_wave", LightWaveConfig.DEFAULTS, everyField)
										.forGetter(MasterSwordConfig::lightWave))
						.apply(i, MasterSwordConfig::new));
	}

	private static <T> MapCodec<T> field(Codec<T> codec, String name, T fallback, boolean everyField) {
		return everyField ? codec.fieldOf(name) : codec.optionalFieldOf(name, fallback);
	}

	// JSON has no comments, so the file carries its own explanation as a key the
	// reading codec simply ignores.
	private static final String[] COMMENT = {
		"The Master Sword -- configuration.",
		"Delete this file to regenerate it with the default values.",
		"Reloaded live by /mastersword reload: max_durability, unbreakable,",
		"full_regen_days, regen_starts_below, and every light_wave value.",
		"Applied only on restart: attack_damage, attack_speed,",
		"repairable_with_netherite_ingot -- they are baked onto the item when it",
		"is registered.",
		"attack_damage is the total, the same number the tooltip shows minus the",
		"1.0 every player has bare-handed.",
		"",
		"Careful with max_durability: lowering it below the damage a sword has",
		"already taken makes that sword read as broken, and regeneration then",
		"writes that clamped value within seconds -- no action from you needed --",
		"which loses the original damage for good. Raise the value back straight",
		"away if you lowered it by mistake.",
		"unbreakable also stops two swords being combined in an anvil, a",
		"grindstone or the crafting grid, since none of them accepts an item that",
		"reports itself as undamageable.",
		"",
		"full_regen_days counts world-clock days, not real time: sleeping through",
		"a night counts towards it, and a clock stopped by the doDaylightCycle",
		"game rule stops regeneration entirely. One day is 24000 ticks.",
		"Keep max_durability below full_regen_days * 1200 or so: regeneration",
		"rounds to whole ticks, so a very short full_regen_days with a high",
		"max_durability ends up noticeably slower than asked. The defaults are",
		"well inside that.",
		"",
		"regen_starts_below is a fraction of max durability: the sword only begins",
		"mending once it drops to that share or below, and then goes back to full.",
		"1.0 makes it always mend, 0.0 never.",
		"",
		"light_wave is the beam a fully charged swing throws. Every value here is",
		"read live, so /mastersword reload applies them at once. damage_ratio is a",
		"fraction of the sword melee damage; range and width are in blocks, speed",
		"in blocks per tick. Set enabled to false to switch the beam off.",
		"requires_full_health withholds the beam unless the player is at full",
		"hearts.",
	};

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static MasterSwordConfig current = DEFAULTS;

	public static MasterSwordConfig get() {
		return current;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(MasterSwordMod.MOD_ID + ".json");
	}

	/** Reads the file, falling back to defaults, then writes it back normalised. */
	public static void load() {
		Path path = path();
		MasterSwordConfig read = DEFAULTS;

		if (Files.exists(path)) {
			try (BufferedReader reader = Files.newBufferedReader(path)) {
				// A hand-edited file must never stop the mod from loading, so a bad
				// value logs and falls back rather than propagating. Note that
				// intRange and floatRange reject an out-of-range value outright,
				// they do not clamp it.
				read = READ_CODEC.parse(JsonOps.INSTANCE, StrictJsonParser.parse(reader))
						.resultOrPartial(error -> MasterSwordMod.LOGGER.warn("Bad value in {}: {}", path, error))
						.orElse(DEFAULTS);
			} catch (IOException | JsonParseException e) {
				MasterSwordMod.LOGGER.warn("Unreadable config at {}, using defaults", path, e);
			}
		}

		current = read;
		save(path);
	}

	private static void save(Path path) {
		WRITE_CODEC.encodeStart(JsonOps.INSTANCE, current)
				.resultOrPartial(error -> MasterSwordMod.LOGGER.warn("Could not encode config: {}", error))
				.ifPresent(encoded -> {
					JsonObject out = new JsonObject();
					out.add("_comment", GSON.toJsonTree(COMMENT));
					for (var entry : encoded.getAsJsonObject().entrySet()) {
						out.add(entry.getKey(), entry.getValue());
					}

					try {
						Files.createDirectories(path.getParent());
						Files.writeString(path, GSON.toJson((JsonElement) out));
					} catch (IOException e) {
						MasterSwordMod.LOGGER.warn("Could not write config to {}", path, e);
					}
				});
	}

	public record ItemConfig(
			float attackDamage,
			float attackSpeed,
			int maxDurability,
			float fullRegenDays,
			float regenStartsBelow,
			boolean unbreakable,
			boolean repairableWithNetheriteIngot) {
		public static final ItemConfig DEFAULTS = new ItemConfig(7.0F, -2.4F, 2031, 2.0F, 0.5F, false, false);

		// The bounds live in the schema. max_durability starts at 1, never 0:
		// Item.getBarWidth divides by it.
		static Codec<ItemConfig> codec(boolean everyField) {
			return RecordCodecBuilder.create(
					i -> i.group(
									field(Codec.floatRange(0.0F, 2048.0F), "attack_damage",
											DEFAULTS.attackDamage, everyField)
											.forGetter(ItemConfig::attackDamage),
									// Not -4.0: that cancels the ATTACK_SPEED attribute outright,
									// the attack bar would never refill, and the charged attack
									// of step 6 would be unreachable.
									field(Codec.floatRange(-3.9F, 4.0F), "attack_speed",
											DEFAULTS.attackSpeed, everyField)
											.forGetter(ItemConfig::attackSpeed),
									field(Codec.intRange(1, 65535), "max_durability",
											DEFAULTS.maxDurability, everyField)
											.forGetter(ItemConfig::maxDurability),
									// Floor at 0.1 rather than 0: the value is a divisor.
									field(Codec.floatRange(0.1F, 1000.0F), "full_regen_days",
											DEFAULTS.fullRegenDays, everyField)
											.forGetter(ItemConfig::fullRegenDays),
									// 1.0 makes the sword always heal, 0.0 never.
									field(Codec.floatRange(0.0F, 1.0F), "regen_starts_below",
											DEFAULTS.regenStartsBelow, everyField)
											.forGetter(ItemConfig::regenStartsBelow),
									field(Codec.BOOL, "unbreakable",
											DEFAULTS.unbreakable, everyField)
											.forGetter(ItemConfig::unbreakable),
									field(Codec.BOOL, "repairable_with_netherite_ingot",
											DEFAULTS.repairableWithNetheriteIngot, everyField)
											.forGetter(ItemConfig::repairableWithNetheriteIngot))
							.apply(i, ItemConfig::new));
		}
	}

	public record LightWaveConfig(
			boolean enabled,
			float cooldownSeconds,
			float range,
			float speed,
			float damageRatio,
			float width,
			int durabilityCost,
			boolean requiresFullHealth) {
		public static final LightWaveConfig DEFAULTS = new LightWaveConfig(true, 15.0F, 12.0F, 1.2F, 0.6F, 1.5F, 1, true);

		static Codec<LightWaveConfig> codec(boolean everyField) {
			return RecordCodecBuilder.create(
					i -> i.group(
									field(Codec.BOOL, "enabled",
											DEFAULTS.enabled, everyField)
											.forGetter(LightWaveConfig::enabled),
									field(Codec.floatRange(0.0F, 3600.0F), "cooldown_seconds",
											DEFAULTS.cooldownSeconds, everyField)
											.forGetter(LightWaveConfig::cooldownSeconds),
									// Range is what ends the wave, so it cannot be zero.
									field(Codec.floatRange(1.0F, 256.0F), "range",
											DEFAULTS.range, everyField)
											.forGetter(LightWaveConfig::range),
									// Above ~8 blocks a tick the wave steps over thin targets.
									field(Codec.floatRange(0.1F, 8.0F), "speed",
											DEFAULTS.speed, everyField)
											.forGetter(LightWaveConfig::speed),
									field(Codec.floatRange(0.0F, 10.0F), "damage_ratio",
											DEFAULTS.damageRatio, everyField)
											.forGetter(LightWaveConfig::damageRatio),
									field(Codec.floatRange(0.1F, 16.0F), "width",
											DEFAULTS.width, everyField)
											.forGetter(LightWaveConfig::width),
									field(Codec.intRange(0, 1000), "durability_cost",
											DEFAULTS.durabilityCost, everyField)
											.forGetter(LightWaveConfig::durabilityCost),
									field(Codec.BOOL, "requires_full_health",
											DEFAULTS.requiresFullHealth, everyField)
											.forGetter(LightWaveConfig::requiresFullHealth))
							.apply(i, LightWaveConfig::new));
		}

		public int cooldownTicks() {
			return Math.round(this.cooldownSeconds * 20.0F);
		}
	}
}
