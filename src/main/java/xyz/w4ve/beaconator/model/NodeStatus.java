package xyz.w4ve.beaconator.model;

/** What the plan says about a node. */
public enum NodeStatus {
	/** In the plan and not built yet. Drawn white. */
	PENDING,
	/** Found in the world by the scan. Drawn faint green. */
	PLACED,
	/**
	 * Outside the perimeter but still built, and topped with a marker block so it reads as
	 * "not one of ours" from a distance. Drawn dark.
	 */
	EXCLUDED,
	/** Dropped from the plan. Not drawn, not counted, not placed. */
	REMOVED;

	/** Whether this node contributes blocks to the material list. */
	public boolean isBuilt() {
		return this != REMOVED;
	}
}
