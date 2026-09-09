package re.jerome.mastersword.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import re.jerome.mastersword.item.MasterSwordItem;

// Lets the Master Sword carry enchantments vanilla declares mutually exclusive,
// Sharpness and Smite together for instance.
//
// Exclusivity is a property of the enchantment, never of the item:
// Enchantment.areCompatible(Holder, Holder) is static and never sees a stack, and
// the only Fabric hook that reaches exclusiveSet (EnchantmentEvents.MODIFY) is
// global -- it would lift the rule for every sword in the game. So the call sites
// that do know the item are intercepted instead. This is one of three.
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@ModifyExpressionValue(
			method = "createResult()V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/Enchantment;areCompatible(Lnet/minecraft/core/Holder;Lnet/minecraft/core/Holder;)Z"))
	private boolean mastersword$allowExclusiveEnchantments(boolean original) {
		// The stack is read through the public getSlot rather than a @Local: three
		// ItemStacks are live at this point (input, result, addition), so a @Local
		// would need an ordinal and would break the day the locals are reordered.
		//
		// Returning true also skips the price++ vanilla charges per incompatible
		// pair, which is right: that charge is the penalty for an enchantment about
		// to be thrown away, and here it is applied.
		return original
				|| MasterSwordItem.isMasterSword(((AnvilMenu) (Object) this).getSlot(AnvilMenu.INPUT_SLOT).getItem());
	}
}
