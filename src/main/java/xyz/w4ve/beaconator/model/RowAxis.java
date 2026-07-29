package xyz.w4ve.beaconator.model;

/**
 * Which way a node's beacons grow away from its corner, as a rotation rather than an axis.
 *
 * <p>The beacons of a node sit in a rectangle on one shared pyramid. This says where that
 * rectangle goes relative to the node: towards +X, +Z, -X or -Z. Two axes were not enough, since
 * they can only push the group one way, and there is no reason every perimeter should grow east
 * and south. Named in degrees, the way schematic mods name it.
 *
 * <p>{@code X} and {@code Z} keep their names so plans written before this still load.
 */
public enum RowAxis {
	/** Towards +X. */
	X(0),
	/** Towards +Z. */
	Z(90),
	/** Towards -X. */
	X_NEGATIVE(180),
	/** Towards -Z. */
	Z_NEGATIVE(270);

	private final int degrees;

	RowAxis(int degrees) {
		this.degrees = degrees;
	}

	public int degrees() {
		return degrees;
	}

	/** True when the group runs along X, whichever way. */
	public boolean isX() {
		return this == X || this == X_NEGATIVE;
	}

	/** +1 when the group grows towards positive coordinates, -1 the other way. */
	public int step() {
		return this == X_NEGATIVE || this == Z_NEGATIVE ? -1 : 1;
	}

	/** The next rotation, a quarter turn on. */
	public RowAxis rotated() {
		return switch (this) {
			case X -> Z;
			case Z -> X_NEGATIVE;
			case X_NEGATIVE -> Z_NEGATIVE;
			case Z_NEGATIVE -> X;
		};
	}

	/** The other horizontal axis, the one the group does not grow along. */
	public RowAxis other() {
		return isX() ? Z : X;
	}
}
