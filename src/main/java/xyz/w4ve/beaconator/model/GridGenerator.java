package xyz.w4ve.beaconator.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Places the nodes of the grid from a centre point, a spacing and how far it reaches.
 *
 * <p>Node {@code (i, j)} sits at {@code (centerX + i * spacing, centerZ + j * spacing)}, so the
 * centre node is exactly where the player put it and every other node is an exact multiple
 * of the spacing away.
 */
public final class GridGenerator {
	private GridGenerator() {
	}

	/** Every node inside the extents, ordered by row then column. */
	public static List<GridNode> nodes(int centerX, int y, int centerZ, int spacing, GridExtents extents) {
		checkSpacing(spacing);

		List<GridNode> nodes = new ArrayList<>(extents.nodeCount());

		for (int j = extents.minJ(); j <= extents.maxJ(); j++) {
			for (int i = extents.minI(); i <= extents.maxI(); i++) {
				nodes.add(nodeAt(centerX, y, centerZ, spacing, i, j));
			}
		}

		return nodes;
	}

	public static GridNode nodeAt(int centerX, int y, int centerZ, int spacing, int i, int j) {
		checkSpacing(spacing);
		return new GridNode(i, j, centerX + i * spacing, y, centerZ + j * spacing);
	}

	/** Grid indices of the node closest to a world position, whether or not it is inside the extents. */
	public static NodeKey nearestKey(int centerX, int centerZ, int spacing, double x, double z) {
		checkSpacing(spacing);
		return new NodeKey(
				(int) Math.round((x - centerX) / (double) spacing),
				(int) Math.round((z - centerZ) / (double) spacing));
	}

	private static void checkSpacing(int spacing) {
		if (spacing < 1) {
			throw new IllegalArgumentException("Spacing must be >= 1, got " + spacing);
		}
	}
}
