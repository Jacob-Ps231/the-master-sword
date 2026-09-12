package re.jerome.mastersword.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Fires when a player pulls a sword out of a pedestal.
 *
 * A trigger of our own rather than minecraft:inventory_changed, which would also
 * fire on a /give or on taking the sword out of the creative menu -- and an
 * advancement for drawing the legendary sword means nothing if it can be had by
 * typing a command.
 *
 * Deliberately generic: the Java only says "a sword was drawn", and the
 * advancement JSON says which sword counts, through the item predicate. That is
 * how UsedTotemTrigger is built, it keeps the balance decision in data, and it
 * leaves room for another advancement later without touching this class.
 */
public class DrawnFromStoneTrigger extends SimpleCriterionTrigger<DrawnFromStoneTrigger.TriggerInstance> {
	@Override
	public Codec<DrawnFromStoneTrigger.TriggerInstance> codec() {
		return DrawnFromStoneTrigger.TriggerInstance.CODEC;
	}

	/** @param drawn the stack as it left the pedestal, enchantments and damage included. */
	public void trigger(ServerPlayer player, ItemStack drawn) {
		this.trigger(player, instance -> instance.matches(drawn));
	}

	public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ItemPredicate> item)
			implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<DrawnFromStoneTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
				i -> i.group(
								EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player")
										.forGetter(DrawnFromStoneTrigger.TriggerInstance::player),
								ItemPredicate.CODEC.optionalFieldOf("item")
										.forGetter(DrawnFromStoneTrigger.TriggerInstance::item))
						.apply(i, DrawnFromStoneTrigger.TriggerInstance::new));

		/** An absent predicate matches any sword, which is what "no item given" should mean. */
		public boolean matches(ItemStack drawn) {
			return this.item.isEmpty() || this.item.get().test(drawn);
		}
	}
}
