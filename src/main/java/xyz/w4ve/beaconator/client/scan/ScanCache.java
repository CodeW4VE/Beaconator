package xyz.w4ve.beaconator.client.scan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import xyz.w4ve.beaconator.client.PlanManager;
import xyz.w4ve.beaconator.config.BeaconatorConfig;
import xyz.w4ve.beaconator.model.GridNode;
import xyz.w4ve.beaconator.model.MaterialTally;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;

/**
 * Keeps the last scan of every node, refreshing the nearby ones as you build.
 *
 * <p>This is what makes finished nodes turn green on their own while you work, which is the
 * whole point of the exercise: seeing at a glance which ones are still missing.
 */
public final class ScanCache {
	private static final Map<NodeKey, NodeScan> RESULTS = new HashMap<>();
	private static long lastScan;

	private ScanCache() {
	}

	public static NodeScan get(NodeKey key) {
		return RESULTS.get(key);
	}

	public static void clear() {
		RESULTS.clear();
	}

	/**
	 * Forgets the reading of the nodes given, and only those.
	 *
	 * <p>Changing a node's state changes the blocks it asks for, so its last scan is stale. The
	 * other two hundred are not: wiping the lot made every finished node lose its colour on every
	 * click, and only the ones within auto scan range ever got it back.
	 */
	public static void invalidate(Iterable<NodeKey> keys) {
		for (NodeKey key : keys) {
			RESULTS.remove(key);
		}
	}

	public static void invalidate(NodeKey key) {
		RESULTS.remove(key);
	}

	public static void tick(Minecraft mc) {
		PerimeterPlan plan = PlanManager.plan();
		BeaconatorConfig config = BeaconatorConfig.get();

		if (plan == null || mc.level == null || mc.player == null || !config.enabled || !config.autoScan
				|| !PlanManager.inPlanDimension()) {
			return;
		}

		long now = System.currentTimeMillis();

		if (now - lastScan < config.autoScanIntervalMs) {
			return;
		}

		lastScan = now;
		double limit = config.autoScanDistance;

		for (GridNode node : plan.nodes()) {
			double dx = mc.player.getX() - node.x();
			double dz = mc.player.getZ() - node.z();

			if (dx * dx + dz * dz > limit * limit) {
				continue;
			}

			scanAndApply(mc, plan, node);
		}
	}

	/** Full sweep of every node whose chunks are loaded. Returns how many were readable. */
	public static int[] scanAll(Minecraft mc) {
		PerimeterPlan plan = PlanManager.plan();

		if (plan == null || mc.level == null) {
			return new int[] {0, 0, 0};
		}

		List<GridNode> nodes = plan.nodes();
		int scanned = 0;
		int complete = 0;

		for (GridNode node : nodes) {
			scanAndApply(mc, plan, node);
			// Counted off the cache, not off this pass: a node read earlier and now out of view
			// is still a node we know about, and that is what the map is showing.
			NodeScan known = RESULTS.get(node.key());

			if (known != null && known.loaded()) {
				scanned++;

				if (known.complete()) {
					complete++;
				}
			}
		}

		return new int[] {scanned, nodes.size(), complete};
	}

	private static NodeScan scanAndApply(Minecraft mc, PerimeterPlan plan, GridNode node) {
		NodeStatus status = plan.statusAt(node.key());

		if (status == NodeStatus.REMOVED) {
			RESULTS.remove(node.key());
			return NodeScan.UNLOADED;
		}

		NodeScan scan = WorldScanner.scanNode(mc.level, plan, node);

		if (!scan.loaded()) {
			// Walking away is not news about the node. Keep the last real reading so the colour
			// survives the chunks unloading, and only record "unknown" if we never read it.
			RESULTS.putIfAbsent(node.key(), scan);
			return scan;
		}

		RESULTS.put(node.key(), scan);

		// Pending nodes are promoted once their beacons are up. The reverse is deliberately not
		// done: a node that reads as empty is far more often a misread plan (wrong Y, beacon group
		// on the other side) than a dismantled perimeter, and downgrading rewrites the plan on the
		// spot. The progress colour already says "I cannot see it" without destroying anything.
		if (status == NodeStatus.PENDING && scan.beaconsPlaced()) {
			PlanManager.changeStatus(node.key(), NodeStatus.PLACED);
		}

		return scan;
	}

	/**
	 * What is still missing across the plan.
	 *
	 * @return {@code [missing tally of scanned nodes, tally of nodes that could not be read]}
	 */
	/**
	 * Something is built here but the node is not finished: blocks are going in, or the pyramid
	 * is up and a beacon is missing.
	 *
	 * <p>This is the case that needs its own colour. A node one beacon short of working looks
	 * finished from anywhere, and you only find out when the perimeter does not spawn proof.
	 */
	public static boolean partial(NodeKey key) {
		NodeScan scan = RESULTS.get(key);

		if (scan == null || !scan.loaded() || scan.found() <= 0) {
			return false;
		}

		return !scan.complete() || !scan.beaconsPlaced();
	}

	/** Everything the plan asks for at this node is in the world, beacons included. */
	public static boolean finished(NodeKey key) {
		NodeScan scan = RESULTS.get(key);
		return scan != null && scan.loaded() && scan.complete() && scan.beaconsPlaced();
	}

	public static MaterialTally[] missingTotals() {
		PerimeterPlan plan = PlanManager.plan();
		MaterialTally missing = new MaterialTally();
		MaterialTally unknown = new MaterialTally();

		if (plan == null) {
			return new MaterialTally[] {missing, unknown};
		}

		for (GridNode node : plan.nodes()) {
			if (!plan.statusAt(node.key()).isBuilt()) {
				continue;
			}

			NodeScan scan = RESULTS.get(node.key());

			if (scan == null || !scan.loaded()) {
				for (Map.Entry<String, Integer> entry : plan.tallyOf(node).counts().entrySet()) {
					unknown.add(entry.getKey(), entry.getValue());
				}

				continue;
			}

			for (Map.Entry<String, Integer> entry : scan.missing().counts().entrySet()) {
				missing.add(entry.getKey(), entry.getValue());
			}
		}

		return new MaterialTally[] {missing, unknown};
	}
}
