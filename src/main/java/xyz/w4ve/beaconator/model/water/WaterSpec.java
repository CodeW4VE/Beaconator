package xyz.w4ve.beaconator.model.water;

/**
 * The knobs of a water network, kept apart from the plan because they say nothing about the
 * beacons: the same perimeter can be costed with several of these.
 *
 * @param layout     shape of the network
 * @param y          the layer the ice floor sits on; the water runs one block above it
 * @param rowStep    only every nth row of nodes gets a spine, so partial coverage can be costed
 *                   without editing the plan. 1 is every row
 * @param iceBlock   what goes under the water. Any block goes: the ice is picked the same way the
 *                   pyramid block is, so a plan can be costed in packed ice or blue ice without
 *                   the mod having an opinion
 * @param sourceEvery how far a source of water carries before the next one is needed
 * @param sink       the block everything drains into, or null for the middle of the grid. This is
 *                   the mouth of the digsort, which is a place you look at in the world rather
 *                   than a number: the centre of a perimeter is rarely where the sorter ended up
 */
public record WaterSpec(WaterLayout layout, int y, int rowStep, String iceBlock, int sourceEvery,
		Sink sink) {
	/** Where the network drains, in world coordinates. Y is the channel's, so it is not kept. */
	public record Sink(int x, int z) {
	}

	/**
	 * The first layer above the bedrock floor.
	 *
	 * <p>Bedrock fills y = -64 and appears at random up to y = -60, so -59 is the deepest layer
	 * that is guaranteed to be diggable everywhere. It is also where a level 4 pyramid under
	 * beacons at y = -55 has its base, which is the whole reason this network is free real estate:
	 * the perimeter is already being dug at exactly this height.
	 */
	public static final int BOTTOM_LAYER = -59;

	public static final String PACKED_ICE = "minecraft:packed_ice";
	public static final String BLUE_ICE = "minecraft:blue_ice";

	public WaterSpec {
		if (rowStep < 1) {
			throw new IllegalArgumentException("Row step must be >= 1, got " + rowStep);
		}

		if (sourceEvery < 1) {
			throw new IllegalArgumentException("Source spacing must be >= 1, got " + sourceEvery);
		}
	}

	/** Every row, packed ice, on the bottom layer, draining to the middle. */
	public static WaterSpec defaults() {
		return new WaterSpec(WaterLayout.FISHBONE, BOTTOM_LAYER, 1, PACKED_ICE, 8, null);
	}

	public WaterSpec with(WaterLayout other) {
		return new WaterSpec(other, y, rowStep, iceBlock, sourceEvery, sink);
	}

	public WaterSpec withRowStep(int step) {
		return new WaterSpec(layout, y, step, iceBlock, sourceEvery, sink);
	}

	public WaterSpec withIce(String block) {
		return new WaterSpec(layout, y, rowStep, block, sourceEvery, sink);
	}

	/** Drains at this block instead of the middle: the one you are looking at. */
	public WaterSpec drainingAt(int x, int z) {
		return new WaterSpec(layout, y, rowStep, iceBlock, sourceEvery, new Sink(x, z));
	}

	/** The layer the water itself occupies, which is what an item floats in. */
	public int waterY() {
		return y + 1;
	}
}
