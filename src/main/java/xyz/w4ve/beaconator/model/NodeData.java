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
 * @param dx       blocks this node sits east of where the grid would put it, 0 while it is on
 *                 the grid. Ground the grid cannot know about is the whole point: a node can
 *                 land on a ravine, on a monument, or just outside the shape being dug
 * @param dz       same along Z
 */
public record NodeData(NodeStatus status, Integer beacons, Integer y, int dx, int dz) {
	public static final NodeData PENDING = new NodeData(NodeStatus.PENDING, null, null, 0, 0);

	/** A node sitting exactly where the grid puts it, which is every node until one is dragged. */
	public NodeData(NodeStatus status, Integer beacons, Integer y) {
		this(status, beacons, y, 0, 0);
	}

	public NodeData withStatus(NodeStatus newStatus) {
		return new NodeData(newStatus, beacons, y, dx, dz);
	}

	public NodeData withBeacons(Integer newBeacons) {
		return new NodeData(status, newBeacons, y, dx, dz);
	}

	public NodeData withY(Integer newY) {
		return new NodeData(status, beacons, newY, dx, dz);
	}

	public NodeData withOffset(int newDx, int newDz) {
		return new NodeData(status, beacons, y, newDx, newDz);
	}

	/** True when this node was nudged off the grid. */
	public boolean moved() {
		return dx != 0 || dz != 0;
	}

	/** True when nothing here differs from the plan defaults, so it does not need storing. */
	public boolean isDefault() {
		return status == NodeStatus.PENDING && beacons == null && y == null && !moved();
	}
}
