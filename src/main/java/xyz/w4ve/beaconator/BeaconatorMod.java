package xyz.w4ve.beaconator;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.w4ve.beaconator.net.NodePayload;
import xyz.w4ve.beaconator.net.PlanListPayload;
import xyz.w4ve.beaconator.net.PlanPayload;
import xyz.w4ve.beaconator.net.PlanRequestPayload;
import xyz.w4ve.beaconator.server.ServerPlan;

/**
 * The half of the mod that runs on both sides.
 *
 * <p>Beaconator is a client tool, but a perimeter is built by a team, and a plan that only exists
 * on one laptop means everyone else is guessing. On a server this half keeps the shared plan and
 * hands it out; installed client-side only, none of it ever runs and the mod behaves exactly as
 * it did before.
 */
public class BeaconatorMod implements ModInitializer {
	public static final String MOD_ID = "beaconator";
	public static final Logger LOGGER = LoggerFactory.getLogger("Beaconator");

	@Override
	public void onInitialize() {
		// Every payload, in every direction it actually travels, before anything registers a
		// receiver for it: Fabric throws at startup if a handler names a type nobody declared.
		PayloadTypeRegistry.playS2C().register(PlanListPayload.TYPE, PlanListPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PlanPayload.TYPE, PlanPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PlanPayload.TYPE, PlanPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PlanRequestPayload.TYPE, PlanRequestPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(NodePayload.TYPE, NodePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(NodePayload.TYPE, NodePayload.CODEC);

		ServerPlan.init();
	}
}
