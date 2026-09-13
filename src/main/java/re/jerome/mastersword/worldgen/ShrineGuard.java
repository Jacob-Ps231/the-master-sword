package re.jerome.mastersword.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import re.jerome.mastersword.MasterSwordMod;

/**
 * Keeps trees out of the shrine.
 *
 * <p>Until now this was the .nbt's job: the structure was forbidden from
 * containing any block of {@code #minecraft:supports_vegetation}, because
 * {@code surface_structures} runs before {@code vegetal_decoration} and a dark
 * oak placed afterwards will happily root in the ruin and grow through it. That
 * worked, but it cost the shrine every blade of grass, every fern and every
 * block of soil -- and a floor that could only be stone is what made both
 * variants read as a paved rectangle dropped into the forest.
 *
 * <p>So the rule moves here. A tree about to be placed asks whether it is
 * standing in a shrine, and gives up if it is. The decor is then free to use
 * whatever it likes.
 *
 * <p>Three deliberate choices:
 *
 * <ul>
 * <li><b>A disc, not the bounding box.</b> Banning trees across the whole square
 * footprint would cut a square hole in the canopy -- the same defect as before,
 * moved from the ground to the sky. The inscribed disc lets the forest close in
 * over the corners, and the shrine reads as a clearing.
 * <li><b>The height is ignored.</b> A trunk is refused anywhere in the column,
 * whatever altitude the heightmap picked for it.
 * <li><b>Only world generation.</b> A sapling a player grows runs against the
 * ServerLevel, never a WorldGenRegion, so it is not caught: someone who wants a
 * tree of their own next to the sword can still plant one.
 * </ul>
 */
public final class ShrineGuard {
	private static final ResourceKey<Structure> SHRINE =
			ResourceKey.create(Registries.STRUCTURE, MasterSwordMod.id("master_sword"));

	private ShrineGuard() {
	}

	/**
	 * True when a feature at this position would land inside a generated shrine.
	 *
	 * <p>This is vanilla's own pattern, not an invention:
	 * {@code ChunkGenerator.applyBiomeDecoration} -- the very method our caller
	 * runs inside -- builds a region-scoped StructureManager and calls
	 * {@code startsForStructure(sectionPos, structure)} on it for every structure
	 * at every decoration step. Asking the same question from a feature is
	 * therefore neither novel nor a risk of reaching for a chunk that is still
	 * being built.
	 */
	@SuppressWarnings("deprecation") // WorldGenRegion.getLevel(); see below
	public static boolean vetoes(WorldGenLevel level, BlockPos pos) {
		// Also the cheapest possible rejection for every tree in the world that
		// has nothing to do with us: one instanceof.
		if (!(level instanceof WorldGenRegion region)) {
			return false;
		}

		// A WorldGenRegion only holds chunks within its own radius, and at the
		// FEATURES step that radius is one: asking it for a chunk further out
		// throws rather than returning null. Decoration features always have
		// their origin in the centre chunk, so this never fires for vanilla --
		// but another mod placing a tree at a wider offset would crash the
		// generator, and the crash would look like ours.
		int chunkX = SectionPos.blockToSectionCoord(pos.getX());
		int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
		if (region.getCenter().getChessboardDistance(chunkX, chunkZ) > 1) {
			return false;
		}

		Structure shrine = region.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(SHRINE);
		if (shrine == null) {
			return false; // datapack missing or failed to load; nothing to protect
		}

		// forWorldGenRegion, and not the level's own manager: the region reads
		// chunks from the generation cache, where the neighbours actually are.
		// The level's manager would ask the chunk source for a chunk that is
		// still being built.
		//
		// getLevel() is @Deprecated on WorldGenRegion -- a warning against
		// reaching for the whole world mid-generation, which is fair. It is kept
		// because it is the only handle a feature has: vanilla gets the same
		// manager in ChunkStatusTasks, where the level is already in scope, and
		// hands it down as an argument our mixin never sees. The value is used
		// for exactly one call and immediately narrowed back to the region.
		StructureManager structures = region.getLevel().structureManager().forWorldGenRegion(region);
		for (StructureStart start : structures.startsForStructure(SectionPos.of(pos), shrine)) {
			if (start.isValid() && insideDisc(start.getBoundingBox(), pos)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Radius of the guarded disc, in blocks, measured from the middle of the
	 * structure -- a constant, not a fraction of the footprint.
	 *
	 * <p>Two goes at this were wrong in the same direction. Protecting the whole
	 * inscribed disc, then the disc less three blocks, both left a ring of bare
	 * grass between the shrine and the forest: the guard was sized to the
	 * footprint, and the footprint is much wider than anything a trunk could
	 * spoil.
	 *
	 * <p>The reason it can be this small is that the decor defends itself. A dark
	 * oak needs #minecraft:supports_vegetation under it, and the terrace is
	 * stone, the pool is water, the ruins are masonry -- none of them will ever
	 * take a tree, guard or no guard. What the guard is for is the handful of
	 * cells of real soil around the sword, and the certainty that no trunk ever
	 * lands on the pedestal itself. Eleven blocks across covers that in both
	 * variants and leaves the rest of the footprint to the forest.
	 */
	private static final double RADIUS = 5.0;

	private static boolean insideDisc(BoundingBox box, BlockPos pos) {
		double centreX = (box.minX() + box.maxX()) / 2.0;
		double centreZ = (box.minZ() + box.maxZ()) / 2.0;
		double dx = pos.getX() - centreX;
		double dz = pos.getZ() - centreZ;
		return dx * dx + dz * dz <= RADIUS * RADIUS;
	}
}
