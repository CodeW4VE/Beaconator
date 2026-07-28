package xyz.w4ve.beaconator.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import xyz.w4ve.beaconator.model.GridExtents;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;

/**
 * The plan travels to other players as the JSON of {@link PlanStore}. If a round trip drops
 * anything, everyone else on the server quietly builds a different perimeter, so it is worth a
 * test of its own rather than trusting that the file format also works as a packet.
 */
class PlanWirePayloadTest {
	private static PerimeterPlan sample() {
		PerimeterPlan plan = new PerimeterPlan("Big Culo", "minecraft:overworld", 604, 4, 704);
		plan.setExtents(GridExtents.ring(2));
		plan.setBeaconsPerNode(2);
		plan.setLevel(4);
		plan.setSpacing(101);
		plan.setStatus(new NodeKey(1, 0), NodeStatus.PLACED);
		plan.setStatus(new NodeKey(-1, 2), NodeStatus.EXCLUDED);
		plan.setStatus(new NodeKey(2, -2), NodeStatus.REMOVED);
		return plan;
	}

	@Test
	void survivesTheRoundTrip() {
		PerimeterPlan sent = sample();
		PerimeterPlan got = PlanStore.fromJson(PlanStore.toJson(sent), "shared");

		assertNotNull(got);
		assertEquals(sent.dimension(), got.dimension());
		assertEquals(sent.centerX(), got.centerX());
		assertEquals(sent.beaconY(), got.beaconY());
		assertEquals(sent.centerZ(), got.centerZ());
		assertEquals(sent.spacing(), got.spacing());
		assertEquals(sent.level(), got.level());
		assertEquals(sent.beaconsPerNode(), got.beaconsPerNode());
		assertEquals(sent.extents().nodeCount(), got.extents().nodeCount());
	}

	@Test
	void keepsEveryNodeState() {
		PerimeterPlan sent = sample();
		PerimeterPlan got = PlanStore.fromJson(PlanStore.toJson(sent), "shared");

		for (NodeStatus status : NodeStatus.values()) {
			assertEquals(sent.countByStatus(status), got.countByStatus(status), "count of " + status);
		}

		assertEquals(NodeStatus.PLACED, got.statusAt(new NodeKey(1, 0)));
		assertEquals(NodeStatus.EXCLUDED, got.statusAt(new NodeKey(-1, 2)));
		assertEquals(NodeStatus.REMOVED, got.statusAt(new NodeKey(2, -2)));
	}

	@Test
	void rubbishOnTheWireIsNotAPlan() {
		assertNull(PlanStore.fromJson("", "shared"));
		assertNull(PlanStore.fromJson("not json at all", "shared"));
		assertNull(PlanStore.fromJson("[1, 2, 3]", "shared"));
	}
}
