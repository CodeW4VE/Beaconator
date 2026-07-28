package xyz.w4ve.beaconator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A whole beacon perimeter: where the grid is, how big, and what each node is doing.
 *
 * <p>Plain Java on purpose. Nothing here touches Minecraft, so the geometry can be tested in
 * CI instead of by flying around a world.
 *
 * <p>The plan holds defaults for the whole grid (spacing, pyramid level, beacons per node)
 * and a small map of per node overrides, so a fresh 200 node plan stores almost nothing.
 */
public final class PerimeterPlan {
	public static final String DEFAULT_PYRAMID_BLOCK = "minecraft:iron_block";
	public static final String DEFAULT_MARKER_BLOCK = "minecraft:black_stained_glass";
	public static final String BEACON_BLOCK = "minecraft:beacon";

	private String name;
	private String dimension;

	private int centerX;
	private int beaconY;
	private int centerZ;

	private int spacing;
	private boolean autoSpacing = true;
	private int level = 4;
	private int beaconsPerNode = 1;
	private RowAxis rowAxis = RowAxis.Z;
	private GridExtents extents = GridExtents.ring(0);

	private String pyramidBlock = DEFAULT_PYRAMID_BLOCK;
	private String markerBlock = DEFAULT_MARKER_BLOCK;
	private boolean placeMarker = true;

	private final Map<NodeKey, NodeData> overrides = new HashMap<>();

	public PerimeterPlan(String name, String dimension, int centerX, int beaconY, int centerZ) {
		this.name = name;
		this.dimension = dimension;
		this.centerX = centerX;
		this.beaconY = beaconY;
		this.centerZ = centerZ;
		this.spacing = CoverageBox.sideFor(level);
	}

	// ---------------------------------------------------------------- settings

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String dimension() {
		return dimension;
	}

	public void setDimension(String dimension) {
		this.dimension = dimension;
	}

	public int centerX() {
		return centerX;
	}

	public int centerZ() {
		return centerZ;
	}

	public int beaconY() {
		return beaconY;
	}

	public void setCenter(int x, int y, int z) {
		this.centerX = x;
		this.beaconY = y;
		this.centerZ = z;
	}

	public int spacing() {
		return spacing;
	}

	/** Sets the spacing by hand, which also stops it from following the pyramid level. */
	public void setSpacing(int spacing) {
		if (spacing < 1) {
			throw new IllegalArgumentException("Spacing must be >= 1, got " + spacing);
		}

		this.spacing = spacing;
		this.autoSpacing = false;
	}

	public boolean autoSpacing() {
		return autoSpacing;
	}

	/** Back to a spacing that follows the pyramid level: exactly touching coverage, no overlap. */
	public void setAutoSpacing(boolean auto) {
		this.autoSpacing = auto;

		if (auto) {
			this.spacing = CoverageBox.sideFor(level);
		}
	}

	public int level() {
		return level;
	}

	public void setLevel(int level) {
		if (level < 1 || level > PyramidCalculator.MAX_LEVEL) {
			throw new IllegalArgumentException("Pyramid level must be 1..4, got " + level);
		}

		this.level = level;

		if (autoSpacing) {
			this.spacing = CoverageBox.sideFor(level);
		}
	}

	public int beaconsPerNode() {
		return beaconsPerNode;
	}

	/** Beacons per node for the whole grid. Per node counts exist in the model but no UI sets them. */
	public void setBeaconsPerNode(int beacons) {
		if (beacons < 1 || beacons > PyramidCalculator.MAX_BEACONS_PER_NODE) {
			throw new IllegalArgumentException("Beacons per node must be 1..5, got " + beacons);
		}

		this.beaconsPerNode = beacons;
	}

	public RowAxis rowAxis() {
		return rowAxis;
	}

	public void setRowAxis(RowAxis rowAxis) {
		this.rowAxis = rowAxis;
	}

	public GridExtents extents() {
		return extents;
	}

	public void setExtents(GridExtents extents) {
		this.extents = extents;
	}

	/** Current ring if the four sides match, or -1 once a side has been adjusted on its own. */
	public int ring() {
		return extents.asRing();
	}

