package xyz.w4ve.beaconator.model;

/**
 * Horizontal axis a row of beacons runs along.
 *
 * <p>A node holds 1 to 5 beacons side by side on a single shared pyramid. This
 * says whether that row grows towards +X or +Z. The pyramid is stretched along
 * the same axis.
 */
public enum RowAxis {
	X,
	Z;

	/** The other horizontal axis, the one the row does not grow along. */
	public RowAxis other() {
		return this == X ? Z : X;
	}
}
