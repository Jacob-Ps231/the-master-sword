package re.jerome.mastersword.mixin;

import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
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
// place() is final, which stops overriding, not bytecode injection.
@Mixin(TreeFeature.class)
public abstract class TreeFeatureMixin {
	@Inject(
			method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
			at = @At("HEAD"),
			cancellable = true)
	private void mastersword$keepTreesOutOfTheShrine(
			FeaturePlaceContext<TreeConfiguration> context, CallbackInfoReturnable<Boolean> cir) {
		if (ShrineGuard.vetoes(context.level(), context.origin())) {
			// false is what vanilla returns when a tree fails to place, so the
			// caller treats this exactly like ground it did not like.
			cir.setReturnValue(false);
		}
	}
}
