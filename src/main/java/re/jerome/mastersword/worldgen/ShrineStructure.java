package re.jerome.mastersword.worldgen;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import re.jerome.mastersword.MasterSwordMod;

/**
 * The shrine's identity in the structure registry, and the biome rule its
 * footprint has to satisfy.
 *
 * <p>Vanilla judges the biome of a structure on <b>one</b> 4x4x4 cell -- the one
 * at the middle of the start piece -- however wide the structure turns out to
 * be. For a clearing 17 blocks across that leaves everything from 2 to 8 blocks
 * out of the centre unexamined, and it is why the sword so often ends up against
 * a river: the middle is dark forest, the west half is water.
 *
 * <p>Measured before it was fixed, on 487 generated shrines over two seeds
 * (SPEC section 9): <b>38 % sat within 16 blocks of another biome</b>, a river in
 * 30 % of cases. Requiring the whole footprint brings that to about 17 % and
 * costs a quarter of the shrines -- the trade Jerome picked out of the table.
 *
 * <p>The same method backs both the rule and its measurement:
 * {@link re.jerome.mastersword.mixin.StructureBiomeMarginMixin} asks it during
 * world generation, and {@link ShrineScan} asks it to score the rule. They
 * cannot drift apart, which is the point.
 */
public final class ShrineStructure {
	public static final ResourceKey<Structure> KEY =
			ResourceKey.create(Registries.STRUCTURE, MasterSwordMod.id("master_sword"));

	private ShrineStructure() {
	}

	/**
	 * True when every biome cell the pieces cover is one the structure accepts.
	 *
	 * <p>⚠️ <b>The pieces, and not {@code StructureStart.getBoundingBox()}.</b>
	 * That one runs the box through {@code adjustBoundingBox}, which inflates it
	 * by 12 blocks in every direction as soon as terrain adaptation is anything
	 * but {@code NONE} -- and ours is {@code beard_thin}. Asking it turns a 17x17
	 * shrine into a 41x41 one and refuses three shrines out of four; measured,
	 * before the control in {@link ShrineScan} caught it.
	 *
	 * <p>Sampled at each piece's {@code minY()}: biome cells are 4 blocks tall,
	 * and the bottom of a piece is the closest thing to the ground the shrine
	 * stands on -- the middle would be six blocks up, a cell or two above the
	 * terrain, and would answer a question nobody asked.
	 *
	 * <p><b>25 lookups, always</b>, for our clearing: the pool holds one element
	 * and the .nbt carries no jigsaw block, so there is only ever one piece, and
	 * it starts at a chunk corner -- {@code minX % 4 == 0}, so 17 blocks span
	 * exactly 5 cells on each axis. It only runs for the shrine, and only once
	 * vanilla's own test has already passed.
	 */
	public static boolean footprintFits(
			List<StructurePiece> pieces, BiomeSource biomes, Climate.Sampler sampler,
			Predicate<Holder<Biome>> valid) {
		// 26.3 moved the lookup off BiomeSource: a resolver is built from the
		// sampler once, and answers by position alone. Built here rather than per
		// piece -- there is only ever one piece, but the cost belongs outside the
		// loop either way.
		BiomeResolver resolver = biomes.createResolver(sampler);
		for (StructurePiece piece : pieces) {
			if (!fits(piece.getBoundingBox(), resolver, valid)) {
				return false;
			}
		}
		return true;
	}

	private static boolean fits(
			BoundingBox box, BiomeResolver biomes, Predicate<Holder<Biome>> valid) {
		int quartY = QuartPos.fromBlock(box.minY());
		for (int quartX = QuartPos.fromBlock(box.minX()); quartX <= QuartPos.fromBlock(box.maxX()); quartX++) {
			for (int quartZ = QuartPos.fromBlock(box.minZ()); quartZ <= QuartPos.fromBlock(box.maxZ()); quartZ++) {
				if (!valid.test(biomes.getNoiseBiome(quartX, quartY, quartZ))) {
					return false;
				}
			}
		}
		return true;
	}
}
