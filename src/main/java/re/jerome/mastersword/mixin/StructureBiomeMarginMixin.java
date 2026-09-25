package re.jerome.mastersword.mixin;

import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import re.jerome.mastersword.worldgen.ShrineStructure;

/**
 * Refuses a shrine whose footprint does not fit inside the dark forest.
 *
 * <p>The reason is in {@link ShrineStructure}: vanilla judges the biome on a
 * single cell at the middle of the structure. This widens the question to the
 * whole footprint, for our structure and no other.
 *
 * <p>Two choices worth writing down, both forced:
 *
 * <ul>
 * <li><b>{@code generate}, not {@code isValidBiome}.</b> That one is
 * {@code private static} and receives no reference to the structure, so there
 * would be no way to leave every other structure alone. {@code generate} carries
 * the {@code Holder<Structure>}, the biome source, the random state and the
 * valid-biome predicate in its own signature -- everything the rule needs.
 * <li><b>At {@code RETURN}, not around {@code findValidGenerationPoint}.</b>
 * Getting the footprint from a {@code GenerationStub} means calling
 * {@code getPiecesBuilder()}, which <i>replays</i> the assembly consumer. By the
 * time {@code generate} returns, the {@code StructureStart} is built and its
 * pieces are free.
 * </ul>
 *
 * <p>⚠️ {@code getPieces()}, never {@code getBoundingBox()} -- see
 * {@link ShrineStructure#footprintFits}: the latter is inflated by 12 blocks for
 * terrain adaptation, and testing it refuses three shrines out of four.
 *
 * <p>Returning {@code INVALID_START} is vanilla's own way of saying no -- it is
 * what {@code generate} returns when its biome test fails -- so nothing
 * downstream sees anything unusual. {@code StructureCheck}'s cache is not
 * touched either: it is fed by {@code findValidGenerationPoint}, never by
 * {@code generate}, so what it remembers stays vanilla's answer and stays true.
 *
 * <p>One known cosmetic effect: {@code generate} reports its JFR
 * {@code ProfiledDuration} as a success just before returning, and this callback
 * runs after. A profiling session would count a shrine we then refuse.
 */
@Mixin(Structure.class)
public abstract class StructureBiomeMarginMixin {
	@Inject(
			method = "generate(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;"
					+ "Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGenerator;"
					+ "Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/biome/Climate$Sampler;"
					+ "Lnet/minecraft/world/level/levelgen/RandomState;"
					+ "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;J"
					+ "Lnet/minecraft/world/level/ChunkPos;ILnet/minecraft/world/level/LevelHeightAccessor;"
					+ "Ljava/util/function/Predicate;)"
					+ "Lnet/minecraft/world/level/levelgen/structure/StructureStart;",
			at = @At("RETURN"),
			cancellable = true)
	private void mastersword$requireWholeFootprintInBiome(
			Holder<Structure> structure, ResourceKey<Level> dimension, RegistryAccess registries,
			ChunkGenerator generator, BiomeSource biomes, Climate.Sampler sampler,
			RandomState randomState,
			StructureTemplateManager templates, long seed, ChunkPos chunk, int references,
			LevelHeightAccessor heights, Predicate<Holder<Biome>> validBiome,
			CallbackInfoReturnable<StructureStart> cir) {
		// The cheapest possible rejection for every structure in the world that has
		// nothing to do with us.
		if (!structure.is(ShrineStructure.KEY)) {
			return;
		}

		StructureStart start = cir.getReturnValue();
		if (!start.isValid()) {
			return; // vanilla already said no, for its own reasons
		}

		// 26.3 hands the climate sampler in as a parameter; in 26.2 it came out of
		// the RandomState, which no longer exposes one.
		if (!ShrineStructure.footprintFits(
				start.getPieces(), biomes, sampler, validBiome)) {
			cir.setReturnValue(StructureStart.INVALID_START);
		}
	}
}
