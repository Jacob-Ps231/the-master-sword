package re.jerome.mastersword.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import re.jerome.mastersword.MasterSwordMod;
import re.jerome.mastersword.entity.LightWaveEntity;

// A single flat quad, oriented along the flight path rather than billboarded at
// the camera -- a wave has a direction, and turning to face the viewer would give
// it away as a sprite.
//
// Two 26.2 renames worth remembering: RenderType moved to
// net.minecraft.client.renderer.rendertype and its static factories now live in
// RenderTypes (plural); and drawing goes through SubmitNodeCollector.submit(...),
// there is no render(...) any more.
public class LightWaveRenderer extends EntityRenderer<LightWaveEntity, LightWaveRenderState> {
	private static final Identifier TEXTURE = MasterSwordMod.id("textures/entity/light_wave.png");
	// Emissive so the wave glows in the dark; translucent so the alpha channel
	// gives it soft edges instead of a hard cutout.
	private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucentEmissive(TEXTURE);
	private static final float SIZE = 1.4F;

	public LightWaveRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public LightWaveRenderState createRenderState() {
		return new LightWaveRenderState();
	}

	@Override
	public void extractRenderState(LightWaveEntity entity, LightWaveRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = entity.getYRot();
		state.xRot = entity.getXRot();
	}

	// Full block light regardless of where the wave is, so it reads as its own
	// light source even before the emissive render type kicks in.
	@Override
	protected int getBlockLightLevel(LightWaveEntity entity, BlockPos pos) {
		return 15;
	}

	// The hitbox is 0.6 blocks but the quad spans 2.8, so culling on the hitbox
	// makes the wave vanish while still visible at the edge of the screen.
	@Override
	protected AABB getBoundingBoxForCulling(LightWaveEntity entity, float partialTick) {
		return entity.getBoundingBox().inflate(SIZE);
	}

	@Override
	public void submit(
			LightWaveRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, state.yRot - 90.0F);
		poseStack.rotateDegrees(Axis.ZP, state.xRot);
		// Lays the quad flat rather than upright: the crescent sweeps parallel to
		// the ground, the way a sword swing does.
		poseStack.rotateDegrees(Axis.XP, 90.0F);

		int light = state.lightCoords;
		collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
			// The lambda runs later, at draw time, so nothing mutable may be
			// captured -- only the light value read above.
			vertex(buffer, pose, light, -SIZE, -SIZE, 0, 1);
			vertex(buffer, pose, light, SIZE, -SIZE, 1, 1);
			vertex(buffer, pose, light, SIZE, SIZE, 1, 0);
			vertex(buffer, pose, light, -SIZE, SIZE, 0, 0);
		});

		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static void vertex(
			VertexConsumer buffer, PoseStack.Pose pose, int light, float x, float y, int u, int v) {
		buffer.addVertex(pose, x, y, 0.0F)
				.setColor(-1)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, 0.0F, 0.0F, 1.0F);
	}
}
