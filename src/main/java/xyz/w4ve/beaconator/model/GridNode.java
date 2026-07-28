package xyz.w4ve.beaconator.model;

/**
 * A node of the grid: one shared pyramid holding a row of beacons.
 *
 * <p>{@code i} and {@code j} are grid indices, {@code (0, 0)} being the centre. {@code x},
 * {@code y} and {@code z} are the block position of the <b>first</b> beacon of the row;
 * the rest follow along {@link PerimeterPlan#rowAxis()}.
 */
public record GridNode(int i, int j, int x, int y, int z) {
	public NodeKey key() {
		return new NodeKey(i, j);
	}
}
