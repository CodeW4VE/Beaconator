package xyz.w4ve.beaconator.model.water;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import xyz.w4ve.beaconator.model.GridNode;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.model.PyramidCalculator;

/**
 * The network of water channels that carries what you throw in at a beacon to the middle.
 *
 * <p>Worked out from the plan alone: the nodes are already on a perfect grid, so the network is
 * geometry rather than pathfinding. Two things shape it.
 *
 * <p>First, <b>the channel cannot go under the pyramids</b>. The beacons sit as deep as they can,
 * their base already occupies the bottom diggable layer, and below that is bedrock. So the channel
 * runs on that same layer and goes <i>around</i> the bases, one block clear of them. That costs
 * nothing: an item riding the water down the side of a pyramid lands at its foot, which is exactly
 * where the channel passes.
 *
 * <p>Second, <b>every run is one way</b>. There is no room to step down towards the middle, so the
 * current is made by sources placed along each run, and two runs meeting head on is a junction that
 * has to be built deliberately. Both are counted rather than drawn: see {@link WaterBudget}.
 */
public final class WaterNetwork {
	private final PerimeterPlan plan;
	private final WaterSpec spec;
	private final List<WaterSegment> segments;
	private final Map<NodeKey, Long> entries;
	private final List<NodeKey> orphans;
	private final long sink;
	private final Set<Long> blocks;
	private final List<WaterSegment> blocked;

	private WaterNetwork(PerimeterPlan plan, WaterSpec spec, List<WaterSegment> segments,
			Map<NodeKey, Long> entries, List<NodeKey> orphans, long sink) {
		this.plan = plan;
		this.spec = spec;
		this.segments = List.copyOf(segments);
		this.entries = Map.copyOf(entries);
		this.orphans = List.copyOf(orphans);
		this.sink = sink;
		this.blocks = rasterise(segments);
		this.blocked = crossings(plan, segments);
	}

	public static WaterNetwork of(PerimeterPlan plan, WaterSpec spec) {
		return spec.layout() == WaterLayout.TREE ? tree(plan, spec) : fishbone(plan, spec);
	}

	// ----------------------------------------------------------------- shapes

