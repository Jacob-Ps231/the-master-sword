package re.jerome.mastersword.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import re.jerome.mastersword.registry.ModBlockEntities;
import re.jerome.mastersword.registry.ModEntityTypes;
import re.jerome.mastersword.MasterSwordMod;

public class MasterSwordClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntityTypes.LIGHT_WAVE, LightWaveRenderer::new);
		// BlockEntityRenderers has no register in 26.2 -- vanilla builds the map
		// itself -- so this goes through Fabric.
		BlockEntityRendererRegistry.register(ModBlockEntities.PEDESTAL, PedestalRenderer::new);
		// Starts listening for shrines coming in and out of the loaded chunks. The
		// fog itself is applied from FogRendererMixin, at the tail of the frame's
		// fog setup.
		FogHandler.register();
		MasterSwordMod.LOGGER.info("The Master Sword client is ready.");
	}
}
