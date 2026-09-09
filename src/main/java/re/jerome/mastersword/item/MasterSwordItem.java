package re.jerome.mastersword.item;

import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import re.jerome.mastersword.config.MasterSwordConfig;
import re.jerome.mastersword.registry.ModComponents;

// The sword repairs itself over time as long as it is not used in combat.
//
// The count follows the *world clock* (Level.getOverworldClockTime), not
// getGameTime. In 26.2 the day/night cycle lives in a separate ServerClockManager,
// and sleeping through a night advances only that clock -- getGameTime is a plain
// tick counter that never jumps. Since the rule is written in days and nights, the
// world clock is also the literal reading of it.
//
// getOverworldClockTime rather than getDefaultClockTime: the latter resolves the
// clock of the current dimension, which is empty in the Nether and the End, and
// would report 0 there.
public class MasterSwordItem extends Item {
	public MasterSwordItem(Properties properties) {
		super(properties);
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity mob, LivingEntity attacker) {
		// Item.postHurtEnemy is empty in 26.2; the weapon's durability cost is
		// applied by ItemStack.postHurtEnemy, after this returns. The order does
		// not matter here -- the timestamp is written either way.
		super.postHurtEnemy(stack, mob, attacker);
		stack.set(ModComponents.LAST_COMBAT_USE, attacker.level().getOverworldClockTime());
	}

	// Server-side only, once per tick per stack: the ServerLevel parameter type
	// makes that structural rather than conventional.
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		regenerate(stack, level);
	}

	private static void regenerate(ItemStack stack, ServerLevel level) {
		int damage = stack.getDamageValue();
		Long last = stack.get(ModComponents.LAST_COMBAT_USE);

		if (damage <= 0) {
			// At rest. Dropping the component is what stops an untouched sword from
			// banking days of credit it would spend on its first scratch.
			if (last != null) {
				stack.remove(ModComponents.LAST_COMBAT_USE);
			}

			return;
		}

		long now = level.getOverworldClockTime();

		// No component yet means the wear came from breaking blocks, which the spec
		// does not count as combat: start the count from here rather than healing
		// everything at once.
		if (last == null || last > now) {
			// last > now happens after /time set moves the clock backwards.
			stack.set(ModComponents.LAST_COMBAT_USE, now);
			return;
		}

		long fullRegenTicks = fullRegenTicks();
		int maxDamage = stack.getMaxDamage();
		long elapsed = now - last;
		long healed = (long) maxDamage * elapsed / fullRegenTicks;
		if (healed <= 0) {
			return;
		}

		// Only the time actually converted into durability is consumed; the
		// remainder stays banked in the timestamp. Rewriting it to now would drop
		// that remainder, and would resynchronise the stack to the client every tick.
		//
		// Rounded up, not down. A point costs fullRegenTicks/maxDamage ticks --
		// 23.6 at the defaults -- and charging the floor of that hands back more
		// time than was spent, every time, with nothing to correct it: measured at
		// 2.7% too fast at the defaults, and 49% once maxDamage outgrows
		// fullRegenTicks.
		//
		// Rounding up errs slow instead, which is the safer direction. Neither
		// rounding is good everywhere though: the error is bounded by the cost of
		// one point, so it stays small while a point costs many ticks and grows
		// once it costs only one or two (+27% at 2.4 ticks a point, +69% at 1.2).
		// Holding maxDamage under fullRegenTicks/20 keeps it below 5%; the
		// defaults sit at 23.6 ticks a point, for 1.5%.
		long consumed = (healed * fullRegenTicks + maxDamage - 1) / maxDamage;
		stack.setDamageValue((int) Math.max(0, damage - healed));
		stack.set(ModComponents.LAST_COMBAT_USE, last + consumed);
	}

	private static long fullRegenTicks() {
		float days = MasterSwordConfig.get().item().fullRegenDays();
		return Math.max(1L, (long) (days * SharedConstants.TICKS_PER_GAME_DAY));
	}
}
