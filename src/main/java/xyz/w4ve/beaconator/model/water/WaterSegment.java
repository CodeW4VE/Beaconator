package xyz.w4ve.beaconator.model.water;

/**
 * A straight run of channel, from where the water enters it to where it leaves.
 *
 * <p>Always axis aligned and always on one layer: a network on the bottom layer cannot step down
 * to steer the current, so direction is carried here and turned into sources and flow stops when
 * the network is costed. The order of the ends is the direction of flow, which matters: two
 * segments meeting head on is a junction where items pile up unless it is built on purpose.
 *
 * @param kind what this run is for, which is only for drawing and for reading the numbers
 */
public record WaterSegment(int x1, int z1, int x2, int z2, Kind kind) {
	public enum Kind {
		/** Collects one row of nodes. */
		SPINE,
		/** Carries the spines to the middle. */
		TRUNK,
		/** Joins one node to the rest of the network, in a tree. */
		LINK
	}

	public WaterSegment {
		if (x1 != x2 && z1 != z2) {
			throw new IllegalArgumentException("Segments are axis aligned: " + x1 + "," + z1
					+ " to " + x2 + "," + z2);
		}
	}

	/** Blocks of channel this run occupies, counting both ends. */
	public int length() {
		return Math.abs(x2 - x1) + Math.abs(z2 - z1) + 1;
	}

	public boolean alongX() {
		return z1 == z2;
	}
}
