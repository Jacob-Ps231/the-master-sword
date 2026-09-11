package re.jerome.mastersword.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

// Render states are pooled and reused frame to frame, so the ItemStackRenderState
// is a field rather than a fresh object: updateForTopItem refills it in place.
//
// Nothing time-dependent lives here: the sword stands still in its pedestal, so
// there is no angle or offset to interpolate. The one angle is the block's own
// facing, which only changes when the block does.
public class PedestalRenderState extends BlockEntityRenderState {
	public final ItemStackRenderState sword = new ItemStackRenderState();

	/** Yaw of the block's facing, in degrees, as Direction.toYRot reports it. */
	public float facingYRot;
}
