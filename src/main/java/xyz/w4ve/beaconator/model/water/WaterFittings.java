package xyz.w4ve.beaconator.model.water;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where the water sources and the flow stops go.
 *
 * <p>The channel is one layer deep with nowhere to step down, so the current is not made by
 * gravity: it is made by a source every so many blocks, all pushing the same way, and stopped
 * where two of them would meet head on. This works out those positions.
 *
 * <p><b>Everything flows to the drain.</b> That is the one thing this has to get right and the
 * thing it used to get wrong: it followed each run from the end it was stored at, and a run is
 * stored in whatever order it was drawn or laid, so half of them pointed away from the drain. The
 * result was sources pushing the wrong way and plates dropped in the middle of a straight stretch
 * where nothing was meeting anything. Now it walks the tree of blocks rooted at the drain, from
 * every dead end down to the middle, so downstream means downstream everywhere.
 *
 * <p><b>The positions are a proposal, the direction is not.</b> How far a source really carries an
 * item on ice, and whether a junction built this way lets things through, is decided by the game
 * and nothing here has been dug yet. But a source that pushes away from the drain is wrong on
 * paper as well as in the world, and so is a plate on a chunk border: see {@link ChunkCrossings}
 * for why that one matters.
 *
 * @see WaterBudget for what they cost
 */
public final class WaterFittings {
	/**
	 * One fitting: where it goes and which way the water is going there.
	 *
	 * @param dx the step towards the drain, so a renderer can ask the world whether the water
	 *           already flowing here is going the same way
	 */
	public record Fitting(int x, int z, int dx, int dz) {
		public long key() {
			return ChunkCrossings.key(x, z);
		}
	}

	private final List<Fitting> sources;
	private final List<Fitting> stops;
	private final List<int[]> warnings;
	private final int crossings;
	private final Set<Long> wet;

	private WaterFittings(List<Fitting> sources, List<Fitting> stops, List<int[]> warnings,
			int crossings, Set<Long> wet) {
		this.sources = List.copyOf(sources);
		this.stops = List.copyOf(stops);
		this.warnings = List.copyOf(warnings);
		this.crossings = crossings;
		this.wet = Set.copyOf(wet);
	}

	/**
	 * The blocks that end up with moving water in them, which is every block of channel that does
	 * not end up with a plate on it.
	 */
	public Set<Long> wet() {
		return wet;
	}

