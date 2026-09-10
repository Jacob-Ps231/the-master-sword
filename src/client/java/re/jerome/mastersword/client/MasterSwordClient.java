package re.jerome.mastersword.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import re.jerome.mastersword.registry.ModEntityTypes;
import re.jerome.mastersword.MasterSwordMod;

public class MasterSwordClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntityTypes.LIGHT_WAVE, LightWaveRenderer::new);
		MasterSwordMod.LOGGER.info("The Master Sword client is ready.");
	}
}
