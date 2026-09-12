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
 */
@Mixin(FogRenderer.class)
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
