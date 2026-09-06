package re.jerome.mastersword.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import re.jerome.mastersword.config.MasterSwordConfig;
import re.jerome.mastersword.item.MasterSwordItem;

// Durability is a data component, baked onto the item when it is registered, so
// it cannot follow a config value that changes while the game runs. Every read
// that matters here goes through ItemStack.getMaxDamage -- getDamageValue,
// isBarVisible, getBarWidth and getBarColor all call it -- so intercepting it
// covers them all.
//
// Two paths do read or write the component directly and are not covered:
// DamagePredicate reads the raw value for datapack predicates, and GrindstoneMenu
// and RepairItemRecipe write it when two swords are merged. Neither matters
// today: the mod ships no predicate, and a merge writes the configured value back
// because it computes it from getMaxDamage in the first place.
//
// Both bodies stay trivial on purpose: getMaxDamage is called on every durability
// bar draw.
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "getMaxDamage()I", at = @At("HEAD"), cancellable = true)
	private void mastersword$configurableDurability(CallbackInfoReturnable<Integer> cir) {
		if (mastersword$isMasterSword()) {
			cir.setReturnValue(MasterSwordConfig.get().item().maxDurability());
		}
	}

	// Reporting the sword as not damageable is what hides the durability bar and
	// stops it wearing down; there is no component for "unbreakable but still
	// enchantable the usual way".
	@Inject(method = "isDamageableItem()Z", at = @At("HEAD"), cancellable = true)
	private void mastersword$unbreakable(CallbackInfoReturnable<Boolean> cir) {
		if (mastersword$isMasterSword() && MasterSwordConfig.get().item().unbreakable()) {
			cir.setReturnValue(false);
		}
	}

	private boolean mastersword$isMasterSword() {
		return ((ItemStack) (Object) this).getItem() instanceof MasterSwordItem;
	}
}
