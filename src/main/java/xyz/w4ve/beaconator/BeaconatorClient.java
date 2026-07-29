package xyz.w4ve.beaconator;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.slf4j.Logger;
import xyz.w4ve.beaconator.client.BeaconatorHud;
import xyz.w4ve.beaconator.client.Keys;
import xyz.w4ve.beaconator.client.LayerFilter;
import xyz.w4ve.beaconator.client.PlanManager;
import xyz.w4ve.beaconator.client.map.MapStore;
import xyz.w4ve.beaconator.client.net.ClientSync;
import xyz.w4ve.beaconator.command.BeaconatorCommand;
import xyz.w4ve.beaconator.config.BeaconatorConfig;
import xyz.w4ve.beaconator.render.PerimeterRenderer;

public class BeaconatorClient implements ClientModInitializer {
	public static final String MOD_ID = BeaconatorMod.MOD_ID;
	public static final Logger LOGGER = BeaconatorMod.LOGGER;

	@Override
	public void onInitializeClient() {
		BeaconatorConfig.get();
		LayerFilter.load();
		Keys.init();
		BeaconatorCommand.init();
		ClientSync.init();

		WorldRenderEvents.AFTER_TRANSLUCENT.register(PerimeterRenderer::render);
		HudRenderCallback.EVENT.register(BeaconatorHud::render);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> PlanManager.onJoin());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PlanManager.onDisconnect());
		// Draw chunks as they arrive instead of waiting for the sweep to reach them.
		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) ->
				MapStore.onChunkLoaded(chunk.getPos().x, chunk.getPos().z));

		LOGGER.info("Beaconator ready");
	}
}