	public void setRing(int ring) {
		this.extents = GridExtents.ring(ring);
	}

	public String pyramidBlock() {
		return pyramidBlock;
	}

	public void setPyramidBlock(String pyramidBlock) {
		this.pyramidBlock = pyramidBlock;
	}

	public String markerBlock() {
		return markerBlock;
	}

	public void setMarkerBlock(String markerBlock) {
		this.markerBlock = markerBlock;
	}

	public boolean placeMarker() {
		return placeMarker;
	}

	/** Whether excluded nodes get a real marker block on top, or are only coloured in the UI. */
	public void setPlaceMarker(boolean placeMarker) {
		this.placeMarker = placeMarker;
	}

	// ------------------------------------------------------------------- nodes

	/** Every node inside the extents, including removed ones. */
	public List<GridNode> nodes() {
		List<GridNode> nodes = GridGenerator.nodes(centerX, beaconY, centerZ, spacing, extents);

		for (int index = 0; index < nodes.size(); index++) {
			GridNode node = nodes.get(index);
			Integer y = yOverride(node.key());

			if (y != null) {
				nodes.set(index, new GridNode(node.i(), node.j(), node.x(), y, node.z()));
			}
		}

		return nodes;
	}

	/** Only the nodes that are actually built. */
	public List<GridNode> buildNodes() {
		List<GridNode> built = new ArrayList<>();

		for (GridNode node : nodes()) {
			if (statusAt(node.key()).isBuilt()) {
				built.add(node);
			}
		}

		return built;
	}

	public GridNode nodeAt(NodeKey key) {
		Integer y = yOverride(key);
		return GridGenerator.nodeAt(centerX, y == null ? beaconY : y, centerZ, spacing, key.i(), key.j());
	}

	public NodeStatus statusAt(NodeKey key) {
		NodeData data = overrides.get(key);
		return data == null ? NodeStatus.PENDING : data.status();
	}

	public void setStatus(NodeKey key, NodeStatus status) {
		update(key, data -> data.withStatus(status));
	}

	/** Left click behaviour: drop the node from the plan, or put it back. */
	public NodeStatus toggleRemoved(NodeKey key) {
		NodeStatus next = statusAt(key) == NodeStatus.REMOVED ? NodeStatus.PENDING : NodeStatus.REMOVED;
		setStatus(key, next);
		return next;
	}

	/** Right click behaviour: mark the node as outside the perimeter, or put it back. */
	public NodeStatus toggleExcluded(NodeKey key) {
		NodeStatus next = statusAt(key) == NodeStatus.EXCLUDED ? NodeStatus.PENDING : NodeStatus.EXCLUDED;
		setStatus(key, next);
		return next;
	}

	public int beaconsAt(NodeKey key) {
		NodeData data = overrides.get(key);
		return data == null || data.beacons() == null ? beaconsPerNode : data.beacons();
	}

	/** Reserved for the expert mode: a single node with a different beacon count. */
	public void setBeaconsAt(NodeKey key, Integer beacons) {
		if (beacons != null && (beacons < 1 || beacons > PyramidCalculator.MAX_BEACONS_PER_NODE)) {
			throw new IllegalArgumentException("Beacons per node must be 1..5, got " + beacons);
		}

		update(key, data -> data.withBeacons(beacons));
	}

	public Integer yOverride(NodeKey key) {
		NodeData data = overrides.get(key);
		return data == null ? null : data.y();
	}

	/** Reserved for uneven ground: a single node sitting at a different height. */
	public void setYAt(NodeKey key, Integer y) {
		update(key, data -> data.withY(y));
	}

	public Map<NodeKey, NodeData> overrides() {
		return Collections.unmodifiableMap(overrides);
	}

	/** Used when loading a saved plan. */
	public void putOverride(NodeKey key, NodeData data) {
		if (data == null || data.isDefault()) {
			overrides.remove(key);
		} else {
			overrides.put(key, data);
		}
	}

	public void clearOverrides() {
		overrides.clear();
	}

	/** Drops overrides for nodes that fell outside the grid after it shrank. */
	public void pruneOverrides() {
		overrides.keySet().removeIf(key -> !extents.contains(key.i(), key.j()));
	}

