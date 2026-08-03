package xyz.w4ve.beaconator.client.water;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.model.water.WaterNetwork;
import xyz.w4ve.beaconator.model.water.WaterSegment;

/**
 * The measured water network, worked out once per change instead of once per frame.
 *
 * <p>Measuring means flooding the whole channel from the drain, which on a real perimeter is ten
 * thousand blocks. Fine to do when a run is drawn; not fine to do sixty times a second while the
 * render asks which runs are red.
 */
public final class WaterCache {
	private static PerimeterPlan plan;
	private static int revision = -1;
	/**
	 * How many nodes the network had to serve last time. Dropping a node, moving one or excluding
	 * it changes who is an orphan without touching a single run, and a cache that only watched the
	 * runs would keep drawing a red cross on a node that is no longer in the plan.
	 */
	private static int served = -1;
	private static WaterNetwork network;
	private static Set<WaterSegment> blocked = Set.of();

	private WaterCache() {
	}

	/** The network as it stands, recomputed only when the plan's water lines have moved on. */
	public static WaterNetwork network(PerimeterPlan current) {
		if (current == null) {
			return null;
		}

		int nodes = current.buildNodes().size();

		if (network == null || current != plan || current.water().revision() != revision
				|| nodes != served) {
			plan = current;
			revision = current.water().revision();
			served = nodes;
			network = current.water().network(current);
			blocked = new HashSet<>(network.blockedByPyramids());
		}

		return network;
	}

	/** Runs that would be dug through a pyramid base, which is what gets drawn in red. */
	public static boolean blocked(PerimeterPlan current, WaterSegment run) {
		network(current);
		return blocked.contains(run);
	}

	/** Forces the next read to measure again. Cheap insurance when a plan is swapped out. */
	public static void invalidate() {
		network = null;
		plan = null;
		revision = -1;
		served = -1;
		blocked = Set.of();
	}

	/** Where everything drains to, as {@code {x, z}}, or null without a plan. */
	public static int[] drain(PerimeterPlan current) {
		WaterNetwork measured = network(current);
		return measured == null ? null : measured.drain();
	}

	/** Nodes the network does not reach, so the map can say which ones are on their own. */
	public static List<xyz.w4ve.beaconator.model.NodeKey> orphans(PerimeterPlan current) {
		WaterNetwork measured = network(current);
		return measured == null ? List.of() : measured.orphans();
	}
}
