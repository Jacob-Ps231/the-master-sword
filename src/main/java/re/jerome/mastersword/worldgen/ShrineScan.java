package re.jerome.mastersword.worldgen;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import re.jerome.mastersword.MasterSwordMod;

/**
 * Measures how often the shrine lands on the edge of its biome.
 *
 * <p>Jerome has reported three times that the sword sits against a river or a
 * shore. The cause is that {@code Structure.isValidBiome} tests exactly one
 * 4x4x4 cell, while the shrine is 17 blocks across.
 *
 * <p>⚠️ Which cell, precisely, is not what SPEC said before 14/09/2026. The
 * north-west corner of the chunk is only the {@code position} argument
 * {@code JigsawStructure.findGenerationPoint} hands to
 * {@code JigsawPlacement.addPieces}; the {@code GenerationStub} it gets back is
 * built on {@code (maxX + minX) / 2} and {@code (maxZ + minZ) / 2} of the start
 * piece's box -- the **centre of the footprint**, which for our 17x17 clearing
 * lands 8 blocks in from that corner. Measured on the known shrine: pedestal at
 * -4295 / -5356, in the chunk starting at -4304 / -5360, tested cell at
 * -4296 / -5352. The biome is therefore judged at the middle of the shrine, and
 * everything within 8 blocks of it is unexamined.
 *
 * <p>What was missing is not the cause but the rate -- one measured shrine is
 * not a rate, and "too often" is not a number.
 *
 * <p>SPEC planned to pregenerate dark forest chunks and rerun
 * {@code tools/structure/biome_edge.js} over them. That is abandoned here, and
 * the reason is arithmetic: a placement cell is 72x72 chunks and yields one
 * candidate, most of which fall outside a dark forest. A hundred shrines would
 * cost on the order of a million generated chunks. None of it is needed --
 * placement is decided from the seed and the biome noise, so it can be asked
 * directly, without generating a single chunk.
 *
 * <p><b>Nothing about the decision is reimplemented here.</b> Candidates come
 * from {@code getPotentialStructureChunk} and {@code isStructureChunk}, and the
 * verdict comes from {@code Structure.generate} -- the very method the chunk
 * generator calls, so the mod's own biome rule
 * ({@link re.jerome.mastersword.mixin.StructureBiomeMarginMixin}) is part of the
 * answer. A candidate is then resolved once more with an always-true biome
 * predicate, purely to obtain the anchor and footprint the game would have used
 * -- without them a refused candidate would have no geometry to be scored on.
 *
 * <p>The report carries a <b>control</b>: the rule rebuilt here from its two
 * halves, compared to the game's verdict on every candidate. One disagreement
 * and the report says so, in those words, because no other number in it would
 * mean anything.
 *
 * <p>Development only: this is an instrument, it has no place in the published
 * mod. {@link re.jerome.mastersword.command.MasterSwordCommand} registers it
 * behind {@code FabricLoader.isDevelopmentEnvironment()}.
 */
public final class ShrineScan {
	private static final ResourceKey<StructureSet> SET =
			ResourceKey.create(Registries.STRUCTURE_SET, MasterSwordMod.id("master_sword"));

	/** Radius in placement cells. 60 is 121x121 cells, about 69 000 blocks each way. */
	private static final int DEFAULT_RADIUS = 60;

	/**
	 * How far to look for a foreign biome, in biome cells -- 64 cells, 256
	 * blocks. Same constant and same Chebyshev ring walk as
	 * {@code tools/structure/biome_edge.js}, so the two instruments can be
	 * compared number for number.
	 */
	private static final int SEARCH_CELLS = 64;

	/** Below this, in blocks, a shrine counts as sitting on the edge. */
	private static final int NEAR = 16;

	/** The margins the "marge" rules require, in blocks. */
	private static final int[] MARGINS = {8, 16, 24, 32};

	private static final int[] BUCKETS = {8, 16, 32, 64, 128};

	/**
	 * The rules being compared. The first is what the game actually does, mod
	 * included; the "marge" rows are what a further margin on top of it would
	 * still cost.
	 *
	 * <p>A "marge N" row with N >= {@link #NEAR} has nothing left on the edge by
	 * construction -- that is arithmetic, not a result. What those rows are for
	 * is the other column: how many shrines the margin costs.
	 */
	private static final String[] RULES = {"le jeu, mod compris", "controle",
			"marge 8", "marge 16", "marge 24", "marge 32"};

