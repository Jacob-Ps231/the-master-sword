package re.jerome.mastersword.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import re.jerome.mastersword.MasterSwordMod;
import re.jerome.mastersword.config.MasterSwordConfig;
import re.jerome.mastersword.worldgen.ShrineScan;

public final class MasterSwordCommand {
	private MasterSwordCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LiteralArgumentBuilder<CommandSourceStack> root =
					Commands.literal(MasterSwordMod.MOD_ID).then(reload());

			// A measuring instrument, not a feature: it answers SPEC question 3
			// (how often the shrine lands on a biome edge) and has no business in
			// the published mod.
			if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
				root.then(ShrineScan.command());
			}

			dispatcher.register(root);
		});
	}

	private static LiteralArgumentBuilder<CommandSourceStack> reload() {
		return Commands.literal("reload")
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
				});
	}
}
