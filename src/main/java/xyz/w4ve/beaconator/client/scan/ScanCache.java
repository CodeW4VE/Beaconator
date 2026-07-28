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

	public static void tick(Minecraft mc) {
		PerimeterPlan plan = PlanManager.plan();
		BeaconatorConfig config = BeaconatorConfig.get();

		if (plan == null || mc.level == null || mc.player == null || !config.autoScan
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
			NodeScan scan = scanAndApply(mc, plan, node);

			if (scan.loaded()) {
				scanned++;

				if (scan.complete()) {
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
		RESULTS.put(node.key(), scan);

		if (!scan.loaded()) {
			return scan;
		}

		// Only pending and placed flip automatically. Excluded and removed are the player's call.
		if (status == NodeStatus.PENDING && scan.beaconsPlaced()) {
			plan.setStatus(node.key(), NodeStatus.PLACED);
			PlanManager.markDirty();
		} else if (status == NodeStatus.PLACED && !scan.beaconsPlaced()) {
			plan.setStatus(node.key(), NodeStatus.PENDING);
			PlanManager.markDirty();
		}

		return scan;
	}

	/**
	 * What is still missing across the plan.
	 *
	 * @return {@code [missing tally of scanned nodes, tally of nodes that could not be read]}
	 */
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
