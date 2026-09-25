package re.jerome.mastersword.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import re.jerome.mastersword.worldgen.ShrineGuard;

// No tree grows inside the shrine.
//
// This is the only hook vanilla leaves: there is no event, and the tree's own
// placed_feature is a vanilla JSON we cannot amend. TreeFeature.place is where
// every generated tree -- and only trees -- passes, so it is the narrowest cut
// that does the job. ShrineGuard carries the reasoning and the cost analysis.
//
// 26.3 reworked the feature API: FeaturePlaceContext is gone, the level, the
// generator, the random source and the origin arrive as four arguments, and
// TreeFeature is now a record. The injection point is the same.
@Mixin(TreeFeature.class)
public abstract class TreeFeatureMixin {
	@Inject(
			method = "place(Lnet/minecraft/world/level/WorldGenLevel;"
					+ "Lnet/minecraft/world/level/chunk/ChunkGenerator;"
					+ "Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
			at = @At("HEAD"),
			cancellable = true)
	private void mastersword$keepTreesOutOfTheShrine(
			WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin,
			CallbackInfoReturnable<Boolean> cir) {
		if (ShrineGuard.vetoes(level, origin)) {
			// false is what vanilla returns when a tree fails to place, so the
			// caller treats this exactly like ground it did not like.
			cir.setReturnValue(false);
		}
	}
}
