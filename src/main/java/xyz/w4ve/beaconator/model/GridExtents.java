package xyz.w4ve.beaconator.model;

/**
 * How far the grid reaches from the centre node, counted in nodes per side.
 *
 * <p>{@code i} runs along X, {@code j} along Z. A {@link #ring(int)} of {@code n} gives the
 * concentric square the scroll wheel steps through: {@code (2n+1)^2} nodes, so 1, 9, 25, 49.
 * There is no step at 4 because a 2x2 square has no centre node.
 *
 * <p>Sides are kept independent because real perimeters are not square. The one this mod
 * was written for is 13x16 nodes, which the wheel alone cannot reach.
 */
public record GridExtents(int minI, int maxI, int minJ, int maxJ) {
	public GridExtents {
		if (minI > 0 || minJ > 0 || maxI < 0 || maxJ < 0) {
			throw new IllegalArgumentException("Extents must contain the centre node (0,0)");
		}
	}

	/** Concentric square of radius {@code n} nodes. */
	public static GridExtents ring(int n) {
		if (n < 0) {
			throw new IllegalArgumentException("Ring must be >= 0, got " + n);
		}

		return new GridExtents(-n, n, -n, n);
	}

	public int columns() {
		return maxI - minI + 1;
	}

	public int rows() {
		return maxJ - minJ + 1;
	}

	public int nodeCount() {
		return columns() * rows();
	}

	public boolean contains(int i, int j) {
		return i >= minI && i <= maxI && j >= minJ && j <= maxJ;
	}

	/** The ring this would be if all four sides matched, or -1 when they do not. */
	public int asRing() {
		if (-minI == maxI && -minJ == maxJ && maxI == maxJ) {
			return maxI;
		}

		return -1;
	}

	/** Grows (or shrinks, with a negative amount) one side, never past the centre node. */
	public GridExtents expand(GridSide side, int amount) {
		return switch (side) {
			case WEST -> new GridExtents(Math.min(0, minI - amount), maxI, minJ, maxJ);
			case EAST -> new GridExtents(minI, Math.max(0, maxI + amount), minJ, maxJ);
			case NORTH -> new GridExtents(minI, maxI, Math.min(0, minJ - amount), maxJ);
			case SOUTH -> new GridExtents(minI, maxI, minJ, Math.max(0, maxJ + amount));
		};
	}

	/** Grows all four sides at once, which is what the scroll wheel does. */
	public GridExtents expandAll(int amount) {
		return new GridExtents(
				Math.min(0, minI - amount),
				Math.max(0, maxI + amount),
				Math.min(0, minJ - amount),
				Math.max(0, maxJ + amount));
	}
}
