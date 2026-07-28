package xyz.w4ve.beaconator.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PerimeterPlanTest {
	private static PerimeterPlan freshPlan() {
		PerimeterPlan plan = new PerimeterPlan("test", "minecraft:overworld", 0, 64, 0);
		plan.setLevel(4);
		return plan;
	}

	@Test
	@DisplayName("A fresh plan is one node, one beacon, touching coverage")
	void defaults() {
		PerimeterPlan plan = freshPlan();

		assertEquals(1, plan.nodes().size());
		assertEquals(1, plan.beaconsPerNode());
		assertEquals(101, plan.spacing());
		assertEquals(0, plan.ring());
		assertEquals(0, plan.gapAcrossRow(), "coverage touches with no overlap and no gap");
		assertFalse(plan.hasCoverageGaps());
	}

	@Test
	@DisplayName("Spacing follows the pyramid level until it is set by hand")
	void spacingFollowsLevel() {
		PerimeterPlan plan = freshPlan();
		plan.setLevel(2);
		assertEquals(61, plan.spacing());

		plan.setSpacing(50);
		plan.setLevel(4);
		assertEquals(50, plan.spacing(), "a hand set spacing stays put");

		plan.setAutoSpacing(true);
		assertEquals(101, plan.spacing());
	}

	@Test
	@DisplayName("A spacing wider than the coverage is reported as a gap")
	void gapsAreDetected() {
		PerimeterPlan plan = freshPlan();
		plan.setSpacing(120);

		assertTrue(plan.hasCoverageGaps());
		assertEquals(19, plan.gapAcrossRow());
	}

	@Test
	@DisplayName("Removed nodes drop out of the plan, excluded ones keep building")
	void statusAffectsMaterials() {
		PerimeterPlan plan = freshPlan();
		plan.setRing(1);
		plan.setPyramidBlock("minecraft:iron_block");

		assertEquals(9 * 164, plan.tally().get("minecraft:iron_block"));
		assertEquals(9, plan.tally().get(PerimeterPlan.BEACON_BLOCK));

		plan.toggleRemoved(new NodeKey(1, 1));
		assertEquals(8 * 164, plan.tally().get("minecraft:iron_block"));
		assertEquals(8, plan.buildNodes().size());

		plan.toggleExcluded(new NodeKey(-1, -1));
		assertEquals(8 * 164, plan.tally().get("minecraft:iron_block"), "excluded nodes are still built");
		assertEquals(1, plan.tally().get(PerimeterPlan.DEFAULT_MARKER_BLOCK));
	}

	@Test
	@DisplayName("Toggles come back to pending on a second click")
	void togglesAreReversible() {
		PerimeterPlan plan = freshPlan();
		NodeKey key = new NodeKey(0, 0);

		assertEquals(NodeStatus.REMOVED, plan.toggleRemoved(key));
		assertEquals(NodeStatus.PENDING, plan.toggleRemoved(key));
		assertEquals(NodeStatus.EXCLUDED, plan.toggleExcluded(key));
		assertEquals(NodeStatus.PENDING, plan.toggleExcluded(key));
		assertTrue(plan.overrides().isEmpty(), "back to default means nothing stored");
	}

	@Test
	@DisplayName("The marker block can be turned off without changing the states")
	void markerCanBeUiOnly() {
		PerimeterPlan plan = freshPlan();
		plan.setRing(1);
		plan.toggleExcluded(new NodeKey(1, 0));
		plan.setPlaceMarker(false);

		assertEquals(0, plan.tally().get(PerimeterPlan.DEFAULT_MARKER_BLOCK));
		assertEquals(NodeStatus.EXCLUDED, plan.statusAt(new NodeKey(1, 0)));
	}

	@Test
	@DisplayName("Shrinking the grid prunes the states that fell outside")
	void pruning() {
		PerimeterPlan plan = freshPlan();
		plan.setRing(2);
		plan.toggleRemoved(new NodeKey(2, 2));
		plan.toggleRemoved(new NodeKey(0, 1));

		plan.setRing(1);
		plan.pruneOverrides();

		assertEquals(1, plan.overrides().size());
		assertEquals(NodeStatus.PENDING, plan.statusAt(new NodeKey(2, 2)));
	}

	@Test
	@DisplayName("Walking the blocks matches the closed form count")
	void blockWalkMatchesTally() {
		PerimeterPlan plan = freshPlan();
		plan.setRing(1);
		plan.setBeaconsPerNode(3);
		plan.setPyramidBlock("minecraft:emerald_block");
		plan.toggleExcluded(new NodeKey(1, 1));
		plan.toggleRemoved(new NodeKey(-1, -1));

		Map<String, Integer> walked = new HashMap<>();

		for (GridNode node : plan.nodes()) {
			plan.forEachBlock(node, (x, y, z, id) -> walked.merge(id, 1, Integer::sum));
		}

		assertEquals(plan.tally().counts(), walked);
	}

	@Test
	@DisplayName("Every block of a node lands where the pyramid says it should")
	void blockPositions() {
		PerimeterPlan plan = freshPlan();
		plan.setBeaconsPerNode(2);
		plan.setRowAxis(RowAxis.Z);

		GridNode node = plan.nodes().get(0);
		Map<Integer, Integer> byY = new HashMap<>();
		plan.forEachBlock(node, (x, y, z, id) -> byY.merge(y, 1, Integer::sum));

		// Base first, then up to the beacons at y = 64.
		assertEquals(90, byY.get(60));
		assertEquals(56, byY.get(61));
		assertEquals(30, byY.get(62));
		assertEquals(12, byY.get(63));
		assertEquals(2, byY.get(64));
	}

	@Test
	@DisplayName("Asking what block goes at a position agrees with walking the plan")
	void blockAtAgreesWithTheWalk() {
		PerimeterPlan plan = freshPlan();
		plan.setRing(1);
		plan.setBeaconsPerNode(3);
		plan.setRowAxis(RowAxis.X);
		plan.setPyramidBlock("minecraft:iron_block");
		plan.toggleExcluded(new NodeKey(1, 1));
		plan.toggleRemoved(new NodeKey(-1, 0));

		Map<String, Integer> walked = new HashMap<>();

		for (GridNode node : plan.nodes()) {
			plan.forEachBlock(node, (x, y, z, id) -> {
				assertEquals(id, plan.blockAt(x, y, z), "at " + x + "," + y + "," + z);
				walked.merge(id, 1, Integer::sum);
			});
		}

		// Nothing outside the walk claims a block either.
		int[] bounds = plan.schematicBounds();
		int hits = 0;

		for (int x = bounds[0] - 2; x <= bounds[3] + 2; x++) {
			for (int y = bounds[1] - 2; y <= bounds[4] + 2; y++) {
				for (int z = bounds[2] - 2; z <= bounds[5] + 2; z++) {
					if (plan.blockAt(x, y, z) != null) {
						hits++;
					}
				}
			}
		}

		assertEquals(plan.tally().total(), hits);
		assertEquals(plan.tally().counts(), walked);
	}

	@Test
	@DisplayName("Removed nodes claim no blocks at all")
	void removedNodesClaimNothing() {
		PerimeterPlan plan = freshPlan();
		GridNode node = plan.nodes().get(0);
		assertEquals(PerimeterPlan.BEACON_BLOCK, plan.blockAt(node.x(), node.y(), node.z()));

		plan.toggleRemoved(node.key());
		assertNull(plan.blockAt(node.x(), node.y(), node.z()));
		assertNull(plan.blockAt(node.x(), node.y() - 1, node.z()));
	}

	@Nested
	@DisplayName("Against the real Big Culo perimeter")
	class BigCulo {
		/*
		 * Numbers below come from the perimeter that is already built on the server, read out
		 * of beacon_bigculo_emerald.litematic: a 1209 x 6 x 1510 schematic holding 416 beacons,
		 * 39,104 emerald blocks and 260 black stained glass. Beacons sit at x = 4, 104, ... 1204
		 * and at z pairs (4, 5), (104, 105), ... (1504, 1505), so 13 x 16 nodes of two beacons
		 * each, spaced 100 apart, on level 4 pyramids.
		 *
		 * If the geometry ever drifts, this is the test that catches it.
		 */
		private static final int NODES = 208;
		private static final int BEACONS = 416;
		private static final int EMERALD = 39104;
		private static final int EXCLUDED_NODES = 130;
		private static final int MARKERS = 260;

		private PerimeterPlan bigCulo() {
			PerimeterPlan plan = new PerimeterPlan("Big Culo", "minecraft:overworld", 604, 4, 704);
			plan.setLevel(4);
			plan.setSpacing(100);
			plan.setBeaconsPerNode(2);
			plan.setRowAxis(RowAxis.Z);
			plan.setPyramidBlock("minecraft:emerald_block");
			plan.setExtents(new GridExtents(-6, 6, -7, 8));
			return plan;
		}

		@Test
		@DisplayName("13 x 16 nodes land on the same blocks as the schematic")
		void gridMatches() {
			PerimeterPlan plan = bigCulo();
			List<GridNode> nodes = plan.nodes();

			assertEquals(NODES, nodes.size());
			assertEquals(13, plan.extents().columns());
			assertEquals(16, plan.extents().rows());

			GridNode first = nodes.get(0);
			assertEquals(4, first.x());
			assertEquals(4, first.z());
			assertEquals(4, first.y());

			GridNode last = nodes.get(nodes.size() - 1);
			assertEquals(1204, last.x());
			assertEquals(1504, last.z());

			List<int[]> beacons = plan.beaconPositionsOf(first);
			assertEquals(2, beacons.size());
			assertEquals(4, beacons.get(0)[2]);
			assertEquals(5, beacons.get(1)[2], "the second beacon of the row sits at z + 1");
		}

		@Test
		@DisplayName("Material counts match the schematic to the block")
		void materialsMatch() {
			PerimeterPlan plan = bigCulo();

			assertEquals(188, PyramidCalculator.totalBlocks(2, 4));
			assertEquals(EMERALD, plan.tally().get("minecraft:emerald_block"));
			assertEquals(BEACONS, plan.tally().get(PerimeterPlan.BEACON_BLOCK));
		}

		@Test
		@DisplayName("Marking 130 nodes as excluded asks for exactly 260 glass blocks")
		void markerCountMatches() {
			PerimeterPlan plan = bigCulo();
			List<GridNode> nodes = plan.nodes();

			for (int index = 0; index < EXCLUDED_NODES; index++) {
				plan.setStatus(nodes.get(index).key(), NodeStatus.EXCLUDED);
			}

			assertEquals(MARKERS, plan.tally().get(PerimeterPlan.DEFAULT_MARKER_BLOCK));
			assertEquals(EMERALD, plan.tally().get("minecraft:emerald_block"), "excluded nodes still build");
			assertEquals(EXCLUDED_NODES, plan.countByStatus(NodeStatus.EXCLUDED));
		}

		@Test
		@DisplayName("A spacing of 100 overlaps by one block, so no strip is left uncovered")
		void coverageHasNoGaps() {
			PerimeterPlan plan = bigCulo();

			assertEquals(-1, plan.gapAcrossRow());
			assertEquals(-2, plan.gapAlongRow());
			assertFalse(plan.hasCoverageGaps());
		}

		@Test
		@DisplayName("The blocks fit the schematic's 1209 x 6 x 1510 box exactly")
		void schematicBoundsMatch() {
			PerimeterPlan plan = bigCulo();
			// The real schematic has excluded nodes, which is what makes it 6 blocks tall.
			plan.setStatus(new NodeKey(0, 0), NodeStatus.EXCLUDED);

			int[] bounds = plan.schematicBounds();
			assertNotNull(bounds);
			assertEquals(1209, bounds[3] - bounds[0] + 1);
			assertEquals(6, bounds[4] - bounds[1] + 1);
			assertEquals(1510, bounds[5] - bounds[2] + 1);
			// Pyramid bases stick out 4 blocks past the outermost beacons at x = 4 and x = 1204.
			assertEquals(0, bounds[0]);
			assertEquals(1208, bounds[3]);
			assertEquals(0, bounds[1]);
			assertEquals(5, bounds[4]);
		}

		@Test
		@DisplayName("Coverage reaches 50 blocks past the outer beacons")
		void coverageBoundsMatch() {
			PerimeterPlan plan = bigCulo();
			int[] bounds = plan.coverageBounds();

			assertNotNull(bounds);
			assertEquals(4 - 50, bounds[0]);
			assertEquals(1204 + 50, bounds[2]);
			assertEquals(1301, bounds[2] - bounds[0] + 1);
			assertEquals(1602, bounds[3] - bounds[1] + 1);
		}
	}
}
