package re.jerome.mastersword;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import re.jerome.mastersword.command.MasterSwordCommand;
import re.jerome.mastersword.config.MasterSwordConfig;
import re.jerome.mastersword.registry.ModComponents;
import re.jerome.mastersword.registry.ModEntityTypes;
import re.jerome.mastersword.registry.ModItems;

public class MasterSwordMod implements ModInitializer {
	public static final String MOD_ID = "mastersword";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Namespaced identifier helper, used by every registry class of this mod. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// The order matters: touching ModItems runs its class initialiser, which
		// registers the sword with the attack and repair values read from the
		// config, so the config has to be loaded first.
		MasterSwordConfig.load();
		ModComponents.register();
		ModEntityTypes.register();
		ModItems.register();
		MasterSwordCommand.register();
		LOGGER.info("The Master Sword is waiting in the dark forest.");
	}
}
