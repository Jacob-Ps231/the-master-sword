package re.jerome.mastersword.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import re.jerome.mastersword.block.PedestalBlock;
import re.jerome.mastersword.block.PedestalBlockEntity;

// Draws the sword driven into the pedestal: point down in the slot, hilt up,
// perfectly still.
//
// The item model is used as-is rather than a bespoke one, so whatever texture the
// sword carries -- enchantment glint included -- is what shows here.
public class PedestalRenderer implements BlockEntityRenderer<PedestalBlockEntity, PedestalRenderState> {
	/** Height of the sword's midpoint above the block origin. */
	private static final float HEIGHT = 1.2F;

	// A sword sprite runs corner to corner, tip at the top right: 45 degrees. The
	// FIXED display transform then turns the model a half turn about Y, which
	// mirrors that to 135 degrees -- which is why a plain 45 here came out flat,
	// tip to the left. 135 lands the tip at 270 degrees: straight down, into the
	// stone. Measured from the texture and confirmed against what the first build
	// actually drew, not assumed.
	//
	// The pedestal takes any #swords item, so this stays keyed to the sprite
	// convention every vanilla sword follows. Our own 3D model is built upright
	// instead, and its FIXED transform carries the 45 degrees that puts it back
	// on that convention -- see master_sword_3d.json.
	private static final float BLADE_DOWN = 135.0F;

	private final ItemModelResolver itemModelResolver;

	public PedestalRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
	}

	@Override
	public PedestalRenderState createRenderState() {
		return new PedestalRenderState();
	}

	@Override
	public void extractRenderState(
			PedestalBlockEntity pedestal,
			PedestalRenderState state,
			float partialTicks,
			Vec3 cameraPos,
			ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderer.super.extractRenderState(pedestal, state, partialTicks, cameraPos, crumbling);

		state.facingYRot = pedestal.getBlockState().getValue(PedestalBlock.FACING).toYRot();

		// null as the ItemOwner: vanilla passes null here too (BrushableBlockRenderer).
		// The call refills the pooled render state in place.
		this.itemModelResolver.updateForTopItem(
				state.sword,
				pedestal.getSword(),
				ItemDisplayContext.FIXED,
				pedestal.getLevel(),
				null,
				0);
	}

	@Override
	public void submit(
			PedestalRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.sword.isEmpty()) {
			return;
		}

		poseStack.pushPose();
		poseStack.translate(0.5F, HEIGHT, 0.5F);
		// Negated: toYRot is the entity-yaw convention (south 0, west 90), while a
		// positive turn about +Y carries south towards east. Unrotated, the blade
		// already faces south, so facing=south lands on zero either way -- west is
		// what settles the sign.
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.facingYRot));
		poseStack.mulPose(Axis.ZP.rotationDegrees(BLADE_DOWN));

		state.sword.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);

		poseStack.popPose();
	}
}
