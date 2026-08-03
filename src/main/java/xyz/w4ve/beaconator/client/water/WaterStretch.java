package xyz.w4ve.beaconator.client.water;

import java.util.ArrayList;
import java.util.List;
import xyz.w4ve.beaconator.model.water.WaterSegment;

/**
 * A run cut into pieces that are all in the same state.
 *
 * <p>What a run is planned as and what it looks like halfway through digging are two different
 * shapes: one straight line on the plan, and on the ground fifty blocks running, two hundred dug
 * and the rest still solid. This is what turns the first into the second so the drawing can show
 * the work rather than the intention.
 *
 * <p>Cut lazily, once per draw, and never stored: the states come from a scan that changes as you
 * dig, and a cached stretch would be a stale one within the minute.
 */
public record WaterStretch(int x1, int z1, int x2, int z2, ChannelState state) {
	/** The pieces of a run, in order, each one all of a state. */
	public static List<WaterStretch> of(WaterSegment run) {
		List<WaterStretch> stretches = new ArrayList<>();
		int stepX = Integer.signum(run.x2() - run.x1());
		int stepZ = Integer.signum(run.z2() - run.z1());
		int x = run.x1();
		int z = run.z1();

		int startX = x;
		int startZ = z;
		ChannelState state = WaterScan.at(x, z);

		while (x != run.x2() || z != run.z2()) {
			int nextX = x + stepX;
			int nextZ = z + stepZ;
			ChannelState next = WaterScan.at(nextX, nextZ);

			// Unknown reads as whatever the stretch was already in. Out of range is not a change in
			// the world, and letting it break the line up would flicker a run into confetti every
			// time you turned around.
			if (next != state && next != ChannelState.UNKNOWN
					&& !(state == ChannelState.UNKNOWN && next == ChannelState.SOLID)) {
				stretches.add(new WaterStretch(startX, startZ, x, z, state));
				startX = nextX;
				startZ = nextZ;
				state = next;
			}

			x = nextX;
			z = nextZ;
		}

		stretches.add(new WaterStretch(startX, startZ, x, z, state));
		return stretches;
	}
}
