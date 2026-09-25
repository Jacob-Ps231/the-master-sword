package re.jerome.mastersword.mixin;

import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.jerome.mastersword.item.MasterSwordItem;

// The beam on a swing that hits nothing.
//
// A left click always tells the server about itself, hit or miss, and the
// charge has to be read before the server zeroes it. In 26.2 that meant
// ServerPlayer.swing(InteractionHand), whose override reset the attack strength
// ticker at its END.
//
// 26.3 moved the whole path: the client sends a ServerboundPunchPacket at the
// tail of Minecraft.startAttack -- for a miss and for a landed hit alike, the
// attack branch simply jumps to the same tail -- and the server answers it in
// handlePunch, which swings and then calls resetAttackStrengthTicker. So the
// hook sits just before that swing, where the charge is still readable, which
// is the same instant the 26.2 hook ran at.
//
// A swing that lands on an entity still does not fire a beam here: the attack
// packet is sent first, so by the time this runs the ticker is already zeroed
// and the charge reads ~0. PlayerAttackMixin covers that path.
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPunchMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(
			method = "handlePunch(Lnet/minecraft/network/protocol/game/ServerboundPunchPacket;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;swing("
							+ "Lnet/minecraft/world/InteractionHand;"
							+ "Lnet/minecraft/world/item/component/SwingAnimation;Z)Z"))
	private void mastersword$fireLightWaveOnFullSwing(ServerboundPunchPacket packet, CallbackInfo ci) {
		ItemStack stack = this.player.getMainHandItem();
		if (!MasterSwordItem.isMasterSword(stack)) {
			return;
		}

		// Same threshold vanilla uses for a full-strength attack.
		if (this.player.getAttackStrengthScale(0.5F) > 0.9F) {
			MasterSwordItem.tryFireLightWave(this.player, stack);
		}
	}
}
