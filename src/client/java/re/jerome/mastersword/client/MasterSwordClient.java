package re.jerome.mastersword.client;

import net.fabricmc.api.ClientModInitializer;
import re.jerome.mastersword.MasterSwordMod;

public class MasterSwordClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MasterSwordMod.LOGGER.info("The Master Sword client is ready.");
	}
}
