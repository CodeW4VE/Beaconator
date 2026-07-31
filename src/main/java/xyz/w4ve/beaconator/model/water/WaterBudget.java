package xyz.w4ve.beaconator.model.water;

import xyz.w4ve.beaconator.model.MaterialTally;

/**
 * What a water network costs.
 *
 * <p>This is the number the whole feature turns on. A channel block is cheap on its own and there
 * are tens of thousands of them, and the ice is not mined as ice: packed ice is nine, blue ice is
 * eighty one, so the pile that has to be gathered is an order of magnitude bigger than the length
 * of the network. Better to find that out here than a week into digging.
 *
 * @param channelBlocks blocks of channel, counted once where runs cross
 * @param iceBlocks     ice under the water, one per channel block
 * @param waterSources  buckets, one every {@link WaterSpec#sourceEvery()} blocks of each run
 * @param flowStops     places the current has to be cut so it does not run backwards
 * @param junctions     places three or more runs meet
 * @param blocksToDig   the tunnel itself, two blocks tall along the whole channel
 * @param trip          how long items take to get in, which is the other thing worth optimising
 * @param nodesServed   nodes with a line
 * @param nodesOrphaned nodes left without one, which is what partial coverage costs
 * @param blockedRuns   runs that would go through a pyramid base, which must always be zero
 */
public record WaterBudget(WaterSpec spec, int channelBlocks, int iceBlocks, int waterSources,
		int flowStops, int junctions, int blocksToDig, Trip trip, int nodesServed,
		int nodesOrphaned, int blockedRuns) {
	private static final int STACK = 64;
	private static final int SHULKER = 27 * STACK;

	/**
	 * What the trips look like, in blocks of channel.
	 *
	 * @param longest      the worst trip in the network
	 * @param average      the mean trip, which is what most items actually do
	 * @param maxTurns     corners on the worst way in, each one a place an item has to be pushed again
	 * @param idealLongest the worst trip if channels went straight through the pyramids, which
	 *                     nothing rectilinear can beat
	 */
	public record Trip(int longest, int average, int maxTurns, int idealLongest) {
		/** Blocks the worst trip loses to the shape of the network. Zero is as good as it gets. */
		public int overhead() {
			return longest - idealLongest;
		}
	}

	/** The worst trip from a node to the middle, along the channel. */
	public int longestRun() {
		return trip.longest();
	}

	/** Ice blocks that have to be mined to craft the ice that goes in: nine each, or eighty one. */
	public int iceToMine() {
		return switch (spec.iceBlock()) {
			case WaterSpec.PACKED_ICE -> iceBlocks * 9;
			case WaterSpec.BLUE_ICE -> iceBlocks * 81;
			default -> iceBlocks;
		};
	}

	public MaterialTally tally() {
		MaterialTally tally = new MaterialTally();
		tally.add(spec.iceBlock(), iceBlocks);
		tally.add("minecraft:water_bucket", waterSources);
		tally.add("minecraft:stone_pressure_plate", flowStops);
		return tally;
	}

	public static int stacks(int blocks) {
		return Math.ceilDiv(blocks, STACK);
	}

	public static int shulkers(int blocks) {
		return Math.ceilDiv(blocks, SHULKER);
	}

	/** One line for a status bar or a log, in the units a builder actually thinks in. */
	public String summary() {
		return channelBlocks + " channel, " + iceBlocks + " " + shortName(spec.iceBlock())
				+ " (" + shulkers(iceBlocks) + " shulkers), longest run " + trip.longest();
	}

	private static String shortName(String blockId) {
		int colon = blockId.indexOf(':');
		return colon < 0 ? blockId : blockId.substring(colon + 1);
	}
}
