package re.jerome.mastersword.item;

import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import re.jerome.mastersword.config.MasterSwordConfig.LightWaveConfig;
import re.jerome.mastersword.entity.LightWaveEntity;
import re.jerome.mastersword.registry.ModEntityTypes;
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

	/**
	 * Shared by every mixin that needs to single this sword out. It lives here
	 * rather than on ModItems because this class has no static fields: touching it
	 * from a mixin cannot trigger an item registration at an unexpected moment.
	 */
	public static boolean isMasterSword(ItemStack stack) {
		return stack.getItem() instanceof MasterSwordItem;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity mob, LivingEntity attacker) {
		// Item.postHurtEnemy is empty in 26.2; the weapon's durability cost is
		// applied by ItemStack.postHurtEnemy, after this returns. The order does
		// not matter here -- the timestamp is written either way.
		super.postHurtEnemy(stack, mob, attacker);
		markCombatUse(stack, attacker.level());
	}

	// Server-side only, once per tick per stack: the ServerLevel parameter type
	// makes that structural rather than conventional.
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		regenerate(stack, level);
	}

	/**
	 * Advances the healing of one stack by one tick.
	 *
	 * Public because the pedestal has to call it too: Item.inventoryTick never fires
	 * for a stack held by a block entity, so a sword resting on its pedestal would
	 * otherwise be the one place it does not mend.
	 *
	 * @return whether the stack was actually written to. The pedestal resynchronises
	 *     to its watching clients on true, and true is rare -- a point of durability
	 *     takes about 24 seconds at the defaults -- so a pedestal is quiet almost
	 *     every tick. inventoryTick ignores the answer: a stack in an inventory is
	 *     already synchronised by the container.
	 */
	public static boolean regenerate(ItemStack stack, ServerLevel level) {
		int damage = stack.getDamageValue();
		Long last = stack.get(ModComponents.LAST_COMBAT_USE);

		if (damage <= 0) {
			// At rest. Dropping the component is what stops an untouched sword from
			// banking days of credit it would spend on its first scratch.
			if (last != null) {
				stack.remove(ModComponents.LAST_COMBAT_USE);
				return true;
			}

			return false;
		}

		long now = level.getOverworldClockTime();

		if (last == null) {
			// The component doubles as the "healing has begun" flag. It is only set
			// once the sword has actually dropped below the threshold, so a sword
			// scratched down to 90% simply wears like any other; below half it starts
			// mending, and then goes all the way back to full.
			int maxDamage = stack.getMaxDamage();
			int remaining = maxDamage - damage;
			if (remaining > MasterSwordConfig.get().item().regenStartsBelow() * maxDamage) {
				return false;
			}

			stack.set(ModComponents.LAST_COMBAT_USE, now);
			return true;
		}

		if (last > now) {
			// Happens after /time set moves the clock backwards.
			stack.set(ModComponents.LAST_COMBAT_USE, now);
			return true;
		}

		long fullRegenTicks = fullRegenTicks();
		int maxDamage = stack.getMaxDamage();
		long elapsed = now - last;
		long healed = (long) maxDamage * elapsed / fullRegenTicks;
		if (healed <= 0) {
			return false;
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
		return true;
	}

	private static long fullRegenTicks() {
		float days = MasterSwordConfig.get().item().fullRegenDays();
		return Math.max(1L, (long) (days * SharedConstants.TICKS_PER_GAME_DAY));
	}

	/**
	 * Restarts the regeneration count. Both the melee hook and the beam call it.
	 *
	 * Only when healing is already under way: the component's absence means the
	 * sword is not mending yet, and writing a timestamp would arm it regardless of
	 * the threshold.
	 */
	public static void markCombatUse(ItemStack stack, Level level) {
		if (stack.has(ModComponents.LAST_COMBAT_USE)) {
			stack.set(ModComponents.LAST_COMBAT_USE, level.getOverworldClockTime());
		}
	}

	/**
	 * Throws the beam, if the cooldown allows. Both attack hooks funnel here: a
	 * swing that hits nothing and a swing that lands travel different server paths,
	 * but the shot itself is the same.
	 */
	public static void tryFireLightWave(ServerPlayer player, ItemStack stack) {
		LightWaveConfig cfg = MasterSwordConfig.get().lightWave();
		if (!cfg.enabled() || player.getCooldowns().isOnCooldown(stack)) {
			return;
		}

		// The Zelda rule: the beam only answers to a player at full hearts. Losing
		// half a heart costs the ranged attack until it is healed back.
		//
		// Compared the way the HUD draws it, which rounds up. Raw health would
		// refuse the beam at 19.6 out of 20 -- ten full hearts on screen, and
		// regeneration heals in steps of 1.0, so that state lasts a whole tick of
		// natural regeneration and reads as a bug.
		if (cfg.requiresFullHealth()
				&& Mth.ceil(player.getHealth()) < Mth.ceil(player.getMaxHealth())) {
			return;
		}

		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle().normalize();

		LightWaveEntity wave = new LightWaveEntity(ModEntityTypes.LIGHT_WAVE, level);
		wave.setOwner(player);
		wave.setPos(player.getEyePosition().add(look.scale(0.6)));
		// shoot rather than setDeltaMovement: it sets the rotation from the
		// direction too, which is what the renderer orients the quad by.
		wave.shoot(look.x, look.y, look.z, cfg.speed(), 0.0F);
		wave.setDamage(meleeDamage(player) * cfg.damageRatio());
		level.addFreshEntity(wave);

		// Volume above 1 also stretches how far the sound carries -- roughly 16
		// blocks per unit -- so this one reaches about 24 blocks rather than 13.
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 1.4F);

		// The cost is paid whether or not the beam connects: throwing it is the use.
		if (cfg.durabilityCost() > 0) {
			stack.hurtAndBreak(cfg.durabilityCost(), player, EquipmentSlot.MAINHAND);
		}

		player.getCooldowns().addCooldown(stack, cfg.cooldownTicks());
		markCombatUse(stack, level);
	}

	private static float meleeDamage(ServerPlayer player) {
		return (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
	}
}
