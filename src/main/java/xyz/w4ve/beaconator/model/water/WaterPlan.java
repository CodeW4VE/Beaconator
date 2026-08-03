package xyz.w4ve.beaconator.model.water;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import xyz.w4ve.beaconator.model.PerimeterPlan;

/**
 * The water network as a document: the runs that are actually going to be dug, kept next to the
 * plan and saved with it.
 *
 * <p>{@link WaterNetwork} works a network out from the plan, which is where a network starts. This
 * is what it becomes once it is yours: generating fills this in, drawing on the map edits it, and
 * what is in here is what gets counted, drawn and built. There is no layer of overrides on top of
 * a calculation, because a run you drew and a run the mod worked out are the same kind of thing and
 * arguing about which one wins is how a tool ends up lying to you.
 *
 * <p>The consequence, designed for on purpose: <b>generating throws away what you drew</b>. So
 * generating is a button you press deliberately and never something that happens quietly when a
 * node changes.
 */
public final class WaterPlan {
	private WaterSpec spec = WaterSpec.defaults();
	private final List<WaterSegment> runs = new ArrayList<>();
	private boolean edited;
	private int revision;

	public WaterSpec spec() {
		return spec;
	}

	public void setSpec(WaterSpec spec) {
		this.spec = spec;
		revision++;
	}

	/**
	 * Bumped by every change. Measuring a network means flooding it, which is not something to do
	 * once a frame, so whoever caches the result watches this to know when the cache is stale.
	 */
	public int revision() {
		return revision;
	}

	/** The runs, in the order they were laid. */
	public List<WaterSegment> runs() {
		return Collections.unmodifiableList(runs);
	}

	public boolean isEmpty() {
		return runs.isEmpty();
	}

	/** True once a run has been drawn or erased by hand, so regenerating knows what it would lose. */
	public boolean edited() {
		return edited;
	}

	public void clear() {
		runs.clear();
		edited = false;
		revision++;
	}

	/** Adds a run. Zero length runs are dropped: they are a click that did not turn into a drag. */
	public void add(WaterSegment run) {
		if (run == null) {
			return;
		}

		runs.add(run);
		edited = true;
		revision++;
	}

	/** The run passing through this block, latest first, or null. */
	public WaterSegment runAt(int x, int z) {
		for (int index = runs.size() - 1; index >= 0; index--) {
			if (covers(runs.get(index), x, z)) {
				return runs.get(index);
			}
		}

		return null;
	}

	/** Takes the last run back off, which is what undo means while you are drawing. */
	public WaterSegment removeLast() {
		if (runs.isEmpty()) {
			return null;
		}

		WaterSegment run = runs.remove(runs.size() - 1);
		edited = true;
		revision++;
		return run;
	}

	/**
	 * Erases the stretch of channel under a block, up to the nearest junction on each side.
	 *
	 * <p>Not the whole run. A generated spine is eight hundred blocks long and crosses the trunk
	 * and every other line on its way in, so taking the whole thing out because you wanted rid of
	 * one stretch is nobody's idea of an eraser. What gets removed is the piece between the two
	 * places this run meets another one, and the junctions themselves stay: they belong to the
	 * lines that are still there.
	 *
	 * <p>A run that meets nothing is removed whole, which is what you want for something you just
	 * drew in the wrong place.
	 *
	 * @return true when there was a run under the block
	 */
	public boolean removeAt(int x, int z) {
		WaterSegment run = runAt(x, z);

		if (run == null) {
			return false;
		}

		List<int[]> blocks = blocksOf(run);
		int at = indexOf(blocks, x, z);
		Set<Long> elsewhere = blocksOfEveryRunBut(run);

		// The nearest block of this run that another line also uses, in each direction.
		int before = -1;
		int after = blocks.size();

		for (int index = at - 1; index >= 0; index--) {
			if (elsewhere.contains(key(blocks.get(index)))) {
				before = index;
				break;
			}
		}

		for (int index = at + 1; index < blocks.size(); index++) {
			if (elsewhere.contains(key(blocks.get(index)))) {
				after = index;
				break;
			}
		}

		int position = runs.indexOf(run);
		runs.remove(position);

		// What survives on each side, junction block included so the rest stays joined up.
		if (after < blocks.size()) {
			runs.add(position, segment(blocks.get(after), blocks.get(blocks.size() - 1), run.kind()));
		}

		if (before >= 0) {
			runs.add(position, segment(blocks.get(0), blocks.get(before), run.kind()));
		}

		edited = true;
		revision++;
		return true;
	}

	/** The blocks of a run, from one end to the other. */
	private static List<int[]> blocksOf(WaterSegment run) {
		List<int[]> blocks = new ArrayList<>();
		int stepX = Integer.signum(run.x2() - run.x1());
		int stepZ = Integer.signum(run.z2() - run.z1());
		int x = run.x1();
		int z = run.z1();
		blocks.add(new int[] {x, z});

		while (x != run.x2() || z != run.z2()) {
			x += stepX;
			z += stepZ;
			blocks.add(new int[] {x, z});
		}

		return blocks;
	}

	private static int indexOf(List<int[]> blocks, int x, int z) {
		for (int index = 0; index < blocks.size(); index++) {
			if (blocks.get(index)[0] == x && blocks.get(index)[1] == z) {
				return index;
			}
		}

		return 0;
	}

	private Set<Long> blocksOfEveryRunBut(WaterSegment run) {
		Set<Long> blocks = new HashSet<>();

		for (WaterSegment other : runs) {
			if (other == run) {
				continue;
			}

			for (int[] block : blocksOf(other)) {
				blocks.add(key(block));
			}
		}

		return blocks;
	}

	private static WaterSegment segment(int[] from, int[] to, WaterSegment.Kind kind) {
		return new WaterSegment(from[0], from[1], to[0], to[1], kind);
	}

	private static long key(int[] block) {
		return ((long) block[0] << 32) | (block[1] & 0xFFFFFFFFL);
	}

	/**
	 * Replaces every run with a freshly worked out network.
	 *
	 * <p>Everything drawn by hand is gone afterwards. That is the deal: what is in here is what
	 * gets built, so there is nowhere for a hand drawn run to hide and come back later.
	 */
	public void generate(PerimeterPlan plan) {
		runs.clear();
		runs.addAll(WaterNetwork.of(plan, spec).segments());
		edited = false;
		revision++;
	}

	/** For loading: runs read back off disk were not drawn in this session. */
	public void clearEditedFlag() {
		edited = false;
	}

	/** Costs and measures what is actually in here, drawn runs and all. */
	public WaterNetwork network(PerimeterPlan plan) {
		return WaterNetwork.over(plan, spec, runs);
	}

	private static boolean covers(WaterSegment run, int x, int z) {
		return x >= Math.min(run.x1(), run.x2()) && x <= Math.max(run.x1(), run.x2())
				&& z >= Math.min(run.z1(), run.z2()) && z <= Math.max(run.z1(), run.z2());
	}

	/** A copy, for putting a plan on the wire or holding one aside. */
	public WaterPlan copy() {
		WaterPlan copy = new WaterPlan();
		copy.spec = spec;
		copy.runs.addAll(runs);
		copy.edited = edited;
		return copy;
	}
}
