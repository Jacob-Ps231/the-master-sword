package re.jerome.mastersword.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import re.jerome.mastersword.MasterSwordMod;
import re.jerome.mastersword.config.MasterSwordConfig;

public final class MasterSwordCommand {
	private MasterSwordCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal(MasterSwordMod.MOD_ID)
						.then(Commands.literal("reload")
								// 26.2 dropped CommandSourceStack.hasPermission(int) entirely.
								// Commands.hasPermission returns a Predicate, which is what
								// requires() wants. LEVEL_GAMEMASTERS is what vanilla /reload uses.
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.executes(context -> {
									MasterSwordConfig.load();
									MasterSwordConfig.ItemConfig item = MasterSwordConfig.get().item();
									context.getSource().sendSuccess(
											() -> Component.translatable(
													"commands.mastersword.reload.success", item.maxDurability()),
											true);
									return 1;
								}))));
	}
}
