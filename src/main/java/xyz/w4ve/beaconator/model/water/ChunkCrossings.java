package xyz.w4ve.beaconator.model.water;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where the channel steps out of one chunk and into the next, which is the one place along a run
 * where being clever about water costs you items.
 *
 * <p>An item in flowing water is pushed again every tick, so it does not care what happened to it a
 * second ago. An item sliding along bare ice is carrying momentum, and momentum is the thing a
 * chunk does not keep: unload the chunk it is drifting through and reload it, and the item comes
 * back sitting still on a block with nothing to push it. There it stays. On a perimeter, where
 * nobody is standing over the channel for the hours it takes a shulker of gravel to make the trip,
 * the far end of the network is unloaded most of the time and the crossings are exactly where that
 * happens.
 *
 * <p>So the rule this class exists to enforce: <b>the two blocks either side of a chunk border
 * carry moving water and nothing else</b>. No plate cutting the current there, no gap between
 * sources for an item to coast through. A block of channel is cheap and a bucket is cheaper than
 * walking eight hundred blocks to find out which crossing swallowed the drop.
 *
 * <p>Two ways of finding them, because they answer different questions. {@link #seams} looks at
 * the channel as blocks and finds every border it touches, whoever laid them, which is what says
 * where a plate may not go. {@link #stepsAcross} follows each run in the direction it flows, which
 * is what says which block gets the source that pushes across. A border between two different runs
 * shows up in the first and not the second, and that is not an oversight: nothing here knows which
 * way water goes between two runs that merely touch, so it gets reported rather than guessed at.
 *
 * @see WaterFittings which places the sources and plates around what this works out
 */
public final class ChunkCrossings {
	/**
	 * One step across a border, in the direction the water flows.
	 *
	 * @param outX the last block of the chunk being left
	 * @param inX  the first block of the chunk being entered, always next to the other one
	 */
	public record Crossing(int outX, int outZ, int inX, int inZ) {
	}

	/**
	 * One border, however many runs step across it: the block on the low side and which way its
	 * neighbour lies. A pair of blocks, not a piece of a run, so two runs sharing a junction do not
	 * report the same border twice.
	 */
	public record Seam(int x, int z, boolean alongX) {
		/** The block on the far side. */
		public int otherX() {
			return alongX ? x + 1 : x;
		}

		public int otherZ() {
			return alongX ? z : z + 1;
		}
	}

	private ChunkCrossings() {
	}

	/**
	 * Every border the channel touches, found by looking at which blocks sit next to each other
	 * rather than at which run they came from.
	 */
	public static List<Seam> seams(Collection<WaterSegment> runs) {
		Set<Long> channel = rasterise(runs);
		List<Seam> seams = new ArrayList<>();

		for (long block : channel) {
			int x = (int) (block >> 32);
			int z = (int) block;

			// Only the low side of each pair is asked, so a border is found once.
			if (channel.contains(key(x + 1, z)) && separates(x, z, x + 1, z)) {
				seams.add(new Seam(x, z, true));
			}

			if (channel.contains(key(x, z + 1)) && separates(x, z, x, z + 1)) {
				seams.add(new Seam(x, z, false));
			}
		}

		return seams;
	}

	/** The border steps of each run, in the order the water flows through them. */
	public static List<Crossing> stepsAcross(Collection<WaterSegment> runs) {
		List<Crossing> crossings = new ArrayList<>();
		Set<Seam> seen = new HashSet<>();

		for (WaterSegment run : runs) {
			int stepX = Integer.signum(run.x2() - run.x1());
			int stepZ = Integer.signum(run.z2() - run.z1());

			for (int index = 0; index < run.length() - 1; index++) {
				int x = run.x1() + stepX * index;
				int z = run.z1() + stepZ * index;

				if (!separates(x, z, x + stepX, z + stepZ)) {
					continue;
				}

				Seam seam = new Seam(Math.min(x, x + stepX), Math.min(z, z + stepZ), stepX != 0);

				if (seen.add(seam)) {
					crossings.add(new Crossing(x, z, x + stepX, z + stepZ));
				}
			}
		}

		return crossings;
	}

	/**
	 * The blocks nothing may be built on but channel: both sides of every border the channel
	 * touches.
	 *
	 * <p>Packed as {@code x} and {@code z} into a long, which is how the rest of the water code
	 * keeps sets of blocks.
	 */
	public static Set<Long> guarded(Collection<WaterSegment> runs) {
		Set<Long> guarded = new HashSet<>();

		for (Seam seam : seams(runs)) {
			guarded.add(key(seam.x(), seam.z()));
			guarded.add(key(seam.otherX(), seam.otherZ()));
		}

		return guarded;
	}

	/**
	 * Corners that sit on a border, which are worth knowing about even though nothing can be done
	 * about them automatically.
	 *
	 * <p>A corner is where a run ends and the next one takes over, and an item has to be pushed
	 * around it rather than through it. One that lands on a border is the same trap as a plate
	 * there, except moving it means moving the run, which changes every trip that uses it. So this
	 * reports and does not fix: the answer is usually to nudge the drain or the spacing, and that
	 * is a decision about the whole network. It also catches the case nothing else can, two runs
	 * that meet across a border without either one stepping over it.
	 *
	 * @return the corner blocks, as {@code {x, z}}
	 */
	public static List<int[]> cornersOnBorders(Collection<WaterSegment> runs) {
		Set<Long> guarded = guarded(runs);
		List<int[]> corners = new ArrayList<>();
		Set<Long> seen = new HashSet<>();

		for (WaterSegment run : runs) {
			addCorner(corners, seen, guarded, run.x1(), run.z1());
			addCorner(corners, seen, guarded, run.x2(), run.z2());
		}

		return corners;
	}

	private static void addCorner(List<int[]> corners, Set<Long> seen, Set<Long> guarded, int x,
			int z) {
		if (guarded.contains(key(x, z)) && seen.add(key(x, z))) {
			corners.add(new int[] {x, z});
		}
	}

	private static Set<Long> rasterise(Collection<WaterSegment> runs) {
		Set<Long> blocks = new HashSet<>();

		for (WaterSegment run : runs) {
			int stepX = Integer.signum(run.x2() - run.x1());
			int stepZ = Integer.signum(run.z2() - run.z1());

			for (int index = 0; index < run.length(); index++) {
				blocks.add(key(run.x1() + stepX * index, run.z1() + stepZ * index));
			}
		}

		return blocks;
	}

	/** True when two neighbouring blocks belong to different chunks. */
	public static boolean separates(int x1, int z1, int x2, int z2) {
		return (x1 >> 4) != (x2 >> 4) || (z1 >> 4) != (z2 >> 4);
	}

	public static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	public static int x(long key) {
		return (int) (key >> 32);
	}

	public static int z(long key) {
		return (int) key;
	}
}
