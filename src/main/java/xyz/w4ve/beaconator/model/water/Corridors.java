package xyz.w4ve.beaconator.model.water;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import xyz.w4ve.beaconator.model.GridNode;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.model.PyramidCalculator;

/**
 * The lanes a channel is allowed to use, and the grid they make.
 *
 * <p>A perimeter at the bottom layer is not open ground: every live node has a pyramid base
 * sitting on that layer, and digging through one breaks a beacon. What is left between them is a
 * lattice of lanes, one just past the far side of each row and each column of bases, and any
 * network down there is drawn on that lattice whether it was planned that way or not.
 *
 * <p>Modelling it explicitly is what lets the tree layout be costed honestly: routing on the open
 * plane gives a shorter network that cannot be built.
 */
final class Corridors {
	private final List<Integer> columns;
	private final List<Integer> rows;
	private final Map<Integer, Integer> columnX;
	private final Map<Integer, Integer> rowZ;

	private Corridors(List<Integer> columns, List<Integer> rows,
			Map<Integer, Integer> columnX, Map<Integer, Integer> rowZ) {
		this.columns = columns;
		this.rows = rows;
		this.columnX = columnX;
		this.rowZ = rowZ;
	}

	/** One lane past the widest base of each column, and of each row, of the live nodes. */
	static Corridors of(PerimeterPlan plan, List<GridNode> live) {
		Map<Integer, Integer> columnX = new TreeMap<>();
		Map<Integer, Integer> rowZ = new TreeMap<>();
		int level = plan.level();

		for (GridNode node : live) {
			int[] off = PyramidCalculator.groupOffsets(plan.beaconsAt(node.key()), level,
					plan.rowAxis());
			int east = node.x() + level + off[1] + 1;
			int south = node.z() + level + off[3] + 1;
			columnX.merge(node.i(), east, Math::max);
			rowZ.merge(node.j(), south, Math::max);
		}

		return new Corridors(new ArrayList<>(columnX.keySet()), new ArrayList<>(rowZ.keySet()),
				columnX, rowZ);
	}

	int columnX(int i) {
		return columnX.get(i);
	}

	int rowZ(int j) {
		return rowZ.get(j);
	}

	/** The crossing of column {@code i} and row {@code j}, as a vertex id. */
	int vertex(int i, int j) {
		return columns.indexOf(i) * rows.size() + rows.indexOf(j);
	}

	int nearestVertex(int x, int z) {
		int best = 0;
		int bestDistance = Integer.MAX_VALUE;

		for (int vertex = 0; vertex < columns.size() * rows.size(); vertex++) {
			int distance = Math.abs(xOf(vertex) - x) + Math.abs(zOf(vertex) - z);

			if (distance < bestDistance) {
				bestDistance = distance;
				best = vertex;
			}
		}

		return best;
	}

	long point(int vertex) {
		return ((long) xOf(vertex) << 32) | (zOf(vertex) & 0xFFFFFFFFL);
	}

	WaterSegment segment(int from, int to) {
		return new WaterSegment(xOf(from), zOf(from), xOf(to), zOf(to), WaterSegment.Kind.LINK);
	}

	static long edgeKey(int a, int b) {
		int low = Math.min(a, b);
		int high = Math.max(a, b);
		return ((long) low << 32) | (high & 0xFFFFFFFFL);
	}

	/**
	 * Cheapest way to every crossing from the ones already reached, where lanes already laid cost
	 * nothing to use again. That last part is the whole trick: it is what makes a second node join
	 * the line its neighbour laid instead of running its own beside it.
	 */
	Map<Integer, Integer> dijkstra(Set<Integer> sources, Set<Long> laid) {
		Map<Integer, Integer> cost = new HashMap<>();
		PriorityQueue<int[]> queue = new PriorityQueue<>(Comparator.comparingInt(entry -> entry[1]));

		for (int source : sources) {
			cost.put(source, 0);
			queue.add(new int[] {source, 0});
		}

		while (!queue.isEmpty()) {
			int[] head = queue.poll();

			if (head[1] > cost.getOrDefault(head[0], Integer.MAX_VALUE)) {
				continue;
			}

			for (int neighbour : neighbours(head[0])) {
				int next = head[1] + weight(head[0], neighbour, laid);

				if (next < cost.getOrDefault(neighbour, Integer.MAX_VALUE)) {
					cost.put(neighbour, next);
					queue.add(new int[] {neighbour, next});
				}
			}
		}

		return cost;
	}

	/**
	 * The lanes a path from {@code vertex} back to the source uses, as {@code {upstream,
	 * downstream}} pairs. Lanes already laid are left out: they are built.
	 */
	List<int[]> pathTo(int vertex, Map<Integer, Integer> cost, Set<Long> laid) {
		List<int[]> path = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		int at = vertex;

		while (cost.getOrDefault(at, Integer.MAX_VALUE) > 0 && seen.add(at)) {
			int step = -1;

			for (int neighbour : neighbours(at)) {
				int through = cost.getOrDefault(neighbour, Integer.MAX_VALUE);

				if (through != Integer.MAX_VALUE && through + weight(at, neighbour, laid) == cost.get(at)) {
					step = neighbour;
					break;
				}
			}

			if (step < 0) {
				break;
			}

			if (!laid.contains(edgeKey(at, step))) {
				path.add(new int[] {at, step});
			}

			at = step;
		}

		return path;
	}

	private int weight(int a, int b, Set<Long> laid) {
		if (laid.contains(edgeKey(a, b))) {
			return 0;
		}

		return Math.abs(xOf(a) - xOf(b)) + Math.abs(zOf(a) - zOf(b));
	}

	private int[] neighbours(int vertex) {
		int column = vertex / rows.size();
		int row = vertex % rows.size();
		List<Integer> found = new ArrayList<>(4);

		if (column > 0) {
			found.add(vertex - rows.size());
		}

		if (column < columns.size() - 1) {
			found.add(vertex + rows.size());
		}

		if (row > 0) {
			found.add(vertex - 1);
		}

		if (row < rows.size() - 1) {
			found.add(vertex + 1);
		}

		int[] neighbours = new int[found.size()];

		for (int index = 0; index < found.size(); index++) {
			neighbours[index] = found.get(index);
		}

		return neighbours;
	}

	private int xOf(int vertex) {
		return columnX.get(columns.get(vertex / rows.size()));
	}

	private int zOf(int vertex) {
		return rowZ.get(rows.get(vertex % rows.size()));
	}
}
