package xyz.w4ve.beaconator.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Layer geometry and block counts for a pyramid shared by a row of beacons.
 *
 * <p>Vanilla validates a beacon by checking, for every layer {@code k} below it, a
 * {@code (2k+1) x (2k+1)} square centred on the beacon column. Put {@code n} beacons in a
 * row and the squares of each one merge into a single rectangle, so layer {@code k}
 * becomes:
 *
 * <pre>
 *   (2k + 1)  across  x  (2k + n)  along the row
 * </pre>
 *
 * <p>Which is why five level 4 beacons need a 9x13 base instead of five separate 9x9 ones.
 * Worked examples:
 *
 * <table border="1">
 *   <caption>Layers from top to bottom</caption>
 *   <tr><th>n</th><th>level</th><th>layers</th><th>blocks</th></tr>
 *   <tr><td>1</td><td>4</td><td>3x3, 5x5, 7x7, 9x9</td><td>164</td></tr>
 *   <tr><td>2</td><td>4</td><td>3x4, 5x6, 7x8, 9x10</td><td>188</td></tr>
 *   <tr><td>5</td><td>4</td><td>3x7, 5x9, 7x11, 9x13</td><td>260</td></tr>
 * </table>
 */
public final class PyramidCalculator {
	public static final int MAX_BEACONS_PER_NODE = 5;
	public static final int MAX_LEVEL = 4;

	private PyramidCalculator() {
	}

	/** Blocks in layer {@code depth} of a pyramid holding {@code beacons} beacons. */
	public static int blocksInLayer(int depth, int beacons) {
		checkLayer(depth);
		checkBeacons(beacons);
		return (2 * depth + 1) * (2 * depth + beacons);
	}

	/** Total pyramid blocks for a full pyramid of {@code level} layers. */
	public static int totalBlocks(int beacons, int level) {
		checkLevel(level);
		checkBeacons(beacons);

		int total = 0;

		for (int depth = 1; depth <= level; depth++) {
			total += blocksInLayer(depth, beacons);
		}

		return total;
	}

	/**
	 * Layers of the pyramid under a row of beacons, top first.
	 *
	 * @param x     x of the first beacon of the row
	 * @param y     y the beacons sit at; the first layer is at {@code y - 1}
	 * @param z     z of the first beacon of the row
	 * @param axis  axis the row grows along
	 */
	public static List<PyramidLayer> layers(int x, int y, int z, int level, int beacons, RowAxis axis) {
		checkLevel(level);
		checkBeacons(beacons);

		List<PyramidLayer> layers = new ArrayList<>(level);
		int extra = beacons - 1;

		for (int depth = 1; depth <= level; depth++) {
			int minX = x - depth;
			int maxX = x + depth + (axis == RowAxis.X ? extra : 0);
			int minZ = z - depth;
			int maxZ = z + depth + (axis == RowAxis.Z ? extra : 0);
			layers.add(new PyramidLayer(depth, y - depth, minX, maxX, minZ, maxZ));
		}

		return layers;
	}

	/** Block positions of every beacon of a row, in order. */
	public static List<int[]> beaconPositions(int x, int y, int z, int beacons, RowAxis axis) {
		checkBeacons(beacons);

		List<int[]> positions = new ArrayList<>(beacons);

		for (int i = 0; i < beacons; i++) {
			int bx = x + (axis == RowAxis.X ? i : 0);
			int bz = z + (axis == RowAxis.Z ? i : 0);
			positions.add(new int[] {bx, y, bz});
		}

		return positions;
	}

	private static void checkBeacons(int beacons) {
		if (beacons < 1 || beacons > MAX_BEACONS_PER_NODE) {
			throw new IllegalArgumentException("Beacons per node must be 1.." + MAX_BEACONS_PER_NODE + ", got " + beacons);
		}
	}

	private static void checkLevel(int level) {
		if (level < 1 || level > MAX_LEVEL) {
			throw new IllegalArgumentException("Pyramid level must be 1.." + MAX_LEVEL + ", got " + level);
		}
	}

	private static void checkLayer(int depth) {
		if (depth < 1 || depth > MAX_LEVEL) {
			throw new IllegalArgumentException("Pyramid layer must be 1.." + MAX_LEVEL + ", got " + depth);
		}
	}
}
