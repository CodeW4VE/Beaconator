package xyz.w4ve.beaconator.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Layer geometry and block counts for a pyramid shared by a group of beacons.
 *
 * <p>Vanilla validates a beacon by checking, for every layer {@code k} below it, a
 * {@code (2k+1) x (2k+1)} square centred on the beacon column. Put several beacons together and
 * their squares merge into one rectangle, so a footprint of {@code a x b} beacons needs:
 *
 * <pre>
 *   layer k = (2k + a) x (2k + b)
 * </pre>
 *
 * <p>Which is where the shape matters. Four beacons in a row need a 9x13 base, four in a square
 * need 10x10: same beacons, 20 fewer blocks per node, across hundreds of nodes. The footprint is
 * therefore chosen, not assumed, by costing every rectangle that fits the beacons.
 *
 * <table border="1">
 *   <caption>Level 4, blocks per node</caption>
 *   <tr><th>beacons</th><th>footprint</th><th>blocks</th><th>in a row</th></tr>
 *   <tr><td>1</td><td>1x1</td><td>164</td><td>164</td></tr>
 *   <tr><td>2</td><td>1x2</td><td>188</td><td>188</td></tr>
 *   <tr><td>3</td><td>1x3</td><td>212</td><td>212</td></tr>
 *   <tr><td>4</td><td>2x2</td><td>216</td><td>236</td></tr>
 *   <tr><td>5</td><td>2x3</td><td>244</td><td>260</td></tr>
 *   <tr><td>6</td><td>2x3</td><td>244</td><td>284</td></tr>
 * </table>
 *
 * <p>Note the last two rows: five and six cost the same pyramid, because both live in a 2x3
 * footprint. There are only five primary effects, so the sixth beacon adds nothing but a spare,
 * and it does so for the price of one beacon and no extra blocks.
 */
public final class PyramidCalculator {
	public static final int MAX_BEACONS_PER_NODE = 6;
	public static final int MAX_LEVEL = 4;

	/**
	 * How the beacons of a node are arranged.
	 *
	 * @param across beacons across the row axis
	 * @param along  beacons along the row axis
	 */
	public record Footprint(int across, int along) {
		public int slots() {
			return across * along;
		}
	}

	private PyramidCalculator() {
	}

	/**
	 * The cheapest arrangement for this many beacons: every rectangle that holds them, costed by
	 * the blocks its pyramid needs, best one wins. Ties go to the flatter one, which is easier to
	 * lay out along a perimeter edge.
	 */
	public static Footprint footprint(int beacons, int level) {
		checkBeacons(beacons);
		checkLevel(level);

		Footprint best = null;
		int bestCost = Integer.MAX_VALUE;

		for (int along = 1; along <= beacons; along++) {
			int across = Math.ceilDiv(beacons, along);
			int cost = blocks(across, along, level);

			if (cost < bestCost || (cost == bestCost && across < best.across())) {
				best = new Footprint(across, along);
				bestCost = cost;
			}
		}

		return best;
	}

	/** Blocks in one layer of a pyramid under this footprint. */
	public static int blocksInLayer(int depth, Footprint footprint) {
		checkLayer(depth);
		return (2 * depth + footprint.across()) * (2 * depth + footprint.along());
	}

	/** Blocks in layer {@code depth} for this many beacons, using the best footprint. */
	public static int blocksInLayer(int depth, int beacons, int level) {
		return blocksInLayer(depth, footprint(beacons, level));
	}

	/** Total pyramid blocks for a full pyramid of {@code level} layers. */
	public static int totalBlocks(int beacons, int level) {
		checkBeacons(beacons);
		checkLevel(level);
		Footprint footprint = footprint(beacons, level);
		return blocks(footprint.across(), footprint.along(), level);
	}

	private static int blocks(int across, int along, int level) {
		int total = 0;

		for (int depth = 1; depth <= level; depth++) {
			total += (2 * depth + across) * (2 * depth + along);
		}

		return total;
	}

	/**
	 * Layers of the pyramid under a group of beacons, top first.
	 *
	 * @param x    x of the first beacon
	 * @param y    y the beacons sit at; the first layer is at {@code y - 1}
	 * @param z    z of the first beacon
	 * @param axis axis the group grows along
	 */
	public static List<PyramidLayer> layers(int x, int y, int z, int level, int beacons, RowAxis axis) {
		checkLevel(level);
		checkBeacons(beacons);

		Footprint footprint = footprint(beacons, level);
		int extraAlong = footprint.along() - 1;
		int extraAcross = footprint.across() - 1;
		int extraX = axis == RowAxis.X ? extraAlong : extraAcross;
		int extraZ = axis == RowAxis.X ? extraAcross : extraAlong;

		List<PyramidLayer> layers = new ArrayList<>(level);

		for (int depth = 1; depth <= level; depth++) {
			layers.add(new PyramidLayer(depth, y - depth,
					x - depth, x + depth + extraX,
					z - depth, z + depth + extraZ));
		}

		return layers;
	}

	/**
	 * Block positions of every beacon of a node, in order.
	 *
	 * <p>Filled along the row axis first, so a group that does not fill its rectangle leaves the
	 * gap at the end rather than in the middle.
	 */
	public static List<int[]> beaconPositions(int x, int y, int z, int beacons, RowAxis axis) {
		checkBeacons(beacons);

		Footprint footprint = footprint(beacons, MAX_LEVEL);
		List<int[]> positions = new ArrayList<>(beacons);

		for (int across = 0; across < footprint.across() && positions.size() < beacons; across++) {
			for (int along = 0; along < footprint.along() && positions.size() < beacons; along++) {
				int bx = x + (axis == RowAxis.X ? along : across);
				int bz = z + (axis == RowAxis.X ? across : along);
				positions.add(new int[] {bx, y, bz});
			}
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