	/**
	 * One spine per row, one trunk to the middle.
	 *
	 * <p>A spine runs one block clear of the row's pyramid bases, so it passes the foot of every
	 * node in that row without a stub of its own. Each spine is two runs, one from each side,
	 * because the water on both ends has to move towards the trunk.
	 */
	private static WaterNetwork fishbone(PerimeterPlan plan, WaterSpec spec) {
		List<GridNode> live = plan.buildNodes();

		if (live.isEmpty()) {
			return empty(plan, spec);
		}

		Map<Integer, List<GridNode>> rows = new TreeMap<>();

		for (GridNode node : live) {
			rows.computeIfAbsent(node.j(), key -> new ArrayList<>()).add(node);
		}

		long drain = drain(plan, spec);
		int trunkX = trunkX(plan, live, x(drain));
		List<WaterSegment> segments = new ArrayList<>();
		Map<NodeKey, Long> entries = new LinkedHashMap<>();
		List<NodeKey> orphans = new ArrayList<>();
		List<Integer> spineZ = new ArrayList<>();

		for (Map.Entry<Integer, List<GridNode>> row : rows.entrySet()) {
			List<GridNode> nodes = row.getValue();

			// Skipping rows is how partial coverage gets costed. The centre row is always in, so
			// the step reads the same from either side of the grid.
			if (Math.floorMod(row.getKey(), spec.rowStep()) != 0) {
				for (GridNode node : nodes) {
					orphans.add(node.key());
				}

				continue;
			}

			Integer spine = null;
			Integer westEnd = null;
			Integer eastEnd = null;

			for (GridNode node : nodes) {
				int[] base = base(plan, node);
				// Past the deepest base of the row, so one lane clears every node in it even when
				// a node was given more beacons than the rest.
				spine = spine == null ? base[3] + 1 : Math.max(spine, base[3] + 1);

				if (base[1] < trunkX) {
					westEnd = westEnd == null ? base[0] : Math.min(westEnd, base[0]);
				} else if (base[0] > trunkX) {
					eastEnd = eastEnd == null ? base[1] : Math.max(eastEnd, base[1]);
				} else {
					// The node the trunk runs past. Either run reaches it, so it needs no end of
					// its own, but the row still needs a spine even if it is the only node there.
					westEnd = westEnd == null ? base[0] : Math.min(westEnd, base[0]);
				}
			}

			if (westEnd != null) {
				segments.add(new WaterSegment(westEnd, spine, trunkX, spine, WaterSegment.Kind.SPINE));
			}

			if (eastEnd != null) {
				segments.add(new WaterSegment(eastEnd, spine, trunkX, spine, WaterSegment.Kind.SPINE));
			}

			spineZ.add(spine);

			for (GridNode node : nodes) {
				entries.put(node.key(), pack(node.x(), spine));
			}
		}

		int drainZ = z(drain);
		int north = spineZ.stream().mapToInt(Integer::intValue).min().orElse(drainZ);
		int south = spineZ.stream().mapToInt(Integer::intValue).max().orElse(drainZ);

		// The trunk runs to the drain's row, not to the middle of the grid: the sorter is wherever
		// it was built, and the last stretch is the same channel as the rest.
		if (north < drainZ) {
			segments.add(new WaterSegment(trunkX, north, trunkX, drainZ, WaterSegment.Kind.TRUNK));
		}

		if (south > drainZ) {
			segments.add(new WaterSegment(trunkX, south, trunkX, drainZ, WaterSegment.Kind.TRUNK));
		}

		// Only run the last stretch when there is a real block to run it to. The middle of a grid
		// is not a place: it is usually inside the centre node's own pyramid, and a channel dug to
		// it would break that beacon.
		if (spec.sink() != null && x(drain) != trunkX) {
			segments.add(new WaterSegment(trunkX, drainZ, x(drain), drainZ, WaterSegment.Kind.TRUNK));
		}

		return new WaterNetwork(plan, spec, segments, entries, orphans,
				spec.sink() == null ? pack(trunkX, drainZ) : drain);
	}

	/** Where the network drains: the block that was picked, or the middle of the grid. */
	private static long drain(PerimeterPlan plan, WaterSpec spec) {
		return spec.sink() == null
				? pack(plan.centerX(), plan.centerZ())
				: pack(spec.sink().x(), spec.sink().z());
	}

