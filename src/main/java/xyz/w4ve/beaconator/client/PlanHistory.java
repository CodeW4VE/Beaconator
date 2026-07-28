package xyz.w4ve.beaconator.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
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

	private record Step(String planName, Map<NodeKey, NodeStatus> before, String description) {
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

		Map<NodeKey, NodeStatus> before = new LinkedHashMap<>();

		for (NodeKey key : keys) {
			before.put(key, plan.statusAt(key));
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

		for (Map.Entry<NodeKey, NodeStatus> entry : step.before().entrySet()) {
			plan.setStatus(entry.getKey(), entry.getValue());
		}

		PlanManager.markDirty();
		return step.description() + " (" + step.before().size() + ")";
	}
}
