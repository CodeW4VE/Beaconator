package xyz.w4ve.beaconator.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GridDetectorTest {
	/** Every block a plan asks for, as {@code "x,y,z=block"}, which is what has to survive a round trip. */
	private static Set<String> blocksOf(PerimeterPlan plan) {
		Set<String> blocks = new HashSet<>();

		for (GridNode node : plan.nodes()) {
			plan.forEachBlock(node, (x, y, z, id) -> blocks.add(x + "," + y + "," + z + "=" + id));
		}

		return blocks;
	}

	private static GridDetector.Input inputFrom(PerimeterPlan plan) {
		List<int[]> beacons = new ArrayList<>();
		List<int[]> markers = new ArrayList<>();

		for (GridNode node : plan.nodes()) {
			plan.forEachBlock(node, (x, y, z, id) -> {
				if (PerimeterPlan.BEACON_BLOCK.equals(id)) {
					beacons.add(new int[] {x, y, z});
				} else if (plan.markerBlock().equals(id)) {
					markers.add(new int[] {x, y, z});
				}
			});
		}

		return new GridDetector.Input(beacons, markers, plan.pyramidBlock(), plan.markerBlock(), plan.level());
	}

	@Test
	@DisplayName("A plan survives the trip through raw block positions and back")
	void roundTrip() {
		PerimeterPlan original = new PerimeterPlan("Big Culo", "minecraft:overworld", 604, 4, 704);
		original.setLevel(4);
		original.setSpacing(100);
		original.setBeaconsPerNode(2);
		original.setRowAxis(RowAxis.Z);
		original.setPyramidBlock("minecraft:emerald_block");
		original.setExtents(new GridExtents(-6, 6, -7, 8));

		for (int i = -6; i <= -3; i++) {
			original.setStatus(new NodeKey(i, 0), NodeStatus.EXCLUDED);
		}

		original.setStatus(new NodeKey(3, 3), NodeStatus.REMOVED);
		original.setStatus(new NodeKey(4, 3), NodeStatus.REMOVED);

		PerimeterPlan detected = GridDetector.detect("Big Culo", "minecraft:overworld", inputFrom(original));

		assertEquals(100, detected.spacing());
		assertEquals(2, detected.beaconsPerNode());
		assertEquals(RowAxis.Z, detected.rowAxis());
		assertEquals(4, detected.level());
		assertEquals("minecraft:emerald_block", detected.pyramidBlock());
		assertEquals(4, detected.countByStatus(NodeStatus.EXCLUDED));
		assertEquals(original.buildNodes().size(), detected.buildNodes().size());
		assertEquals(blocksOf(original), blocksOf(detected), "same blocks in the same places");
	}

	@Test
	@DisplayName("Rows along X are told apart from rows along Z")
	void detectsRowAxis() {
		PerimeterPlan original = new PerimeterPlan("x", "minecraft:overworld", 0, 64, 0);
		original.setSpacing(101);
		original.setBeaconsPerNode(5);
		original.setRowAxis(RowAxis.X);
		original.setRing(1);

		PerimeterPlan detected = GridDetector.detect("x", "minecraft:overworld", inputFrom(original));

		assertEquals(RowAxis.X, detected.rowAxis());
		assertEquals(5, detected.beaconsPerNode());
		assertEquals(blocksOf(original), blocksOf(detected));
	}

	@Test
	@DisplayName("A single beacon per node still reports a sane grid")
	void singleBeaconNodes() {
		PerimeterPlan original = new PerimeterPlan("x", "minecraft:overworld", 1000, 70, -2000);
		original.setSpacing(101);
		original.setBeaconsPerNode(1);
		original.setRing(2);

		PerimeterPlan detected = GridDetector.detect("x", "minecraft:overworld", inputFrom(original));

		assertEquals(1, detected.beaconsPerNode());
		assertEquals(101, detected.spacing());
		assertEquals(25, detected.extents().nodeCount());
		assertEquals(blocksOf(original), blocksOf(detected));
	}

	@Test
	@DisplayName("Whole missing rows do not confuse the spacing")
	void spacingSurvivesGaps() {
		PerimeterPlan original = new PerimeterPlan("x", "minecraft:overworld", 0, 64, 0);
		original.setSpacing(100);
		original.setRing(2);

		for (int j = -2; j <= 2; j++) {
			original.setStatus(new NodeKey(0, j), NodeStatus.REMOVED);
		}

		PerimeterPlan detected = GridDetector.detect("x", "minecraft:overworld", inputFrom(original));

		assertEquals(100, detected.spacing());
		assertEquals(blocksOf(original), blocksOf(detected));
	}

	@Test
	@DisplayName("An empty schematic is rejected rather than guessed at")
	void needsBeacons() {
		GridDetector.Input empty = new GridDetector.Input(List.of(), List.of(), null, null, 4);
		assertThrows(IllegalArgumentException.class,
				() -> GridDetector.detect("x", "minecraft:overworld", empty));
	}
}
