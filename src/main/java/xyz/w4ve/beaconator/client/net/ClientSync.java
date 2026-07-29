package xyz.w4ve.beaconator.client.net;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import xyz.w4ve.beaconator.client.Lang;
import xyz.w4ve.beaconator.client.PlanManager;
import xyz.w4ve.beaconator.client.scan.ScanCache;
import xyz.w4ve.beaconator.io.PlanStore;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.net.NodePayload;
import xyz.w4ve.beaconator.net.PlanListPayload;
import xyz.w4ve.beaconator.net.PlanPayload;
import xyz.w4ve.beaconator.net.PlanRequestPayload;

/**
 * The client half of the shared plans: what the server has, and what you do to the one you opened.
 *
 * <p>While a shared plan is open, every node you place, exclude or drop goes to the server and
 * comes back to everyone else, so the perimeter fills in live instead of each person keeping
 * their own increasingly wrong copy.
 */
public final class ClientSync {
	/** True while applying something that came from the server, so it is not echoed straight back. */
	private static boolean applying;
	/** The shared plan currently open, or empty when the open plan is a local file. */
	private static String openShared = "";
	private static List<PlanListPayload.Entry> available = new ArrayList<>();

	private ClientSync() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(PlanListPayload.TYPE, (payload, context) ->
				context.client().execute(() -> available = new ArrayList<>(payload.entries())));

		ClientPlayNetworking.registerGlobalReceiver(PlanPayload.TYPE, (payload, context) ->
				context.client().execute(() -> receivePlan(payload)));

		ClientPlayNetworking.registerGlobalReceiver(NodePayload.TYPE, (payload, context) ->
				context.client().execute(() -> receiveNode(payload)));
	}

	/** The plans the server is holding. Empty when the server does not have the mod. */
	public static List<PlanListPayload.Entry> available() {
		return available;
	}

	/** True when the open plan is one of the server's rather than a local file. */
	public static boolean shared() {
		return !openShared.isEmpty();
	}

	public static String openShared() {
		return openShared;
	}

	/** The server has the mod and this client can talk to it. */
	public static boolean connected() {
		return ClientPlayNetworking.canSend(PlanListPayload.TYPE);
	}

	public static void forget() {
		available = new ArrayList<>();
		openShared = "";
	}

	// --------------------------------------------------------------- receiving

	private static void receivePlan(PlanPayload payload) {
		PerimeterPlan received = PlanStore.fromJson(payload.json(), payload.name());

		if (received == null) {
			return;
		}

		applying = true;

		try {
			PlanManager.setPlan(received);
			openShared = payload.name();
			ScanCache.clear();
			PlanManager.actionBar(Component.literal(Lang.t("share.opened", payload.name())));
		} finally {
			applying = false;
		}
	}

	private static void receiveNode(NodePayload payload) {
		PerimeterPlan plan = PlanManager.plan();
		NodeStatus status = payload.state();

		if (!payload.plan().equals(openShared) || plan == null || status == null) {
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

	// ----------------------------------------------------------------- sending

	/** Tells the server about a node you just changed. Does nothing off a shared plan. */
	public static void sendNode(NodeKey key, NodeStatus status) {
		if (!shared() || applying || !ClientPlayNetworking.canSend(NodePayload.TYPE)) {
			return;
		}

		ClientPlayNetworking.send(new NodePayload(openShared, key.i(), key.j(), status));
	}

	/** Asks for one of the server's plans. It arrives as a {@link PlanPayload} and opens itself. */
	public static boolean open(String name) {
		if (!connected()) {
			return false;
		}

		ClientPlayNetworking.send(new PlanRequestPayload(name));
		return true;
	}

	/** Puts the open plan on the server under its own name. Anyone with the mod may do this. */
	public static boolean share() {
		PerimeterPlan plan = PlanManager.plan();

		if (plan == null || !connected()) {
			return false;
		}

		ClientPlayNetworking.send(new PlanPayload(plan.name(), PlanStore.toJson(plan)));
		openShared = plan.name();
		return true;
	}

	/** Takes a plan off the server. Everyone keeps whatever they have open. */
	public static boolean remove(String name) {
		if (!connected()) {
			return false;
		}

		ClientPlayNetworking.send(new PlanPayload(name, ""));

		if (name.equals(openShared)) {
			openShared = "";
		}

		return true;
	}

	/** A local plan opened by hand stops being the shared one. */
	public static void detach() {
		if (!applying) {
			openShared = "";
		}
	}
}
