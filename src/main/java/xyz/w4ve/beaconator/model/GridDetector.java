package xyz.w4ve.beaconator.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Rebuilds a plan from the beacons of an existing schematic.
 *
 * <p>This is how an already built perimeter comes back into the mod: read where the beacons
 * are, work out the spacing, how many sit on each node and which way the rows run, then mark
 * the ones carrying a marker block as excluded and the missing grid slots as removed.
 */
public final class GridDetector {
	private GridDetector() {
	}

	/**
	 * Positions read out of a schematic.
	 *
	 * @param beacons      every beacon position, as {@code [x, y, z]}
	 * @param markers      every marker block position, as {@code [x, y, z]}
	 * @param pyramidBlock block id the pyramids are made of
	 * @param markerBlock  block id used to mark excluded nodes, or null if there are none
	 * @param level        pyramid layers found under the beacons
	 */
	public record Input(List<int[]> beacons, List<int[]> markers, String pyramidBlock,
			String markerBlock, int level) {
	}

	public static PerimeterPlan detect(String name, String dimension, Input input) {
		if (input.beacons().isEmpty()) {
			throw new IllegalArgumentException("No beacons in the schematic, nothing to detect");
		}

		int beaconY = mostCommonY(input.beacons());
		Set<Long> positions = new HashSet<>();

		for (int[] beacon : input.beacons()) {
			if (beacon[1] == beaconY) {
				positions.add(pack(beacon[0], beacon[2]));
			}
		}

		// Beacons of one node touch each other, so a flood fill finds the nodes without having to
		// assume anything about their shape. It used to walk rows, which read a 2x3 group as two
		// separate three beacon nodes.
		List<List<int[]>> groups = groups(positions);
		int beaconsPerNode = mostCommonSize(groups);
		RowAxis axis = detectAxis(groups);
		List<int[]> origins = new ArrayList<>();

		for (List<int[]> group : groups) {
			int minX = Integer.MAX_VALUE;
			int minZ = Integer.MAX_VALUE;

			for (int[] cell : group) {
				minX = Math.min(minX, cell[0]);
				minZ = Math.min(minZ, cell[1]);
			}

			origins.add(new int[] {minX, minZ});
		}

		origins.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));

		int spacing = detectSpacing(origins);
		int[] centre = centreOf(origins, spacing);

		PerimeterPlan plan = new PerimeterPlan(name, dimension, centre[0], beaconY, centre[1]);
		plan.setLevel(Math.clamp(input.level(), 1, PyramidCalculator.MAX_LEVEL));
		plan.setSpacing(spacing);
		plan.setBeaconsPerNode(Math.clamp(beaconsPerNode, 1, PyramidCalculator.MAX_BEACONS_PER_NODE));
		plan.setRowAxis(axis);

		if (input.pyramidBlock() != null) {
			plan.setPyramidBlock(input.pyramidBlock());
		}

		if (input.markerBlock() != null) {
			plan.setMarkerBlock(input.markerBlock());
		}

		// Grid indices of every node that actually exists in the schematic.
		Set<Long> present = new HashSet<>();
		int minI = 0;
		int maxI = 0;
		int minJ = 0;
		int maxJ = 0;

		for (int[] origin : origins) {
			int i = Math.floorDiv(origin[0] - centre[0], spacing);
			int j = Math.floorDiv(origin[1] - centre[1], spacing);
			present.add(pack(i, j));
			minI = Math.min(minI, i);
			maxI = Math.max(maxI, i);
			minJ = Math.min(minJ, j);
			maxJ = Math.max(maxJ, j);
		}

		plan.setExtents(new GridExtents(minI, maxI, minJ, maxJ));

		Set<Long> excluded = new HashSet<>();

		for (int[] marker : input.markers()) {
			if (marker[1] != beaconY + 1) {
				continue;
			}

			int i = Math.floorDiv(marker[0] - centre[0], spacing);
			int j = Math.floorDiv(marker[2] - centre[1], spacing);
			excluded.add(pack(i, j));
		}

		for (int j = minJ; j <= maxJ; j++) {
			for (int i = minI; i <= maxI; i++) {
				long key = pack(i, j);

				if (!present.contains(key)) {
					plan.setStatus(new NodeKey(i, j), NodeStatus.REMOVED);
				} else if (excluded.contains(key)) {
					plan.setStatus(new NodeKey(i, j), NodeStatus.EXCLUDED);
				}
			}
		}

		plan.setPlaceMarker(!excluded.isEmpty() || input.markerBlock() != null);
		return plan;
	}

	// ----------------------------------------------------------------- helpers

	private static int mostCommonY(List<int[]> beacons) {
		java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();

		for (int[] beacon : beacons) {
			counts.merge(beacon[1], 1, Integer::sum);
		}

		return counts.entrySet().stream()
				.max(java.util.Map.Entry.comparingByValue())
				.map(java.util.Map.Entry::getKey)
				.orElse(beacons.get(0)[1]);
	}

	/** Connected groups of beacons, four way. One group is one node, whatever shape it is. */
	private static List<List<int[]>> groups(Set<Long> positions) {
		Set<Long> left = new HashSet<>(positions);
		List<List<int[]>> groups = new ArrayList<>();

		while (!left.isEmpty()) {
			long seed = left.iterator().next();
			List<int[]> group = new ArrayList<>();
			Deque<Long> queue = new ArrayDeque<>();
			queue.add(seed);
			left.remove(seed);

			while (!queue.isEmpty()) {
				long packed = queue.poll();
				int x = unpackFirst(packed);
				int z = unpackSecond(packed);
				group.add(new int[] {x, z});

				for (long neighbour : new long[] {pack(x + 1, z), pack(x - 1, z), pack(x, z + 1), pack(x, z - 1)}) {
					if (left.remove(neighbour)) {
						queue.add(neighbour);
					}
				}
			}

			groups.add(group);
		}

		return groups;
	}

	/** The group size most nodes have, so one half built node does not set the count. */
	private static int mostCommonSize(List<List<int[]>> groups) {
		Map<Integer, Integer> counts = new HashMap<>();

		for (List<int[]> group : groups) {
			counts.merge(group.size(), 1, Integer::sum);
		}

		return counts.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(1);
	}

	/** Which way the groups are longer. A square footprint has no axis, so Z is as good as X. */
	private static RowAxis detectAxis(List<List<int[]>> groups) {
		int alongX = 0;
		int alongZ = 0;

		for (List<int[]> group : groups) {
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxZ = Integer.MIN_VALUE;

			for (int[] cell : group) {
				minX = Math.min(minX, cell[0]);
				maxX = Math.max(maxX, cell[0]);
				minZ = Math.min(minZ, cell[1]);
				maxZ = Math.max(maxZ, cell[1]);
			}

			if (maxX - minX > maxZ - minZ) {
				alongX++;
			} else if (maxZ - minZ > maxX - minX) {
				alongZ++;
			}
		}

		return alongX > alongZ ? RowAxis.X : RowAxis.Z;
	}

	private static int detectSpacing(List<int[]> origins) {
		int spacing = 0;
		spacing = gcdOfDeltas(origins, 0, spacing);
		spacing = gcdOfDeltas(origins, 1, spacing);
		return spacing <= 0 ? 1 : spacing;
	}

	private static int gcdOfDeltas(List<int[]> origins, int component, int current) {
		List<Integer> values = origins.stream().map(o -> o[component]).distinct().sorted().toList();

		for (int index = 1; index < values.size(); index++) {
			current = gcd(current, values.get(index) - values.get(index - 1));
		}

		return current;
	}

	private static int gcd(int a, int b) {
		int x = Math.abs(a);
		int y = Math.abs(b);

		while (y != 0) {
			int temp = y;
			y = x % y;
			x = temp;
		}

		return x;
	}

	/** Picks the node closest to the middle as the centre, so the grid indices stay balanced. */
	private static int[] centreOf(List<int[]> origins, int spacing) {
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;

		for (int[] origin : origins) {
			minX = Math.min(minX, origin[0]);
			maxX = Math.max(maxX, origin[0]);
			minZ = Math.min(minZ, origin[1]);
			maxZ = Math.max(maxZ, origin[1]);
		}

		int columns = (maxX - minX) / spacing;
		int rows = (maxZ - minZ) / spacing;
		return new int[] {minX + columns / 2 * spacing, minZ + rows / 2 * spacing};
	}

	private static long pack(int a, int b) {
		return ((long) a << 32) | (b & 0xFFFFFFFFL);
	}

	private static int unpackFirst(long packed) {
		return (int) (packed >> 32);
	}

	private static int unpackSecond(long packed) {
		return (int) packed;
	}
}
