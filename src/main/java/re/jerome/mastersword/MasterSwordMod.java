package re.jerome.mastersword;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MasterSwordMod implements ModInitializer {
	public static final String MOD_ID = "mastersword";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Namespaced identifier helper, used by every registry class of this mod. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		LOGGER.info("The Master Sword is waiting in the dark forest.");
	}
}
