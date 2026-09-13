package re.jerome.mastersword.mixin;

import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.FallenTreeFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FallenTreeConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import re.jerome.mastersword.worldgen.ShrineGuard;

// The other two things dark_forest_vegetation drops on a shrine.
//
// TreeFeature is not the whole story: the same random_selector also rolls
// huge_brown_mushroom (2.5%), huge_red_mushroom (5%), fallen_oak_tree (1.25%)
// and fallen_birch_tree (0.25%), and those are different Feature classes. Their
// can_place_on is #minecraft:huge_red_mushroom_can_place_on, which resolves to
// #substrate_overworld -- grass, dirt, podzol, moss -- in other words exactly
// the living ground step 13 gave the shrine back.
//
// Nine percent of sixteen draws a chunk is better than one in a chunk, so
// without these two a shrine would usually come with a giant mushroom or a log
// lying across its terrace. The mineral floor used to stop them by accident;
// now it has to be said out loud.
public final class FeaturePlacementMixins {
	private FeaturePlacementMixins() {
	}

	@Mixin(AbstractHugeMushroomFeature.class)
	public abstract static class HugeMushroom {
		@Inject(
				method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
				at = @At("HEAD"),
				cancellable = true)
		private void mastersword$keepMushroomsOutOfTheShrine(
				FeaturePlaceContext<HugeMushroomFeatureConfiguration> context, CallbackInfoReturnable<Boolean> cir) {
			if (ShrineGuard.vetoes(context.level(), context.origin())) {
				cir.setReturnValue(false);
			}
		}
	}

	@Mixin(FallenTreeFeature.class)
	public abstract static class FallenTree {
		@Inject(
				method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
				at = @At("HEAD"),
				cancellable = true)
		private void mastersword$keepFallenTrunksOutOfTheShrine(
				FeaturePlaceContext<FallenTreeConfiguration> context, CallbackInfoReturnable<Boolean> cir) {
			if (ShrineGuard.vetoes(context.level(), context.origin())) {
				cir.setReturnValue(false);
			}
		}
	}
}
