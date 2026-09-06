package re.jerome.mastersword.item;

import net.minecraft.world.item.Item;

// A bare Item today. It exists this early so ItemStackMixin can recognise the
// sword with an instanceof -- getMaxDamage is a hot path, and referencing
// ModItems from a mixin would trigger its class initialiser, and with it the
// item registration, at an unpredictable moment.
//
// Steps 4 and 6 will fill it in: durability regeneration in inventoryTick, and
// the light wave on a charged attack.
public class MasterSwordItem extends Item {
	public MasterSwordItem(Properties properties) {
		super(properties);
	}
}
