package xyz.w4ve.beaconator.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Nodes dragged off the grid.
 *
 * <p>The grid is arithmetic, and half the mod leans on that: which node a position belongs to,
 * what block goes where, what a click is pointing at. Moving one node breaks the arithmetic for
 * that node alone, so what these check is that the other two hundred carry on as they were.
 */
class MovedNodeTest {
	private static PerimeterPlan plan() {
		PerimeterPlan plan = new PerimeterPlan("test", "minecraft:overworld", 0, 64, 0);
		plan.setLevel(4);
		plan.setExtents(GridExtents.ring(2));
		return plan;
	}

	@Test
	@DisplayName("Moving one node leaves every other one where it was")
	void onlyTheMovedNodeMoves() {
		PerimeterPlan plan = plan();
		NodeKey moved = new NodeKey(1, 0);
		NodeKey neighbour = new NodeKey(2, 0);
		int neighbourX = plan.nodeAt(neighbour).x();

		plan.setOffsetAt(moved, 13, -7);

		assertEquals(101 + 13, plan.nodeAt(moved).x());
		assertEquals(-7, plan.nodeAt(moved).z());
		assertEquals(neighbourX, plan.nodeAt(neighbour).x(), "the neighbour did not budge");
		assertEquals(101, plan.gridNodeAt(moved).x(), "the grid still knows where it belongs");
		assertEquals(0, plan.nodes().size() - plan.extents().nodeCount(), "no node appeared or left");
	}

	@Test
	@DisplayName("An offset rides along with the node's own state and is not stored otherwise")
	void offsetIsAnOverride() {
		PerimeterPlan plan = plan();
		NodeKey key = new NodeKey(0, 1);

		assertFalse(plan.moved(key));
		assertTrue(plan.overrides().isEmpty(), "a plan nobody touched stores nothing");

		plan.setOffsetAt(key, 5, 0);
		plan.setStatus(key, NodeStatus.PLACED);

		assertTrue(plan.moved(key));
		assertEquals(NodeStatus.PLACED, plan.statusAt(key), "the state survived the move");
		assertEquals(List.of(key), plan.movedKeys());

		plan.setOffsetAt(key, 0, 0);
		assertFalse(plan.moved(key));
		assertEquals(NodeStatus.PLACED, plan.statusAt(key), "putting it back is not a reset");
	}

	@Test
	@DisplayName("An offset cannot reach past a neighbour's cell")
	void offsetIsClamped() {
		PerimeterPlan plan = plan();
		NodeKey key = new NodeKey(0, 0);

		plan.setOffsetAt(key, 5000, -5000);

		assertEquals(plan.spacing(), plan.offsetAt(key)[0]);
		assertEquals(-plan.spacing(), plan.offsetAt(key)[1]);
	}

	@Test
	@DisplayName("A moved node is still what you are pointing at, and its old cell is not")
	void pickingFollowsTheNode() {
		PerimeterPlan plan = plan();
		NodeKey key = new NodeKey(1, 0);
		plan.setOffsetAt(key, 40, 40);

		assertEquals(key, plan.keyNear(101 + 40 + 0.5, 40 + 0.5), "right on top of where it went");
		assertEquals(new NodeKey(2, 0), plan.keyNear(202.5, 0.5), "a node that never moved");
		assertNull(plan.keyNear(9000.0, 9000.0), "nothing out in the middle of nowhere");
	}

	@Test
	@DisplayName("The blocks of a moved node are asked for at its new spot, not its old one")
	void blocksFollowTheNode() {
		PerimeterPlan plan = plan();
		NodeKey key = new NodeKey(1, 0);
		GridNode home = plan.nodeAt(key);

		assertEquals(PerimeterPlan.BEACON_BLOCK, plan.blockAt(home.x(), home.y(), home.z()));

		plan.setOffsetAt(key, 20, 30);
		GridNode moved = plan.nodeAt(key);

		assertEquals(PerimeterPlan.BEACON_BLOCK, plan.blockAt(moved.x(), moved.y(), moved.z()),
				"the beacon is where the node went");
		assertNull(plan.blockAt(home.x(), home.y(), home.z()),
				"and nothing is left behind at the old spot");
		assertEquals(plan.pyramidBlock(), plan.blockAt(moved.x() + 2, moved.y() - 3, moved.z()),
				"the pyramid came with it");
	}

	@Test
	@DisplayName("A node moved right out of its own cell is still found by blockAt")
	void blocksFoundAcrossCells() {
		PerimeterPlan plan = plan();
		NodeKey key = new NodeKey(0, 0);
		// Past halfway to the neighbour, so the position now falls in the neighbour's cell and
		// the one division that used to answer this question gives the wrong node.
		plan.setOffsetAt(key, 70, 0);
		GridNode moved = plan.nodeAt(key);

		assertEquals(new NodeKey(1, 0), GridGenerator.nearestKey(plan.centerX(), plan.centerZ(),
				plan.spacing(), moved.x(), moved.z()), "it really is in the neighbour's cell");
		assertEquals(PerimeterPlan.BEACON_BLOCK, plan.blockAt(moved.x(), moved.y(), moved.z()));
	}

	@Test
	@DisplayName("Moving a node does not change what the perimeter costs")
	void materialsAreUnchanged() {
		PerimeterPlan plan = plan();
		MaterialTally before = plan.tally();

		plan.setOffsetAt(new NodeKey(1, 1), 17, -23);

		assertEquals(before.counts(), plan.tally().counts());
	}

	@Test
	@DisplayName("The coverage of the whole plan follows the node that moved")
	void coverageFollowsTheNode() {
		PerimeterPlan plan = plan();
		int[] before = plan.coverageBounds();

		plan.setOffsetAt(new NodeKey(2, 0), 30, 0);
		int[] after = plan.coverageBounds();

		assertNotNull(after);
		assertEquals(before[2] + 30, after[2], "the east edge moved with it");
		assertEquals(before[0], after[0], "the west edge did not");
	}
}
