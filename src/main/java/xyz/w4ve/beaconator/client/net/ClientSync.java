package xyz.w4ve.beaconator.client.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import xyz.w4ve.beaconator.client.PlanManager;
import xyz.w4ve.beaconator.io.PlanStore;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.net.NodePayload;
import xyz.w4ve.beaconator.net.PlanPayload;

/**
 * The client half of the shared plan: takes what the server sends and reports back what you do.
 *
 * <p>While a shared plan is open, every node you place, exclude or drop goes to the server and
 * comes back to everyone else, so the perimeter fills in live instead of each person keeping
 * their own increasingly wrong copy.
 */
public final class ClientSync {
	/** True while applying something that came from the server, so it is not echoed straight back. */
	private static boolean applying;
	private static boolean shared;

	private ClientSync() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(PlanPayload.TYPE, (payload, context) ->
				context.client().execute(() -> receivePlan(payload)));

		ClientPlayNetworking.registerGlobalReceiver(NodePayload.TYPE, (payload, context) ->
				context.client().execute(() -> receiveNode(payload)));
	}

	/** True when the open plan is the server's copy rather than a local file. */
	public static boolean shared() {
		return shared;
	}

	/** The server has a plan and this client speaks the protocol. */
	public static boolean available() {
		return ClientPlayNetworking.canSend(PlanPayload.TYPE);
	}

	private static void receivePlan(PlanPayload payload) {
		if (payload.isEmpty()) {
			shared = false;
			return;
		}

		PerimeterPlan received = PlanStore.fromJson(payload.json(), "shared");

		if (received == null) {
			return;
		}

		applying = true;

		try {
			PlanManager.setPlan(received);
			shared = true;
			PlanManager.actionBar(Component.literal("Beaconator: shared plan from the server"));
		} finally {
			applying = false;
		}
	}

	private static void receiveNode(NodePayload payload) {
		PerimeterPlan plan = PlanManager.plan();
		NodeStatus status = payload.state();

		if (!shared || plan == null || status == null) {
			return;
		}

		NodeKey key = new NodeKey(payload.i(), payload.j());

		if (!plan.extents().contains(key.i(), key.j())) {
			return;
		}

		applying = true;

		try {
			plan.setStatus(key, status);
		} finally {
			applying = false;
		}
	}

	/** Tells the server about a node you just changed. Does nothing off a shared plan. */
	public static void sendNode(NodeKey key, NodeStatus status) {
		if (!shared || applying || !ClientPlayNetworking.canSend(NodePayload.TYPE)) {
			return;
		}

		ClientPlayNetworking.send(new NodePayload(key.i(), key.j(), status));
	}

	/** Publishes the open plan for everyone. Operators only, enforced by the server. */
	public static boolean publish() {
		PerimeterPlan plan = PlanManager.plan();

		if (plan == null || !ClientPlayNetworking.canSend(PlanPayload.TYPE)) {
			return false;
		}

		ClientPlayNetworking.send(new PlanPayload(PlanStore.toJson(plan)));
		shared = true;
		return true;
	}

	/** Takes the shared plan down. Everyone keeps what they have open, nobody gets it on join. */
	public static boolean unpublish() {
		if (!ClientPlayNetworking.canSend(PlanPayload.TYPE)) {
			return false;
		}

		ClientPlayNetworking.send(PlanPayload.none());
		shared = false;
		return true;
	}

	/** A local plan opened by hand stops being the shared one. */
	public static void detach() {
		if (!applying) {
			shared = false;
		}
	}
}
