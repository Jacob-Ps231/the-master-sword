package re.jerome.mastersword.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.jerome.mastersword.item.MasterSwordItem;

// The beam on a swing that lands.
//
// Player.attack calls onAttack() -- which zeroes attackStrengthTicker -- before
// it even computes fullStrengthAttack, and long before itemAttackInteraction
// hands control to the item. Reading the charge back from postHurtEnemy would
// therefore always report about 0.04 and never clear 0.9. The local vanilla
// already computed is borrowed instead.
@Mixin(Player.class)
public abstract class PlayerAttackMixin {
	@Inject(
			method = "attack(Lnet/minecraft/world/entity/Entity;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;itemAttackInteraction(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/damagesource/DamageSource;Z)V",
					shift = At.Shift.AFTER))
	private void mastersword$fireLightWaveOnFullHit(
			Entity target,
			CallbackInfo ci,
			// index rather than ordinal: slot 8 is reused by two variables and slot
			// 13 by another, so counting by type is fragile here.
			@Local(index = 7) boolean fullStrengthAttack,
			@Local(index = 3) ItemStack attackingItemStack) {
		if (!fullStrengthAttack || !MasterSwordItem.isMasterSword(attackingItemStack)) {
			return;
		}

		if ((Object) this instanceof ServerPlayer player) {
			MasterSwordItem.tryFireLightWave(player, attackingItemStack);
		}
	}
}
