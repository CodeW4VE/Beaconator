package xyz.w4ve.beaconator.model.water;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where the water sources and the flow stops go.
 *
 * <p>The channel is one layer deep with nowhere to step down, so the current is not made by
 * gravity: it is made by a source every so many blocks, all pushing the same way, and stopped
 * where two of them would meet head on. This works out those positions from the runs.
 *
 * <p><b>These are a proposal, not a measurement.</b> How far a source actually carries an item on
 * ice, and whether a junction built this way lets things through, is decided by the game and by
 * how the thing is built, and nothing here has been dug yet. Treat every position as a guess to be
 * checked against the first stretch that gets built, which is why they are drawn as marks rather
 * than folded into the schematic.
 *
 * @see WaterBudget for what they cost
 */
public final class WaterFittings {
	private final List<int[]> sources;
	private final List<int[]> stops;

	private WaterFittings(List<int[]> sources, List<int[]> stops) {
		this.sources = List.copyOf(sources);
		this.stops = List.copyOf(stops);
	}

	/**
	 * Works them out for a network.
	 *
	 * <p>A source goes at the far end of each run, and then every {@code sourceEvery} blocks along
	 * it towards the drain. A stop goes at the end each run flows into, which is where its current
	 * has to give way to whatever it is joining.
	 */
	public static WaterFittings of(WaterPlan water) {
		List<int[]> sources = new ArrayList<>();
		List<int[]> stops = new ArrayList<>();
		Set<Long> taken = new HashSet<>();
		int every = Math.max(1, water.spec().sourceEvery());

		for (WaterSegment run : water.runs()) {
			int stepX = Integer.signum(run.x2() - run.x1());
			int stepZ = Integer.signum(run.z2() - run.z1());
			int length = run.length();

			// The order of a run's ends is the direction it flows, so this walks it downstream and
			// drops a source at the head and then at every step of the spacing.
			for (int index = 0; index < length; index += every) {
				int x = run.x1() + stepX * index;
				int z = run.z1() + stepZ * index;

				if (taken.add(key(x, z))) {
					sources.add(new int[] {x, z});
				}
			}

			stops.add(new int[] {run.x2(), run.z2()});
		}

		return new WaterFittings(sources, stops);
	}

	/** Water source blocks, as {@code {x, z}}. */
	public List<int[]> sources() {
		return sources;
	}

	/** Where a current has to be stopped so it does not fight the one it joins. */
	public List<int[]> stops() {
		return stops;
	}

	private static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}
}
