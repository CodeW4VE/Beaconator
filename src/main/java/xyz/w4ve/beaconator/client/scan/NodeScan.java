package xyz.w4ve.beaconator.client.scan;

import xyz.w4ve.beaconator.model.MaterialTally;

/**
 * What the world actually looks like at one node.
 *
 * @param loaded          false when the chunks are not in view distance, in which case the
 *                        counts mean nothing and the node is left alone
 * @param expected        blocks the plan asks for here
 * @param found           blocks already in place
 * @param beaconsExpected beacons the plan asks for here
 * @param beaconsFound    beacons already in place
 * @param missing         what is still needed, per block id
 */
public record NodeScan(boolean loaded, int expected, int found, int beaconsExpected, int beaconsFound,
		MaterialTally missing) {
	public static final NodeScan UNLOADED = new NodeScan(false, 0, 0, 0, 0, new MaterialTally());

	/** 0 to 1. Unloaded nodes report 0. */
	public float progress() {
		if (!loaded || expected == 0) {
			return loaded ? 1.0f : 0.0f;
		}

		return Math.min(1.0f, found / (float) expected);
	}

	public boolean complete() {
		return loaded && found >= expected;
	}

	/** True once every beacon of the node is in the world, pyramid or not. */
	public boolean beaconsPlaced() {
		return loaded && beaconsExpected > 0 && beaconsFound >= beaconsExpected;
	}

	public int remaining() {
		return Math.max(0, expected - found);
	}
}
