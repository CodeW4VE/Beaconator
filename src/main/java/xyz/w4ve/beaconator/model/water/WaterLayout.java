package xyz.w4ve.beaconator.model.water;

/**
 * Shape of the network that carries items from the beacons to the middle.
 *
 * <p>Both shapes are rectilinear and both run on one layer, so what separates them is only how
 * much channel they need and how hard they are to build in order. The budget prints them side by
 * side because on a solid blob of nodes they land close together, and on a hollow perimeter they
 * do not.
 */
public enum WaterLayout {
	/**
	 * One spine per row of nodes, every spine feeding a single trunk that runs to the middle.
	 * Regular, easy to build a row at a time, and easy to reason about when an item stops.
	 */
	FISHBONE,
	/**
	 * Every node joins the nearest line already drawn, growing outwards from the middle. Shorter
	 * than a fishbone whenever the live nodes are not a solid rectangle, at the cost of a network
	 * whose shape nobody can hold in their head.
	 */
	TREE
}
