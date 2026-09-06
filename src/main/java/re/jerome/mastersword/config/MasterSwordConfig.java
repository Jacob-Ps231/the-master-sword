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
public record MasterSwordConfig(ItemConfig item) {
	public static final MasterSwordConfig DEFAULTS = new MasterSwordConfig(ItemConfig.DEFAULTS);

	public static final Codec<MasterSwordConfig> READ_CODEC = codec(false);
	private static final Codec<MasterSwordConfig> WRITE_CODEC = codec(true);

	private static Codec<MasterSwordConfig> codec(boolean everyField) {
		return RecordCodecBuilder.create(
				i -> i.group(field(ItemConfig.codec(everyField), "item", ItemConfig.DEFAULTS, everyField)
								.forGetter(MasterSwordConfig::item))
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
		"Reloaded live by /mastersword reload: max_durability, unbreakable.",
		"Applied only on restart: attack_damage, attack_speed,",
		"repairable_with_netherite_ingot -- they are baked onto the item when it",
		"is registered.",
		"attack_damage is the total, the same number the tooltip shows minus the",
		"1.0 every player has bare-handed.",
		"",
		"Careful with max_durability: lowering it below the damage a sword has",
		"already taken makes that sword read as broken, and the next hit writes",
		"the clamped value and destroys it. Raise it back before using the sword",
		"and nothing is lost.",
		"unbreakable also stops two swords being combined in an anvil, a",
		"grindstone or the crafting grid, since none of them accepts an item that",
		"reports itself as undamageable.",
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
			boolean unbreakable,
			boolean repairableWithNetheriteIngot) {
		public static final ItemConfig DEFAULTS = new ItemConfig(7.0F, -2.4F, 2031, false, false);

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
									field(Codec.BOOL, "unbreakable",
											DEFAULTS.unbreakable, everyField)
											.forGetter(ItemConfig::unbreakable),
									field(Codec.BOOL, "repairable_with_netherite_ingot",
											DEFAULTS.repairableWithNetheriteIngot, everyField)
											.forGetter(ItemConfig::repairableWithNetheriteIngot))
							.apply(i, ItemConfig::new));
		}
	}
}
