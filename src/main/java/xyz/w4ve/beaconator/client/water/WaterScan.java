package xyz.w4ve.beaconator.client.water;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import xyz.w4ve.beaconator.client.PlanManager;
import xyz.w4ve.beaconator.client.scan.WorldScanner;
import xyz.w4ve.beaconator.config.BeaconatorConfig;
import xyz.w4ve.beaconator.model.GridNode;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.model.water.WaterPlan;
import xyz.w4ve.beaconator.model.water.WaterSegment;

/**
 * Reads the channel back out of the world: what is still solid, what is dug, what is floored, and
 * what is already running.
 *
 * <p>Same rule as the pyramid scan and for the same reason: only loaded chunks can be read, so
 * everything else stays unknown rather than being reported as missing. A channel is ten thousand
 * blocks long and most of it is always out of range.
 *
 * <p>Kept per block rather than per run, because a run is eight hundred blocks and you dig it a
 * stretch at a time. That is what lets a finished stretch stop being drawn while the rest of the
 * same line stays lit.
 */
public final class WaterScan {
	private static final Map<Long, ChannelState> STATES = new HashMap<>();
	/** Nodes whose beacon has water sitting on it, which is the drop into the channel working. */
	private static final Set<NodeKey> FED = new HashSet<>();
	private static long lastScan;

	private WaterScan() {
	}

	public static ChannelState at(int x, int z) {
		return STATES.getOrDefault(key(x, z), ChannelState.UNKNOWN);
	}

	/** Whether this node's water is coming down onto its beacon yet. */
	public static boolean fed(NodeKey key) {
		return FED.contains(key);
	}

	public static void clear() {
		STATES.clear();
		FED.clear();
	}

	/** Blocks read so far, by state, for a progress line. Index by {@link ChannelState#ordinal()}. */
	public static int[] tally(PerimeterPlan plan) {
		int[] counts = new int[ChannelState.values().length];

		if (plan == null) {
			return counts;
		}

		Set<Long> seen = new HashSet<>();

		for (WaterSegment run : plan.water().runs()) {
			int stepX = Integer.signum(run.x2() - run.x1());
			int stepZ = Integer.signum(run.z2() - run.z1());
			int x = run.x1();
			int z = run.z1();

			while (true) {
				if (seen.add(key(x, z))) {
					counts[at(x, z).ordinal()]++;
				}

				if (x == run.x2() && z == run.z2()) {
					break;
				}

				x += stepX;
				z += stepZ;
			}
		}

		return counts;
	}

	/**
	 * Rereads the channel near the player.
	 *
	 * <p>Runs on the same clock as the pyramid scan and stays within the same radius, so digging a
	 * stretch turns it off in front of you without the mod walking ten thousand blocks a second.
	 */
	public static void tick(Minecraft mc) {
		PerimeterPlan plan = PlanManager.plan();
		BeaconatorConfig config = BeaconatorConfig.get();

		if (plan == null || mc.level == null || mc.player == null || !config.enabled
				|| !config.autoScan || !PlanManager.inPlanDimension()
				|| plan.water().isEmpty()) {
			return;
		}

		long now = System.currentTimeMillis();

		if (now - lastScan < config.autoScanIntervalMs) {
			return;
		}

		lastScan = now;
		scanNear(mc, plan, config.autoScanDistance);
	}

	/** Reads every block of channel within range of the player, and the beacons with it. */
	public static int scanNear(Minecraft mc, PerimeterPlan plan, int range) {
		WaterPlan water = plan.water();
		Level level = mc.level;

		if (level == null || mc.player == null) {
			return 0;
		}

		double limit = (double) range * range;
		double eyeX = mc.player.getX();
		double eyeZ = mc.player.getZ();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int floor = water.spec().y();
		int waterY = water.spec().waterY();
		int read = 0;

		for (WaterSegment run : water.runs()) {
			int stepX = Integer.signum(run.x2() - run.x1());
			int stepZ = Integer.signum(run.z2() - run.z1());
			int x = run.x1();
			int z = run.z1();

			while (true) {
				double dx = eyeX - x;
				double dz = eyeZ - z;

				if (dx * dx + dz * dz <= limit && level.hasChunk(x >> 4, z >> 4)) {
					STATES.put(key(x, z), read(level, cursor, x, floor, waterY, z, water));
					read++;
				}

				if (x == run.x2() && z == run.z2()) {
					break;
				}

				x += stepX;
				z += stepZ;
			}
		}

		readBeacons(level, cursor, plan, eyeX, eyeZ, limit);
		return read;
	}

	/**
	 * One block of channel.
	 *
	 * <p>Water first: a stretch that is running is finished whatever is under it, and that is the
	 * one state that takes the line off the screen. Then air, which separates a dug tunnel from
	 * solid rock, and then the floor, which separates a tunnel from a tunnel that is ready.
	 */
	private static ChannelState read(Level level, BlockPos.MutableBlockPos cursor,
			int x, int floor, int waterY, int z, WaterPlan water) {
		cursor.set(x, waterY, z);
		BlockState above = level.getBlockState(cursor);

		if (!above.getFluidState().isEmpty()) {
			return ChannelState.FLOWING;
		}

		if (!above.isAir()) {
			return ChannelState.SOLID;
		}

		cursor.set(x, floor, z);
		BlockState below = level.getBlockState(cursor);
		var ice = WorldScanner.block(water.spec().iceBlock());

		// Any ice counts as floored. Somebody who ran out of packed ice and finished a stretch in
		// blue ice has built the thing, and saying otherwise would be pedantry with a red line.
		if (ice != null && (below.is(ice) || below.is(net.minecraft.tags.BlockTags.ICE))) {
			return ChannelState.FLOORED;
		}

		return ChannelState.OPEN;
	}

	/**
	 * Which nodes have water on the beacon.
	 *
	 * <p>That block is where a shulker gets picked up: the water comes over the beacon, runs down
	 * the side of the pyramid and lands in the channel. It is also the one part of the drop that
	 * can be checked without knowing anything about how somebody chose to build it.
	 */
	private static void readBeacons(Level level, BlockPos.MutableBlockPos cursor, PerimeterPlan plan,
			double eyeX, double eyeZ, double limit) {
		for (GridNode node : plan.buildNodes()) {
			double dx = eyeX - node.x();
			double dz = eyeZ - node.z();

			if (dx * dx + dz * dz > limit) {
				continue;
			}

			boolean fed = false;

			for (int[] beacon : plan.beaconPositionsOf(node)) {
				if (!level.hasChunk(beacon[0] >> 4, beacon[2] >> 4)) {
					continue;
				}

				cursor.set(beacon[0], beacon[1] + 1, beacon[2]);

				if (!level.getBlockState(cursor).getFluidState().isEmpty()) {
					fed = true;
					break;
				}
			}

			if (fed) {
				FED.add(node.key());
			} else {
				FED.remove(node.key());
			}
		}
	}

	private static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}
}
