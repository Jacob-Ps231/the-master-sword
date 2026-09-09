package re.jerome.mastersword.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.commands.EnchantCommand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import re.jerome.mastersword.item.MasterSwordItem;

// The third and last caller that checks exclusivity: /enchant. Administrative
// only, but it is what makes the feature testable in one command instead of
// farming enchanted books.
@Mixin(EnchantCommand.class)
public class EnchantCommandMixin {
	@ModifyExpressionValue(
			method = "enchant(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/Collection;Lnet/minecraft/core/Holder;I)I",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;isEnchantmentCompatible(Ljava/util/Collection;Lnet/minecraft/core/Holder;)Z"))
	private static boolean mastersword$allowExclusiveEnchantments(boolean original, @Local ItemStack item) {
		return original || MasterSwordItem.isMasterSword(item);
	}
}