	/**
	 * Every node joins the network by the shortest right angle, nearest node first, starting from
	 * the middle.
	 *
	 * <p>Greedy rather than optimal: the optimal rectilinear tree is a Steiner problem, and the
	 * point here is a number to compare against the fishbone, not a build order. It is the shape
	 * that wins when the live nodes are a ring or a scatter rather than a solid blob.
	 */
	private static WaterNetwork tree(PerimeterPlan plan, WaterSpec spec) {
		List<GridNode> live = new ArrayList<>();
		List<NodeKey> orphans = new ArrayList<>();

		// Rows are the only thing rowStep can skip in a tree too, so that both shapes answer the
		// same question when coverage is cut back.
		for (GridNode node : plan.buildNodes()) {
			if (Math.floorMod(node.j(), spec.rowStep()) == 0) {
				live.add(node);
			} else {
				orphans.add(node.key());
			}
		}

		if (live.isEmpty()) {
			return new WaterNetwork(plan, spec, List.of(), Map.of(), orphans, drain(plan, spec));
		}

		Corridors corridors = Corridors.of(plan, live);
		List<WaterSegment> segments = new ArrayList<>();
		Map<NodeKey, Long> entries = new LinkedHashMap<>();

		// Each node still pays for the run along the face of its own pyramid, the same stretch the
		// fishbone gets for free, because that is where the water coming down the side lands.
		Map<NodeKey, Integer> targets = new LinkedHashMap<>();

		for (GridNode node : live) {
			int[] base = base(plan, node);
			int z = corridors.rowZ(node.j());
			segments.add(new WaterSegment(base[0], z, corridors.columnX(node.i()), z,
					WaterSegment.Kind.LINK));
			entries.put(node.key(), pack(node.x(), z));
			targets.put(node.key(), corridors.vertex(node.i(), node.j()));
		}

		long drain = drain(plan, spec);
		int sink = corridors.nearestVertex(x(drain), z(drain));
		Set<Integer> reached = new HashSet<>();
		reached.add(sink);
		Set<Long> laid = new HashSet<>();
		List<NodeKey> pending = new ArrayList<>(targets.keySet());

		while (!pending.isEmpty()) {
			// Cheapest node first, with everything already laid costing nothing to reuse. That
			// reuse is what turns a pile of shortest paths into a tree.
			Map<Integer, Integer> cost = corridors.dijkstra(reached, laid);
			NodeKey best = null;
			int bestCost = Integer.MAX_VALUE;

			for (NodeKey key : pending) {
				int c = cost.getOrDefault(targets.get(key), Integer.MAX_VALUE);

				if (c < bestCost) {
					bestCost = c;
					best = key;
				}
			}

			if (best == null) {
				// Cannot happen on a connected corridor grid, but a network that quietly drops
				// nodes is worse than one that says it did.
				break;
			}

			for (int[] edge : corridors.pathTo(targets.get(best), cost, laid)) {
				laid.add(Corridors.edgeKey(edge[0], edge[1]));
				reached.add(edge[0]);
				reached.add(edge[1]);
				segments.add(corridors.segment(edge[0], edge[1]));
			}

			reached.add(targets.get(best));
			pending.remove(best);
		}

		// The last stretch, from the corridor grid to the block that was actually picked. Same rule
		// as the fishbone: no last stretch unless there is a block to point at.
		long mouth = corridors.point(sink);

		if (spec.sink() != null) {
			if (x(mouth) != x(drain)) {
				segments.add(new WaterSegment(x(mouth), z(mouth), x(drain), z(mouth),
						WaterSegment.Kind.LINK));
			}

			if (z(mouth) != z(drain)) {
				segments.add(new WaterSegment(x(drain), z(mouth), x(drain), z(drain),
						WaterSegment.Kind.LINK));
			}
		}

		return new WaterNetwork(plan, spec, segments, entries, orphans,
				spec.sink() == null ? mouth : drain);
	}

	private static WaterNetwork empty(PerimeterPlan plan, WaterSpec spec) {
		return new WaterNetwork(plan, spec, List.of(), Map.of(), List.of(), drain(plan, spec));
	}

	// --------------------------------------------------------------- geometry

	/** The base of a node's pyramid as {@code {minX, maxX, minZ, maxZ}}, the layer the channel is on. */
	private static int[] base(PerimeterPlan plan, GridNode node) {
		int level = plan.level();
		int[] off = PyramidCalculator.groupOffsets(plan.beaconsAt(node.key()), level, plan.rowAxis());

		return new int[] {
			node.x() - level + off[0],
			node.x() + level + off[1],
			node.z() - level + off[2],
			node.z() + level + off[3]
		};
	}

	/**
	 * Where the trunk runs: the lane past a column of bases that lands closest to the drain.
	 *
	 * <p>Not always the centre column. Putting the trunk beside the drain is what keeps the trip
	 * at its Manhattan minimum for every node on the far side of it, and only the nodes past the
	 * drain pay a detour. It costs a little more channel and gets items in sooner, which is the
	 * trade this feature is meant to make.
	 */
	private static int trunkX(PerimeterPlan plan, List<GridNode> live, int drainX) {
		Map<Integer, Integer> lanes = new TreeMap<>();

		for (GridNode node : live) {
			lanes.merge(node.i(), base(plan, node)[1] + 1, Math::max);
		}

		Integer best = null;

		for (int lane : lanes.values()) {
			if (best == null || Math.abs(lane - drainX) < Math.abs(best - drainX)) {
				best = lane;
			}
		}

		if (best != null) {
			return best;
		}

		int[] off = PyramidCalculator.groupOffsets(plan.beaconsPerNode(), plan.level(), plan.rowAxis());
		return plan.centerX() + plan.level() + off[1] + 1;
	}

