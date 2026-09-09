package re.jerome.mastersword.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import re.jerome.mastersword.item.MasterSwordItem;

// The enchanting table half of lifting exclusivity, and with it the loot
// functions and enchantment providers -- they all funnel through
// selectEnchantment.
//
// The wrapped call is filterCompatibleEnchantments rather than areCompatible
// itself: areCompatible is invoked from a synthetic lambda, outside the method
// being targeted, so an expression-level injection cannot reach it. Wrapping the
// caller also hands us the ItemStack for free, as a parameter.
@Mixin(EnchantmentHelper.class)
public class EnchantmentHelperMixin {
	@WrapOperation(
			method = "selectEnchantment(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/item/ItemStack;ILjava/util/stream/Stream;)Ljava/util/List;",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;filterCompatibleEnchantments(Ljava/util/List;Lnet/minecraft/world/item/enchantment/EnchantmentInstance;)V"))
	private static void mastersword$keepExclusiveEnchantments(
			List<EnchantmentInstance> enchants,
			EnchantmentInstance target,
			Operation<Void> original,
			@Local(argsOnly = true) ItemStack stack) {
		if (!MasterSwordItem.isMasterSword(stack)) {
			original.call(enchants, target);
			return;
		}

		// Not simply skipping the call. areCompatible starts with
		// !enchantment.equals(other), so it reports an enchantment as incompatible
		// with itself, and this filter is therefore also what stops the draw loop
		// picking the same one twice. Dropping it wholesale would hand out
		// "Sharpness IV, Sharpness IV"; only the duplicate is removed here.
		enchants.removeIf(e -> e.enchantment().equals(target.enchantment()));
	}
}
