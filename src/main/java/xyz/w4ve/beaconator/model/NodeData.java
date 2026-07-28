package xyz.w4ve.beaconator.model;

/**
 * Per node overrides. Only stored for nodes that differ from the plan defaults, so a fresh
 * plan of 200 nodes is a handful of bytes.
 *
 * @param status   state of the node
 * @param beacons  beacons on this node, or {@code null} to use the plan default. Reserved for
 *                 the expert mode; the UI keeps every node on the same count for now
 * @param y        height of this node's beacons, or {@code null} to use the plan default.
 *                 Reserved for uneven ground
 */
public record NodeData(NodeStatus status, Integer beacons, Integer y) {
	public static final NodeData PENDING = new NodeData(NodeStatus.PENDING, null, null);

	public NodeData withStatus(NodeStatus newStatus) {
		return new NodeData(newStatus, beacons, y);
	}

	public NodeData withBeacons(Integer newBeacons) {
		return new NodeData(status, newBeacons, y);
	}

	public NodeData withY(Integer newY) {
		return new NodeData(status, beacons, newY);
	}

	/** True when nothing here differs from the plan defaults, so it does not need storing. */
	public boolean isDefault() {
		return status == NodeStatus.PENDING && beacons == null && y == null;
	}
}