	private ShrineScan() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("scan")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(context -> start(context.getSource(), DEFAULT_RADIUS))
				.then(Commands.argument("rayon", IntegerArgumentType.integer(1, 400))
						.executes(context ->
								start(context.getSource(), IntegerArgumentType.getInteger(context, "rayon"))));
	}

	/**
	 * Headless entry point: {@code MASTERSWORD_SCAN=60 ./gradlew runServer}
	 * scans, writes its report and stops the server.
	 *
	 * <p>It exists because the command cannot be used headlessly. On the Loom dev
	 * dedicated server every console command fails with "an unexpected error
	 * occurred while trying to execute that command" and no stack trace --
	 * vanilla {@code list} and {@code seed} included, checked with this mod's
	 * command tree removed from the build. So the scan is reached through a
	 * lifecycle hook, which never touches the dispatcher.
	 */
	public static void installHeadlessRun() {
		if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
			return;
		}

		String requested = System.getenv("MASTERSWORD_SCAN");
		if (requested == null) {
			return;
		}

		int radius;
		try {
			radius = Integer.parseInt(requested.trim());
		} catch (NumberFormatException error) {
			// Never take the game down over a typo in a measuring tool.
			MasterSwordMod.LOGGER.error("MASTERSWORD_SCAN attend un rayon en cellules, pas {}", requested);
			return;
		}

		// Off the server thread even here, where nobody is playing: a wide scan
		// takes minutes, and a tick that long makes the watchdog declare the
		// server crashed and shut it down before the report is written.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Scan scan;
			try {
				scan = prepare(server, radius);
			} catch (RuntimeException error) {
				MasterSwordMod.LOGGER.error("shrine scan failed", error);
				server.halt(false);
				return;
			}

			CompletableFuture.runAsync(() -> {
				try {
					write(scan);
				} catch (Throwable error) {
					MasterSwordMod.LOGGER.error("shrine scan failed", error);
				}
				server.execute(() -> server.halt(false));
			});
		});
	}

	private static int start(CommandSourceStack source, int radius) {
		MinecraftServer server = source.getServer();
		Scan scan;
		try {
			scan = prepare(server, radius);
		} catch (RuntimeException error) {
			source.sendFailure(Component.literal("scan impossible : " + error));
			return 0;
		}

		long cells = (2L * radius + 1) * (2L * radius + 1);
		source.sendSuccess(() -> Component.literal("scan en cours : " + cells + " cellules..."), false);

		CompletableFuture.runAsync(() -> {
			Path file;
			try {
				file = write(scan);
			} catch (Throwable error) {
				MasterSwordMod.LOGGER.error("shrine scan failed", error);
				server.execute(() -> source.sendFailure(Component.literal("scan echoue : " + error)));
				return;
			}
			server.execute(() -> source.sendSuccess(
					() -> Component.literal("scan fini, rapport dans " + file), false));
		});

		return 1;
	}

	/** Everything that has to be read on the server thread. */
	private static Scan prepare(MinecraftServer server, int radius) {
		ServerLevel level = server.overworld();
		ServerChunkCache cache = level.getChunkSource();
		ChunkGeneratorStructureState state = cache.getGeneratorState();

		// Stronghold ring positions are computed lazily; force that here, on the
		// server thread, so the scan itself only ever reads.
		state.ensureStructuresGenerated();

		RegistryAccess registries = server.registryAccess();
		StructureSet set = registries.lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(SET).value();
		if (!(set.placement() instanceof RandomSpreadStructurePlacement placement)) {
			throw new IllegalStateException("master_sword n'est pas un random_spread");
		}

		ChunkGenerator generator = cache.getGenerator();
		return new Scan(
				registries, generator, generator.getBiomeSource(), cache.randomState(),
				server.getStructureTemplateManager(), level, state, placement,
				set.structures().getFirst().structure(), radius);
	}

	/** Runs the scan and saves its report; safe off the server thread. */
	private static Path write(Scan scan) throws IOException {
		String report = scan.run();
		Path file = FabricLoader.getInstance().getGameDir()
				.resolve("mastersword-scan-" + scan.seed() + ".txt");
		Files.writeString(file, report, StandardCharsets.UTF_8);
		MasterSwordMod.LOGGER.info("shrine scan -> {}\n{}", file, report);
		return file;
	}

	/** One candidate chunk, as the game itself resolved it. */
	private record Candidate(
			ChunkPos chunk, BlockPos anchor, BoundingBox box, List<StructurePiece> pieces,
			boolean kept, Edge edge) {
	}

	/** Nearest cell of another biome, Chebyshev distance in blocks. */
	private record Edge(int blocks, String biome) {
	}

	private static final class Scan {
		private final RegistryAccess registries;
		private final ChunkGenerator generator;
		private final BiomeSource biomes;
		private final RandomState randomState;
		private final Climate.Sampler sampler;
		private final BiomeResolver resolver;
		private final StructureTemplateManager templates;
		private final ServerLevel level;
		private final ChunkGeneratorStructureState state;
		private final RandomSpreadStructurePlacement placement;
		private final Holder<Structure> structure;
		private final Predicate<Holder<Biome>> valid;
		private final int radius;
		private int refusedByGrid;
		private int noGenerationPoint;

		Scan(RegistryAccess registries, ChunkGenerator generator, BiomeSource biomes, RandomState randomState,
				StructureTemplateManager templates, ServerLevel level, ChunkGeneratorStructureState state,
				RandomSpreadStructurePlacement placement, Holder<Structure> structure, int radius) {
			this.registries = registries;
			this.generator = generator;
			this.biomes = biomes;
			this.randomState = randomState;
			// 26.3: the climate sampler is built from the random state on demand, and
			// biome lookups go through a resolver rather than through BiomeSource.
			//
			// EMPTY_UNCACHED is not a shortcut. The game builds its own sampler with
			// caches on (ChunkGenerator.createStructures), and a cached context owns
			// a mutable array of cells -- fine on the generation thread, not here:
			// the scan runs on a pool thread so a long measurement cannot trip the
			// watchdog. The cache only memoises, keyed on the position, so the
			// values are the same either way; this one is merely slower.
			this.sampler = randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
			this.resolver = biomes.createResolver(this.sampler);
			this.templates = templates;
			this.level = level;
			this.state = state;
			this.placement = placement;
			this.structure = structure;
			// Exactly the predicate ChunkGenerator hands to Structure.generate:
			// HolderSet::contains on the structure's own biome list.
			this.valid = structure.value().biomes()::contains;
			this.radius = radius;
		}

		long seed() {
			return state.getLevelSeed();
		}

		String run() {
			long started = System.currentTimeMillis();
			long seed = seed();
			List<Candidate> candidates = new ArrayList<>();

			for (int cellX = -radius; cellX <= radius; cellX++) {
				for (int cellZ = -radius; cellZ <= radius; cellZ++) {
					// getPotentialStructureChunk takes CHUNK coordinates, not a cell
					// index: it does the floorDiv by spacing itself. Handing it the
					// index folds every cell onto the four around the origin, and the
					// scan then reports two biomes for the whole world.
					ChunkPos chunk = placement.getPotentialStructureChunk(
							seed, cellX * placement.spacing(), cellZ * placement.spacing());
					// Frequency and the mansion exclusion_zone both live in here.
					if (!placement.isStructureChunk(state, chunk.x(), chunk.z())) {
						refusedByGrid++;
						continue;
					}
					Candidate candidate = resolve(seed, chunk);
					if (candidate == null) {
						noGenerationPoint++;
						continue;
					}
					candidates.add(candidate);
				}
			}

			return report(candidates, seed, System.currentTimeMillis() - started);
		}

		/**
		 * Asks the game where this candidate would go, and whether it would be
		 * kept.
		 *
		 * <p>The verdict comes from {@code Structure.generate} -- the method the
		 * chunk generator itself calls -- so it carries the mod's own biome rule
		 * ({@link re.jerome.mastersword.mixin.StructureBiomeMarginMixin}) and not
		 * merely vanilla's. Measuring through {@code findValidGenerationPoint}
		 * would walk past the mixin and keep reporting the placement we had before
		 * the fix.
		 *
		 * <p>The second call, with an always-true biome predicate, is only there
		 * for the geometry: a refused candidate returns {@code INVALID_START}, and
		 * without its anchor and footprint the alternative rules would have nothing
		 * to be scored on.
		 */
		private Candidate resolve(long seed, ChunkPos chunk) {
			StructureStart start = structure.value().generate(
					structure, Level.OVERWORLD, registries, generator, biomes, sampler, randomState, templates,
					seed, chunk, 0, level, valid);

			Optional<Structure.GenerationStub> stub = findPoint(seed, chunk, biome -> true);
			if (stub.isEmpty()) {
				return null; // no terrain fit at all; not a biome question
			}

			// The anchor is already the middle of the footprint, where the pedestal
			// stands and where the game read the biome -- see the class comment.
			// So the distance is measured from it, and it is the same point the
			// game judged.
			BlockPos anchor = stub.get().position();
			StructurePiecesBuilder built = stub.get().getPiecesBuilder();
			BoundingBox box = built.getBoundingBox();
			return new Candidate(chunk, anchor, box, built.build().pieces(), start.isValid(), edge(anchor));
		}

		private Optional<Structure.GenerationStub> findPoint(
				long seed, ChunkPos chunk, Predicate<Holder<Biome>> biomeTest) {
			return structure.value().findValidGenerationPoint(new Structure.GenerationContext(
					registries, generator, biomes, sampler, randomState, templates, seed, chunk, level,
					biomeTest));
		}

		private Holder<Biome> biomeAt(int x, int y, int z) {
			return resolver.getNoiseBiome(
					QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
		}

		private static String name(Holder<Biome> biome) {
			return biome.unwrapKey().map(key -> key.identifier().toString()).orElse("?");
		}

		/** Nearest foreign biome cell, walked ring by ring like biome_edge.js. */
		private Edge edge(BlockPos from) {
			// Ring 0 is the cell the shrine stands on. It can only be foreign for a
			// candidate vanilla refused, but leaving it out would report those at 4
			// blocks and quietly make distance 0 impossible.
			for (int ring = 0; ring <= SEARCH_CELLS; ring++) {
				for (int dx = -ring; dx <= ring; dx++) {
					for (int dz = -ring; dz <= ring; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
							continue; // the ring, not the filled square
						}
						Holder<Biome> biome = biomeAt(
								from.getX() + QuartPos.toBlock(dx), from.getY(), from.getZ() + QuartPos.toBlock(dz));
						if (!valid.test(biome)) {
							return new Edge(QuartPos.toBlock(ring), name(biome));
						}
					}
				}
			}
			return new Edge(Integer.MAX_VALUE, "-");
		}

		/** Would each rule keep this candidate? Same order as {@link #RULES}. */
		private boolean[] rules(Candidate candidate) {
			boolean[] verdicts = new boolean[RULES.length];
			verdicts[0] = candidate.kept();
			// The control: the mod's rule, rebuilt from its two halves -- vanilla's
			// single cell, then the footprint. It has to agree with the game on
			// every single candidate; when it does not, the instrument is lying and
			// no other number in the report means anything.
			verdicts[1] = valid.test(biomeAt(
							candidate.anchor().getX(), candidate.anchor().getY(), candidate.anchor().getZ()))
					&& ShrineStructure.footprintFits(candidate.pieces(), biomes, sampler, valid);
			for (int i = 0; i < MARGINS.length; i++) {
				verdicts[2 + i] = candidate.kept() && candidate.edge().blocks() >= MARGINS[i];
			}
			return verdicts;
		}

		private String report(List<Candidate> candidates, long seed, long millis) {
			StringBuilder out = new StringBuilder();
			List<Candidate> kept = candidates.stream().filter(Candidate::kept).toList();

			out.append("seed ").append(seed)
					.append(", rayon ").append(radius).append(" cellules (spacing ")
					.append(placement.spacing()).append(", separation ").append(placement.separation()).append(')')
					.append(", ").append(millis).append(" ms\n");
			out.append("cellules refusees par la grille : ").append(refusedByGrid)
					.append(", sans point de generation : ").append(noGenerationPoint).append('\n');
			out.append("candidats retenus par la grille : ").append(candidates.size())
					.append(", dont sanctuaires generes : ").append(kept.size()).append('\n');

			// Which biomes the anchors actually land in, and what the structure
			// says it accepts: the two numbers that tell a real rate from a broken
			// instrument.
			out.append("biomes acceptes par la structure : ")
					.append(structure.value().biomes().size()).append('\n');
			Map<String, Integer> anchors = new HashMap<>();
			for (Candidate candidate : candidates) {
				anchors.merge(name(biomeAt(
						candidate.anchor().getX(), candidate.anchor().getY(), candidate.anchor().getZ())),
						1, Integer::sum);
			}
			out.append("biome sous l'ancre des candidats :\n");
			anchors.entrySet().stream()
					.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
					.limit(8)
					.forEach(entry -> out.append(String.format("  %-40s %4d%n", entry.getKey(), entry.getValue())));

			if (kept.isEmpty()) {
				out.append("\nles quinze premiers candidats, bruts :\n");
				candidates.stream().limit(15).forEach(candidate -> out.append(String.format(
						"  chunk %5d %5d   ancre %8d %4d %8d   emprise %d..%d %d..%d   %s%n",
						candidate.chunk().x(), candidate.chunk().z(),
						candidate.anchor().getX(), candidate.anchor().getY(), candidate.anchor().getZ(),
						candidate.box().minX(), candidate.box().maxX(),
						candidate.box().minZ(), candidate.box().maxZ(),
						name(biomeAt(candidate.anchor().getX(), candidate.anchor().getY(),
								candidate.anchor().getZ())))));
				return out.append("\naucun sanctuaire : rien a mesurer\n").toString();
			}

			out.append("\ndistance au premier biome etranger, depuis le centre de l'emprise :\n");
			int[] histogram = new int[BUCKETS.length + 1];
			for (Candidate candidate : kept) {
				histogram[bucket(candidate.edge().blocks())]++;
			}
			int previous = 0;
			for (int i = 0; i <= BUCKETS.length; i++) {
				String label = i < BUCKETS.length
						? String.format("%3d - %3d blocs", previous, BUCKETS[i])
						: String.format("     > %3d blocs", previous);
				out.append(String.format("  %s : %4d  %5.1f %%  %s%n",
						label, histogram[i], 100.0 * histogram[i] / kept.size(),
						"#".repeat(Math.min(40, Math.round(40f * histogram[i] / kept.size())))));
				if (i < BUCKETS.length) {
					previous = BUCKETS[i];
				}
			}

			long near = kept.stream().filter(candidate -> candidate.edge().blocks() < NEAR).count();
			out.append(String.format("%na moins de %d blocs d'un autre biome : %d sur %d, soit %.1f %%%n",
					NEAR, near, kept.size(), 100.0 * near / kept.size()));

			Map<String, Integer> neighbours = new HashMap<>();
			for (Candidate candidate : kept) {
				neighbours.merge(candidate.edge().biome(), 1, Integer::sum);
			}
			out.append("\nbiome voisin le plus proche, tous sanctuaires confondus :\n");
			neighbours.entrySet().stream()
					.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
					.limit(12)
					.forEach(entry -> out.append(String.format("  %-40s %4d  %5.1f %%%n",
							entry.getKey(), entry.getValue(), 100.0 * entry.getValue() / kept.size())));

			out.append("\nce que chaque regle d'acceptation donnerait :\n");
			out.append(String.format("  %-18s %8s %9s %8s %8s%n",
					"regle", "gardes", "% du jeu", "bordure", "% bord"));
			int[] total = new int[RULES.length];
			int[] onEdge = new int[RULES.length];
			int disagreements = 0;
			for (Candidate candidate : candidates) {
				boolean[] verdicts = rules(candidate);
				if (verdicts[0] != verdicts[1]) {
					disagreements++;
				}
				for (int i = 0; i < RULES.length; i++) {
					if (!verdicts[i]) {
						continue;
					}
					total[i]++;
					if (candidate.edge().blocks() < NEAR) {
						onEdge[i]++;
					}
				}
			}
			for (int i = 0; i < RULES.length; i++) {
				out.append(String.format("  %-18s %8d %8.1f %% %8d %7.1f %%%n", RULES[i], total[i],
						100.0 * total[i] / kept.size(), onEdge[i],
						total[i] == 0 ? 0.0 : 100.0 * onEdge[i] / total[i]));
			}

			out.append(disagreements == 0
					? "\ncontrole : le point teste ici est bien celui du jeu, sur les "
							+ candidates.size() + " candidats\n"
					: "\n*** CONTROLE EN ECHEC : " + disagreements + " desaccords avec le jeu."
							+ " Aucun chiffre de ce rapport ne vaut rien ***\n");

			out.append("\nles sanctuaires generes, du plus au bord au plus au centre :\n");
			out.append("  (dx, dz = decalage du point teste par rapport au coin nord-ouest du chunk)\n");
			kept.stream()
					.sorted(Comparator.comparingInt(candidate -> candidate.edge().blocks()))
					.forEach(candidate -> out.append(String.format(
							"  %7d %4d %7d  dx %+3d dz %+3d   %6s blocs   %s%n",
							candidate.anchor().getX(), candidate.anchor().getY(), candidate.anchor().getZ(),
							candidate.anchor().getX() - candidate.chunk().getMinBlockX(),
							candidate.anchor().getZ() - candidate.chunk().getMinBlockZ(),
							candidate.edge().blocks() == Integer.MAX_VALUE
									? "> " + QuartPos.toBlock(SEARCH_CELLS)
									: String.valueOf(candidate.edge().blocks()),
							candidate.edge().biome())));

			return out.toString();
		}

		private static int bucket(int blocks) {
			for (int i = 0; i < BUCKETS.length; i++) {
				if (blocks < BUCKETS[i]) {
					return i;
				}
			}
			return BUCKETS.length;
		}
	}
}
