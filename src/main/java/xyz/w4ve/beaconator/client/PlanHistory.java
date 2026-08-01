package xyz.w4ve.beaconator.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import xyz.w4ve.beaconator.model.NodeData;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.PerimeterPlan;

/**
 * Undo for node state changes.
 *
 * <p>Marking a couple of hundred nodes is the kind of job where one stray click is expensive, so
 * every change records the states it is about to overwrite. Grid changes are not covered: those
 * are easy to reverse by hand and would double the bookkeeping.
 */
public final class PlanHistory {
	private static final int MAX_STEPS = 64;

	private record Step(String planName, Map<NodeKey, NodeData> before, String description) {
	}

	private static final Deque<Step> STEPS = new ArrayDeque<>();

	private PlanHistory() {
	}

	public static int size() {
		return STEPS.size();
	}

	public static void clear() {
		STEPS.clear();
	}

	/** Remembers the current state of one node before it is changed. */
	public static void record(PerimeterPlan plan, NodeKey key, String description) {
		record(plan, java.util.List.of(key), description);
	}

	/** Remembers the current state of a batch of nodes before they are changed. */
	public static void record(PerimeterPlan plan, Iterable<NodeKey> keys, String description) {
		if (plan == null) {
			return;
		}

		Map<NodeKey, NodeData> before = new LinkedHashMap<>();

		for (NodeKey key : keys) {
			before.put(key, plan.dataAt(key));
		}

		if (before.isEmpty()) {
			return;
		}

		STEPS.push(new Step(plan.name(), before, description));

		while (STEPS.size() > MAX_STEPS) {
			STEPS.removeLast();
		}
	}

	/**
	 * Puts the last recorded batch back.
	 *
	 * @return a short description of what was undone, or null when there was nothing to undo
	 */
	public static String undo(PerimeterPlan plan) {
		if (plan == null || STEPS.isEmpty()) {
			return null;
		}

		// Steps from another plan are stale; drop them rather than applying them to this one.
		while (!STEPS.isEmpty() && !STEPS.peek().planName().equals(plan.name())) {
			STEPS.pop();
		}

		if (STEPS.isEmpty()) {
			return null;
		}

		Step step = STEPS.pop();

		for (Map.Entry<NodeKey, NodeData> entry : step.before().entrySet()) {
			NodeKey key = entry.getKey();
			NodeData data = entry.getValue();
			PlanManager.changeStatus(key, data.status());

			// Both halves go back, so undoing a drag puts the node where it was rather than
			// leaving it parked wherever it was dropped with its old colour on.
			int[] now = plan.offsetAt(key);

			if (now[0] != data.dx() || now[1] != data.dz()) {
				PlanManager.moveNode(key, data.dx(), data.dz());
			}
		}

		PlanManager.markDirty();
		return step.description() + " (" + step.before().size() + ")";
	}
}