	/**
	 * Works them out for a measured network.
	 *
	 * <p>A source goes at every dead end of the channel, then every {@code sourceEvery} blocks on
	 * the way down to the drain, and always on the last block before a chunk border, where the
	 * count starts again. That last one is what keeps the border wet: a source pushes seven blocks
	 * past itself, so the block on the far side is in moving water and an item crosses under power
	 * rather than coasting on momentum a chunk will not keep.
	 *
	 * <p>A plate goes one block upstream of every source, which is where the water the source
	 * before it is pushing runs out and the two currents meet, and at the last block of a branch
	 * before it joins another one. Never on either side of a border: one that lands there walks
	 * back upstream to the first free block.
	 */
	public static WaterFittings of(WaterNetwork network) {
		WaterSpec spec = network.spec();
		Map<Long, Long> downstream = network.downstream();
		Map<Long, Integer> distance = network.blockDistances();
		int every = Math.max(1, spec.sourceEvery());

		Set<Long> guarded = ChunkCrossings.guarded(network.segments());
		int crossings = ChunkCrossings.seams(network.segments()).size();

		List<Fitting> sources = new ArrayList<>();
		List<Fitting> stops = new ArrayList<>();
		List<int[]> warnings = new ArrayList<>();
		Set<Long> placed = new HashSet<>();
		Set<Long> plated = new HashSet<>();
		Set<Long> watered = new HashSet<>();
		List<Long> plateSpots = new ArrayList<>();
		Set<Long> walked = new HashSet<>();

		// Furthest first, so every walk starts at a dead end and every branch is followed whole
		// before the branch it joins. Going the other way round would start halfway down a line.
		List<Long> starts = new ArrayList<>(distance.keySet());
		starts.sort(Comparator.comparingInt(distance::get).reversed());

		for (long start : starts) {
			if (walked.contains(start)) {
				continue;
			}

			long at = start;
			int since = every;

			while (true) {
				Long next = downstream.get(at);
				boolean leaving = next != null && ChunkCrossings.separates(
						ChunkCrossings.x(at), ChunkCrossings.z(at),
						ChunkCrossings.x(next), ChunkCrossings.z(next));

				// Never on the drain itself. It is the end of the line: there is nowhere for a
				// source there to push an item except back the way it came.
				// Two sources may never touch. Put water next to water and you have one pool with
				// no current in it at all, which is the one way to build this that moves nothing:
				// there is always a plate between one source and the next.
				if ((since >= every || leaving) && next != null && !touchesPlate(at, watered)) {
					if (placed.add(at)) {
						sources.add(fitting(at, downstream));
						watered.add(at);
					}

					// The block behind this source is where its current meets the one before it.
					Long behind = upstream(at, start, downstream, distance);

					if (behind != null) {
						plateSpots.add(behind);
					}

					since = 1;
				} else {
					since++;
				}

				walked.add(at);

				// Where this branch runs into one that has already been walked, its current has to
				// give way rather than fight what is coming down the other line.
				if (next == null || walked.contains(next)) {
					if (next != null) {
						plateSpots.add(at);
					}

					break;
				}

				at = next;
			}
		}

		for (long spot : plateSpots) {
			addPlate(stops, warnings, placed, plated, guarded, spot, downstream, distance);
		}

		Set<Long> wet = fillTheGaps(network, sources, stops, placed, plated, watered, guarded,
				downstream);

		// Corners on a border are the other way an item ends up needing momentum it has not got,
		// and unlike a plate they cannot be nudged out of the way without moving the run.
		warnings.addAll(ChunkCrossings.cornersOnBorders(network.segments()));

		return new WaterFittings(sources, stops, warnings, crossings, wet);
	}

	/**
	 * Leaves no block of channel empty: every one ends up with water in it or a plate on it.
	 *
	 * <p>His rule, and it is the one an item cares about. A block with nothing in it is a block
	 * where whatever is going past is going past on momentum alone, and one bare block of ice in
	 * the middle of a working channel is the hardest thing in the whole network to find once the
	 * drops start going missing.
	 *
	 * <p>Water reaches seven blocks past its source and no further, whatever the spacing is set
	 * to, and a plate stops it dead. So this floods forward from every source, marks what is
	 * genuinely wet, and puts a plate on everything else. Widening the spacing does not thin the
	 * channel out any more: it trades buckets for plates.
	 */
	private static Set<Long> fillTheGaps(WaterNetwork network, List<Fitting> sources,
			List<Fitting> stops, Set<Long> placed, Set<Long> plated, Set<Long> watered,
			Set<Long> guarded, Map<Long, Long> downstream) {
		Set<Long> wet = new HashSet<>();

		for (Fitting source : sources) {
			long at = source.key();
			wet.add(at);

			for (int step = 0; step < FLOW_REACH; step++) {
				Long next = downstream.get(at);

				if (next == null || plated.contains(next)) {
					break;
				}

				at = next;
				wet.add(at);
			}
		}

		for (long block : network.blocks()) {
			if (wet.contains(block) || placed.contains(block)) {
				continue;
			}

			// On a chunk border the gap is filled with water, never with a plate: a plate there is
			// the thing an item cannot survive, and that rule beats this one. A source only fails
			// to go in when one is already touching, and then the block is wet anyway.
			if (guarded.contains(block) && !touchesPlate(block, watered)) {
				placed.add(block);
				watered.add(block);
				wet.add(block);
				sources.add(fitting(block, downstream));
				continue;
			}

			if (guarded.contains(block)) {
				continue;
			}

			placed.add(block);
			plated.add(block);
			stops.add(fitting(block, downstream));
		}

		return wet;
	}

