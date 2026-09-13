package re.jerome.mastersword.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import re.jerome.mastersword.client.FogHandler;

/**
 * Where the shrine fog is added to the game's own (SPEC 5).
 *
 * Fabric has no fog API -- checked across the 66 jars of the cache, not one class
 * mentions fog -- so a mixin is the only way in. setupFog is the one place worth
 * taking: it is public, it returns the FogData the frame will use, and it is
 * called once per frame from GameRenderer.extractCamera. Injecting at RETURN
 * means every vanilla FogEnvironment has already had its say, so what the handler
 * receives is the game's final answer and it only ever has to thicken it.
 *
 * The priority is what makes this work under Sodium, and it goes the way that
 * reads backwards. Sodium sits on the SAME injection point -- its own
 * FogRendererMixin is @Mixin(FogRenderer.class) @Inject(method = "setupFog",
 * at = @At("RETURN")), and it photographs the FogData through a MixinExtras
 * @Local into its FogParameters, which is what its terrain shaders read and what
 * Iris reads in turn for its fogEnd uniform. So the only thing that decides
 * whether Sodium sees a thickened fog or the game's own is who runs first, and
 * with everyone left at the default 1000 that falls to config registration
 * order, which is to say chance. It fell the wrong way: the fog was invisible
 * with Sodium installed.
 *
 * LOWER priority is applied FIRST, hence runs FIRST. MixinInfo.compareTo sorts
 * ascending, TargetClassContext.applyMixins() walks that SortedSet in order, and
 * an @Inject at RETURN inserts its call immediately before the areturn -- so the
 * mixin applied first ends up first in the instruction stream. Raising the
 * priority would push us behind Sodium, which is the opposite of what is wanted
 * and is the likely reason this was once written off as unfixable.
 *
 * 500 rather than 999: far enough below the default to stay ahead of a mod that
 * merely nudges itself under it, and nothing needs to run before us -- every
 * vanilla FogEnvironment runs inside the body of setupFog, so they are all done
 * by the time any RETURN handler fires.
 */
@Mixin(value = FogRenderer.class, priority = 500)
public class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void mastersword$thickenNearShrine(
			Camera camera,
			int renderDistanceInChunks,
			DeltaTracker deltaTracker,
			float darkenWorldAmount,
			ClientLevel level,
			CallbackInfoReturnable<FogData> cir) {
		FogHandler.apply(cir.getReturnValue(), camera, deltaTracker, level, darkenWorldAmount);
	}
}
