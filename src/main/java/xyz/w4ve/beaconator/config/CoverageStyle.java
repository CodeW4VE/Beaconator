package xyz.w4ve.beaconator.config;

/**
 * How much of a beacon's coverage volume to actually draw.
 *
 * <p>The real volume is unbounded upwards, so drawing it whole for two hundred nodes would
 * leave you staring at a wall of translucent boxes. A slab reads far better from the air.
 */
public enum CoverageStyle {
	/** A thin slab at beacon height. Best for reading the grid from above. */
	SLAB,
	/** A slab at the lowest covered block, so you can see how deep the effect reaches. */
	FLOOR,
	/** The whole thing, from the lowest covered block up to the world ceiling. */
	FULL
}