	/** Segments that run through a pyramid base, which is a build that would break a beacon. */
	private static List<WaterSegment> crossings(PerimeterPlan plan, List<WaterSegment> segments) {
		List<int[]> bases = new ArrayList<>();

		for (GridNode node : plan.buildNodes()) {
			bases.add(base(plan, node));
		}

		List<WaterSegment> hits = new ArrayList<>();

		for (WaterSegment segment : segments) {
			int minX = Math.min(segment.x1(), segment.x2());
			int maxX = Math.max(segment.x1(), segment.x2());
			int minZ = Math.min(segment.z1(), segment.z2());
			int maxZ = Math.max(segment.z1(), segment.z2());

			for (int[] b : bases) {
				if (minX <= b[1] && maxX >= b[0] && minZ <= b[3] && maxZ >= b[2]) {
					hits.add(segment);
					break;
				}
			}
		}

		return hits;
	}

	private static Set<Long> rasterise(List<WaterSegment> segments) {
		Set<Long> blocks = new HashSet<>();

		for (WaterSegment segment : segments) {
			int stepX = Integer.signum(segment.x2() - segment.x1());
			int stepZ = Integer.signum(segment.z2() - segment.z1());
			int x = segment.x1();
			int z = segment.z1();

			blocks.add(pack(x, z));

			while (x != segment.x2() || z != segment.z2()) {
				x += stepX;
				z += stepZ;
				blocks.add(pack(x, z));
			}
		}

		return blocks;
	}

	// ---------------------------------------------------------------- reading

	public WaterSpec spec() {
		return spec;
	}

	public List<WaterSegment> segments() {
		return segments;
	}

	/** Where everything ends up, as {@code {x, z}}: the picked block, or the middle of the grid. */
	public int[] drain() {
		return new int[] {x(sink), z(sink)};
	}

	/** Nodes left without a line, which is what cutting coverage back actually costs. */
	public List<NodeKey> orphans() {
		return orphans;
	}

	/** Runs that would be dug through a pyramid base. Anything here is a bug in the shape. */
	public List<WaterSegment> blockedByPyramids() {
		return blocked;
	}

	/** Blocks of channel, counted once where runs cross. */
	public int channelBlocks() {
		return blocks.size();
	}

	/** Places where three or more runs meet, which is where a current has to be steered. */
	public int junctions() {
		int junctions = 0;

		for (long block : blocks) {
			int neighbours = 0;

			for (long neighbour : neighbours(block)) {
				if (blocks.contains(neighbour)) {
					neighbours++;
				}
			}

			if (neighbours > 2) {
				junctions++;
			}
		}

		return junctions;
	}

	/**
	 * How far each node's items have to travel, measured along the channel rather than as the crow
	 * flies. Nodes the network never reaches are left out.
	 */
	public Map<NodeKey, Integer> distances() {
		Flood flood = flood();
		Map<NodeKey, Integer> distances = new LinkedHashMap<>();

		for (Map.Entry<NodeKey, Long> entry : entries.entrySet()) {
			Integer distance = flood.distance().get(entry.getValue());

			if (distance != null) {
				distances.put(entry.getKey(), distance);
			}
		}

		return distances;
	}

	/** The worst trip in the network, in blocks. */
	public int longestRun() {
		return distances().values().stream().mapToInt(Integer::intValue).max().orElse(0);
	}

	/**
	 * The trip every node would make if the channel ran straight at the middle through everything
	 * in its way. Nothing rectilinear can beat this, so it is the bar a layout is judged against
	 * when what matters is how fast items arrive rather than what the network costs.
	 */
	public Map<NodeKey, Integer> idealDistances() {
		Map<NodeKey, Integer> ideal = new LinkedHashMap<>();

		for (Map.Entry<NodeKey, Long> entry : entries.entrySet()) {
			ideal.put(entry.getKey(), manhattan(entry.getValue(), sink));
		}

		return ideal;
	}