	/** The block that flows into this one along the branch being walked, or null at a dead end. */
	private static Long upstream(long block, long start, Map<Long, Long> downstream,
			Map<Long, Integer> distance) {
		if (block == start) {
			return null;
		}

		Integer mine = distance.get(block);

		if (mine == null) {
			return null;
		}

		// The neighbour one step further from the drain that flows into here. There can be several
		// where branches meet; any of them is the block the current stops at.
		for (long neighbour : neighbours(block)) {
			Long theirs = downstream.get(neighbour);

			if (theirs != null && theirs == block) {
				return neighbour;
			}
		}

		return null;
	}

	/**
	 * Puts a plate at a block, or at the first block upstream of it that is free.
	 *
	 * <p>Free means two things: not on either side of a chunk border, which is what
	 * {@link ChunkCrossings} is about, and not on top of a source or another plate. A plate one
	 * block earlier cuts the same current, so walking back costs nothing.
	 */
	private static void addPlate(List<Fitting> stops, List<int[]> warnings, Set<Long> placed,
			Set<Long> plated, Set<Long> guarded, long spot, Map<Long, Long> downstream,
			Map<Long, Integer> distance) {
		long at = spot;

		for (int back = 0; back < 16; back++) {
			// Never next to another one either. Two branches ending a block apart used to ask for
			// a plate each and get them, which is how a channel ended up with three plates in a
			// row: past the first, they are cutting a current that has already been cut.
			if (!guarded.contains(at) && !placed.contains(at) && !touchesPlate(at, plated)) {
				placed.add(at);
				plated.add(at);
				stops.add(fitting(at, downstream));
				return;
			}

			Long behind = upstream(at, -1, downstream, distance);

			if (behind == null) {
				break;
			}

			at = behind;
		}

		if (guarded.contains(spot)) {
			warnings.add(new int[] {ChunkCrossings.x(spot), ChunkCrossings.z(spot)});
		}
	}

	/** How far water reaches past its source, whatever the spacing is set to. */
	private static final int FLOW_REACH = 7;

	/** True when one of the four blocks around this one is already in the given set. */
	private static boolean touchesPlate(long block, Set<Long> plated) {
		for (long neighbour : neighbours(block)) {
			if (plated.contains(neighbour)) {
				return true;
			}
		}

		return false;
	}

	private static Fitting fitting(long block, Map<Long, Long> downstream) {
		Long next = downstream.get(block);
		int x = ChunkCrossings.x(block);
		int z = ChunkCrossings.z(block);

		if (next == null) {
			return new Fitting(x, z, 0, 0);
		}

		return new Fitting(x, z, ChunkCrossings.x(next) - x, ChunkCrossings.z(next) - z);
	}

	private static long[] neighbours(long block) {
		int x = ChunkCrossings.x(block);
		int z = ChunkCrossings.z(block);
		return new long[] {
			ChunkCrossings.key(x + 1, z), ChunkCrossings.key(x - 1, z),
			ChunkCrossings.key(x, z + 1), ChunkCrossings.key(x, z - 1)
		};
	}

	/** Water source blocks, each knowing which way it pushes. */
	public List<Fitting> sources() {
		return sources;
	}

	/** Where a current has to be stopped so it does not fight the one it joins. */
	public List<Fitting> stops() {
		return stops;
	}

	/**
	 * Places worth walking to before the channel is finished: a corner sitting on a chunk border,
	 * or a plate with no room to move off one.
	 */
	public List<int[]> warnings() {
		return warnings;
	}

	/** How many times the channel steps from one chunk into the next. */
	public int crossings() {
		return crossings;
	}
}