	private void update(NodeKey key, java.util.function.UnaryOperator<NodeData> op) {
		NodeData current = overrides.getOrDefault(key, NodeData.PENDING);
		NodeData next = op.apply(current);

		if (next.isDefault()) {
			overrides.remove(key);
		} else {
			overrides.put(key, next);
		}
	}

	// ---------------------------------------------------------------- geometry

	public CoverageBox coverageOf(GridNode node) {
		return CoverageBox.forRow(node.x(), node.y(), node.z(), level, beaconsAt(node.key()), rowAxis);
	}

	public List<PyramidLayer> layersOf(GridNode node) {
		return PyramidCalculator.layers(node.x(), node.y(), node.z(), level, beaconsAt(node.key()), rowAxis);
	}

	public List<int[]> beaconPositionsOf(GridNode node) {
		return PyramidCalculator.beaconPositions(node.x(), node.y(), node.z(), beaconsAt(node.key()), rowAxis);
	}

	/**
	 * Blocks left uncovered between neighbouring nodes, per axis. Negative means the coverage
	 * overlaps by that much, which is the safe side; positive means a strip of the perimeter
	 * gets no effect at all.
	 */
	public int gapAcrossRow() {
		return spacing - CoverageBox.sideFor(level);
	}

	public int gapAlongRow() {
		return spacing - (CoverageBox.sideFor(level) + beaconsPerNode - 1);
	}

	public boolean hasCoverageGaps() {
		return gapAcrossRow() > 0 || gapAlongRow() > 0;
	}

	// --------------------------------------------------------------- materials

	/** Walks every block this plan asks for, bottom layer first so it can be built as it goes. */
	public void forEachBlock(GridNode node, BlockSink sink) {
		NodeStatus status = statusAt(node.key());

		if (!status.isBuilt()) {
			return;
		}

		List<PyramidLayer> layers = layersOf(node);

		for (int index = layers.size() - 1; index >= 0; index--) {
			PyramidLayer layer = layers.get(index);

			for (int x = layer.minX(); x <= layer.maxX(); x++) {
				for (int z = layer.minZ(); z <= layer.maxZ(); z++) {
					sink.accept(x, layer.y(), z, pyramidBlock);
				}
			}
		}

		for (int[] pos : beaconPositionsOf(node)) {
			sink.accept(pos[0], pos[1], pos[2], BEACON_BLOCK);
		}

		if (status == NodeStatus.EXCLUDED && placeMarker) {
			for (int[] pos : beaconPositionsOf(node)) {
				sink.accept(pos[0], pos[1] + 1, pos[2], markerBlock);
			}
		}
	}

	/**
	 * The block this plan wants at a position, or null if it wants nothing there.
	 *
	 * <p>Answered without building any index: only the nearest node can possibly reach a given
	 * position, because a pyramid is always far smaller than the spacing between nodes. That
	 * makes this cheap enough to call once per frame for assisted placement.
	 */
	public String blockAt(int x, int y, int z) {
		NodeKey key = GridGenerator.nearestKey(centerX, centerZ, spacing, x, z);

		if (!extents.contains(key.i(), key.j())) {
			return null;
		}

		NodeStatus status = statusAt(key);

		if (!status.isBuilt()) {
			return null;
		}

		GridNode node = nodeAt(key);
		int beacons = beaconsAt(key);
		int extra = beacons - 1;

		if (y == node.y() || (y == node.y() + 1 && status == NodeStatus.EXCLUDED && placeMarker)) {
			boolean onRow = rowAxis == RowAxis.X
					? z == node.z() && x >= node.x() && x <= node.x() + extra
					: x == node.x() && z >= node.z() && z <= node.z() + extra;

			if (!onRow) {
				return null;
			}

			return y == node.y() ? BEACON_BLOCK : markerBlock;
		}

		int depth = node.y() - y;

		if (depth < 1 || depth > level) {
			return null;
		}

		int minX = node.x() - depth;
		int maxX = node.x() + depth + (rowAxis == RowAxis.X ? extra : 0);
		int minZ = node.z() - depth;
		int maxZ = node.z() + depth + (rowAxis == RowAxis.Z ? extra : 0);

		if (x < minX || x > maxX || z < minZ || z > maxZ) {
			return null;
		}

		return pyramidBlock;
	}