	/**
	 * Corners on the way in, per node.
	 *
	 * <p>Worth watching separately from distance: a corner is where an item loses its momentum and
	 * has to be pushed again, so two networks of the same length do not arrive at the same time.
	 */
	public Map<NodeKey, Integer> turns() {
		Flood flood = flood();
		Map<NodeKey, Integer> turns = new LinkedHashMap<>();

		for (Map.Entry<NodeKey, Long> entry : entries.entrySet()) {
			Long at = entry.getValue();

			if (!flood.parent().containsKey(at) && !flood.distance().containsKey(at)) {
				continue;
			}

			int corners = 0;
			Boolean heading = null;

			while (flood.parent().containsKey(at)) {
				long next = flood.parent().get(at);
				boolean alongX = z(next) == z(at);

				if (heading != null && heading != alongX) {
					corners++;
				}

				heading = alongX;
				at = next;
			}

			turns.put(entry.getKey(), corners);
		}

		return turns;
	}

	/** Nodes with a line that the water cannot actually reach the middle from. */
	public List<NodeKey> disconnected() {
		Flood flood = flood();
		List<NodeKey> lost = new ArrayList<>();

		for (Map.Entry<NodeKey, Long> entry : entries.entrySet()) {
			if (!flood.distance().containsKey(entry.getValue())) {
				lost.add(entry.getKey());
			}
		}

		return lost;
	}

	/** Distance from the middle to every block of channel, and the way back. */
	private record Flood(Map<Long, Integer> distance, Map<Long, Long> parent) {
	}

	/**
	 * Walks the channel out from the middle. Also what proves the network is one piece.
	 *
	 * <p>The parent of each block is the way back to the middle. The network is a tree in both
	 * layouts, so that way back is the only way there is, which is what makes counting corners
	 * along it meaningful.
	 */
	private Flood flood() {
		Map<Long, Integer> distance = new HashMap<>();
		Map<Long, Long> parent = new HashMap<>();

		if (blocks.isEmpty()) {
			return new Flood(distance, parent);
		}

		long start = blocks.contains(sink) ? sink : nearestBlock(sink);
		Deque<Long> queue = new ArrayDeque<>();
		distance.put(start, 0);
		queue.add(start);

		while (!queue.isEmpty()) {
			long block = queue.removeFirst();
			int next = distance.get(block) + 1;

			for (long neighbour : neighbours(block)) {
				if (blocks.contains(neighbour) && !distance.containsKey(neighbour)) {
					distance.put(neighbour, next);
					parent.put(neighbour, block);
					queue.addLast(neighbour);
				}
			}
		}

		return new Flood(distance, parent);
	}

	private long nearestBlock(long from) {
		return blocks.stream().min(Comparator.comparingInt(block -> manhattan(from, block)))
				.orElse(from);
	}

	public WaterBudget budget() {
		int channel = channelBlocks();
		int sources = 0;

		for (WaterSegment segment : segments) {
			sources += Math.ceilDiv(segment.length(), spec.sourceEvery());
		}

		int junctions = junctions();

		return new WaterBudget(spec, channel, channel, sources, sources + junctions, junctions,
				channel * 2, trip(), entries.size(), orphans.size(), blocked.size());
	}

	/** How the trips in this network come out, which is the number to judge a layout by. */
	public WaterBudget.Trip trip() {
		Map<NodeKey, Integer> distances = distances();

		if (distances.isEmpty()) {
			return new WaterBudget.Trip(0, 0, 0, 0);
		}

		int longest = 0;
		int total = 0;

		for (int distance : distances.values()) {
			longest = Math.max(longest, distance);
			total += distance;
		}

		int ideal = 0;

		for (Map.Entry<NodeKey, Integer> entry : idealDistances().entrySet()) {
			if (distances.containsKey(entry.getKey())) {
				ideal = Math.max(ideal, entry.getValue());
			}
		}

		int corners = turns().values().stream().mapToInt(Integer::intValue).max().orElse(0);

		return new WaterBudget.Trip(longest, total / distances.size(), corners, ideal);
	}

	// ---------------------------------------------------------------- packing

	private static long pack(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private static int x(long packed) {
		return (int) (packed >> 32);
	}

	private static int z(long packed) {
		return (int) packed;
	}

	private static long[] neighbours(long block) {
		int x = x(block);
		int z = z(block);
		return new long[] {pack(x + 1, z), pack(x - 1, z), pack(x, z + 1), pack(x, z - 1)};
	}

	private static int manhattan(long a, long b) {
		return Math.abs(x(a) - x(b)) + Math.abs(z(a) - z(b));
	}
}
