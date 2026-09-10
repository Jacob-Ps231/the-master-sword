package re.jerome.mastersword.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.jerome.mastersword.item.MasterSwordItem;

// The beam on a swing that hits nothing.
//
// A left click always sends a swing packet, and this override is where the
// server handles it -- crucially, it resets the attack strength ticker at its
// END, so at HEAD the charge is still readable. That is the whole reason this
// hook exists: by the time Item.postHurtEnemy runs, Player.attack has already
// called onAttack() and zeroed the ticker, so the charge is unreadable there.
//
// A swing that lands on an entity does not come through here with a charge: the
// attack packet is processed first and zeroes the ticker, so this hook sees ~0
// and stays quiet. PlayerAttackMixin covers that path instead.
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"))
	private void mastersword$fireLightWaveOnFullSwing(InteractionHand hand, CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND) {
			return;
		}

		ServerPlayer player = (ServerPlayer) (Object) this;
		ItemStack stack = player.getMainHandItem();
		if (!MasterSwordItem.isMasterSword(stack)) {
			return;
		}

		// Same threshold vanilla uses for a full-strength attack.
		if (player.getAttackStrengthScale(0.5F) > 0.9F) {
			MasterSwordItem.tryFireLightWave(player, stack);
		}
	}
}