	/** Everything the whole plan asks for. Counted in closed form, no per block walk. */
	public MaterialTally tally() {
		MaterialTally tally = new MaterialTally();

		for (GridNode node : nodes()) {
			NodeStatus status = statusAt(node.key());

			if (!status.isBuilt()) {
				continue;
			}

			int beacons = beaconsAt(node.key());
			tally.add(pyramidBlock, PyramidCalculator.totalBlocks(beacons, level));
			tally.add(BEACON_BLOCK, beacons);

			if (status == NodeStatus.EXCLUDED && placeMarker) {
				tally.add(markerBlock, beacons);
			}
		}

		return tally;
	}

	/** Blocks one node asks for, useful for the per node breakdown. */
	public MaterialTally tallyOf(GridNode node) {
		MaterialTally tally = new MaterialTally();
		NodeStatus status = statusAt(node.key());

		if (!status.isBuilt()) {
			return tally;
		}

		int beacons = beaconsAt(node.key());
		tally.add(pyramidBlock, PyramidCalculator.totalBlocks(beacons, level));
		tally.add(BEACON_BLOCK, beacons);

		if (status == NodeStatus.EXCLUDED && placeMarker) {
			tally.add(markerBlock, beacons);
		}

		return tally;
	}

	public int countByStatus(NodeStatus status) {
		int count = 0;

		for (GridNode node : nodes()) {
			if (statusAt(node.key()) == status) {
				count++;
			}
		}

		return count;
	}

	/**
	 * Footprint of the effect coverage as {@code [minX, minZ, maxX, maxZ]}, or null when every
	 * node was removed. This is the area that gets the buffs, which is wider than the build.
	 */
	public int[] coverageBounds() {
		Integer minX = null;
		Integer maxX = null;
		Integer minZ = null;
		Integer maxZ = null;

		for (GridNode node : buildNodes()) {
			CoverageBox box = coverageOf(node);
			minX = minX == null ? box.minX() : Math.min(minX, box.minX());
			maxX = maxX == null ? box.maxX() : Math.max(maxX, box.maxX());
			minZ = minZ == null ? box.minZ() : Math.min(minZ, box.minZ());
			maxZ = maxZ == null ? box.maxZ() : Math.max(maxZ, box.maxZ());
		}

		if (minX == null) {
			return null;
		}

		return new int[] {minX, minZ, maxX, maxZ};
	}

	/**
	 * Box the actual blocks occupy, as {@code [minX, minY, minZ, maxX, maxY, maxZ]}, or null
	 * when every node was removed. This is what a schematic export has to be sized to: the
	 * pyramid bases stick out {@code level} blocks past the outermost beacons.
	 */
	public int[] schematicBounds() {
		Integer minX = null;
		Integer maxX = null;
		Integer minZ = null;
		Integer maxZ = null;
		Integer minY = null;
		Integer maxY = null;

		for (GridNode node : buildNodes()) {
			int beacons = beaconsAt(node.key());
			int extra = beacons - 1;
			int baseMinX = node.x() - level;
			int baseMaxX = node.x() + level + (rowAxis == RowAxis.X ? extra : 0);
			int baseMinZ = node.z() - level;
			int baseMaxZ = node.z() + level + (rowAxis == RowAxis.Z ? extra : 0);
			int top = node.y() + (statusAt(node.key()) == NodeStatus.EXCLUDED && placeMarker ? 1 : 0);

			minX = minX == null ? baseMinX : Math.min(minX, baseMinX);
			maxX = maxX == null ? baseMaxX : Math.max(maxX, baseMaxX);
			minZ = minZ == null ? baseMinZ : Math.min(minZ, baseMinZ);
			maxZ = maxZ == null ? baseMaxZ : Math.max(maxZ, baseMaxZ);
			minY = minY == null ? node.y() - level : Math.min(minY, node.y() - level);
			maxY = maxY == null ? top : Math.max(maxY, top);
		}

		if (minX == null) {
			return null;
		}

		return new int[] {minX, minY, minZ, maxX, maxY, maxZ};
	}
}
