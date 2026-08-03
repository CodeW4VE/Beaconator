package xyz.w4ve.beaconator.model.water;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xyz.w4ve.beaconator.model.GridExtents;
import xyz.w4ve.beaconator.model.GridNode;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;

/**
 * The network is geometry, so it can be checked without the game. What these tests are really
 * guarding is the one rule that would cost a rebuild: no run may pass through a pyramid base,
 * because that is a broken beacon that nobody notices until the haste stops.
 */
class WaterNetworkTest {
	private static PerimeterPlan plan(int ring) {
		PerimeterPlan plan = new PerimeterPlan("test", "minecraft:overworld", 0, -55, 0);
		plan.setExtents(GridExtents.ring(ring));
		plan.setAutoSpacing(false);
		plan.setSpacing(101);
		plan.setLevel(4);
		plan.setBeaconsPerNode(2);
		return plan;
	}

	@Test
	@DisplayName("No run is dug through a pyramid base")
	void keepsClearOfThePyramids() {
		for (WaterLayout layout : WaterLayout.values()) {
			WaterNetwork network = WaterNetwork.of(plan(3), WaterSpec.defaults().with(layout));
			assertTrue(network.blockedByPyramids().isEmpty(),
					layout + " runs through " + network.blockedByPyramids().size() + " bases");
		}
	}

	@Test
	@DisplayName("Every node's water reaches the middle")
	void everyNodeIsConnected() {
		for (WaterLayout layout : WaterLayout.values()) {
			PerimeterPlan plan = plan(3);
			WaterNetwork network = WaterNetwork.of(plan, WaterSpec.defaults().with(layout));

			assertTrue(network.orphans().isEmpty(), layout + " left nodes out");
			assertTrue(network.disconnected().isEmpty(), layout + " left islands");
			assertEquals(plan.buildNodes().size(), network.distances().size(),
					layout + " did not measure every node");
		}
	}

	@Test
	@DisplayName("A fishbone spine is one row wide and the trunk carries it in")
	void fishboneMeasuresUp() {
		PerimeterPlan plan = plan(1);
		WaterNetwork network = WaterNetwork.of(plan, WaterSpec.defaults());

		// Three rows of three nodes at 101 apart. A spine spans the row's bases, the trunk spans
		// the outer rows, and the only overlap is where they cross.
		assertEquals(3, network.segments().stream()
				.filter(segment -> segment.kind() == WaterSegment.Kind.SPINE)
				.mapToInt(segment -> segment.z1()).distinct().count());

		Map<NodeKey, Integer> distances = network.distances();
		assertEquals(0, network.orphans().size());

		// The far corner is two spacings out, so its trip is about that plus the clearance.
		int corner = distances.get(new NodeKey(-1, -1));
		assertTrue(corner > 200 && corner < 230, "corner trip was " + corner);

		// The centre node is next to the sink, so it is the cheapest trip of all.
		assertTrue(distances.get(new NodeKey(0, 0)) < corner);
	}

	@Test
	@DisplayName("A fishbone trip is as short as a rectilinear channel can be")
	void fishboneIsAsFastAsItGets() {
		WaterNetwork network = WaterNetwork.of(plan(3), WaterSpec.defaults());
		Map<NodeKey, Integer> ideal = network.idealDistances();

		// Every item travels its own Manhattan distance to the middle, which is the floor: no
		// network without diagonals gets an item in sooner. This is what makes the fishbone the
		// answer when the goal is speed rather than materials.
		network.distances().forEach((key, distance) ->
				assertEquals(ideal.get(key), distance, "node " + key.i() + "," + key.j()));

		assertEquals(0, network.trip().overhead());
		// One corner: off the spine onto the trunk. There is no way in with none.
		assertEquals(1, network.trip().maxTurns());
	}

	@Test
	@DisplayName("The network drains where you point it, not at the middle of the grid")
	void drainsAtThePickedBlock() {
		PerimeterPlan plan = plan(2);
		int x = plan.centerX() + 400;
		int z = plan.centerZ() + 400;

		for (WaterLayout layout : WaterLayout.values()) {
			WaterNetwork network = WaterNetwork.of(plan,
					WaterSpec.defaults().with(layout).drainingAt(x, z));

			assertArrayEquals(new int[] {x, z}, network.drain(), layout + " drained elsewhere");
			assertTrue(network.disconnected().isEmpty(), layout + " never reaches the drain");
			assertTrue(network.blockedByPyramids().isEmpty(), layout + " digs through a base");
		}
	}

	@Test
	@DisplayName("The trunk moves to the drain, so nobody takes the long way round")
	void trunkFollowsTheDrain() {
		PerimeterPlan plan = plan(2);
		// A sorter off the east side of the perimeter. Every node is then west of the trunk, so
		// every trip is still the shortest one there is.
		WaterNetwork network = WaterNetwork.of(plan,
				WaterSpec.defaults().drainingAt(plan.centerX() + 400, plan.centerZ()));
		Map<NodeKey, Integer> ideal = network.idealDistances();

		network.distances().forEach((key, distance) ->
				assertEquals(ideal.get(key), distance, "node " + key.i() + "," + key.j()));

		// Against the middle column it would have been a detour of two spacings for the far side.
		assertEquals(0, network.trip().overhead());
	}

	@Test
	@DisplayName("A tree pays for its length with slower trips")
	void treeTradesSpeedForMaterials() {
		WaterNetwork tree = WaterNetwork.of(plan(3), WaterSpec.defaults().with(WaterLayout.TREE));

		assertTrue(tree.trip().overhead() > 0, "tree somehow matched the ideal");
		assertTrue(tree.trip().maxTurns() > 1, "tree somehow went straight in");
	}

	@Test
	@DisplayName("Removed nodes get no line")
	void skipsRemovedNodes() {
		PerimeterPlan plan = plan(2);

		for (GridNode node : plan.nodes()) {
			if (node.i() != 0) {
				plan.setStatus(node.key(), NodeStatus.REMOVED);
			}
		}

		WaterNetwork network = WaterNetwork.of(plan, WaterSpec.defaults());

		assertEquals(5, network.distances().size());
		// One column of nodes needs no spine worth the name, so the network is mostly trunk.
		assertTrue(network.channelBlocks() < WaterNetwork.of(plan(2), WaterSpec.defaults())
				.channelBlocks());
	}

	@Test
	@DisplayName("Skipping rows leaves nodes behind, and says so")
	void partialCoverageIsCounted() {
		PerimeterPlan plan = plan(2);
		WaterNetwork every = WaterNetwork.of(plan, WaterSpec.defaults());
		WaterNetwork other = WaterNetwork.of(plan, WaterSpec.defaults().withRowStep(2));

		assertEquals(0, every.orphans().size());
		assertEquals(10, other.orphans().size());
		assertTrue(other.channelBlocks() < every.channelBlocks());
		assertEquals(other.orphans().size(), other.budget().nodesOrphaned());
	}

	@Test
	@DisplayName("A tree beats a fishbone when the middle is empty")
	void treeWinsOnARing() {
		PerimeterPlan plan = plan(3);

		for (GridNode node : plan.nodes()) {
			if (Math.abs(node.i()) < 3 && Math.abs(node.j()) < 3 && !(node.i() == 0 && node.j() == 0)) {
				plan.setStatus(node.key(), NodeStatus.REMOVED);
			}
		}

		int fishbone = WaterNetwork.of(plan, WaterSpec.defaults()).channelBlocks();
		int tree = WaterNetwork.of(plan, WaterSpec.defaults().with(WaterLayout.TREE)).channelBlocks();

		assertTrue(tree < fishbone, "tree " + tree + " was not shorter than fishbone " + fishbone);
	}

	@Test
	@DisplayName("The ice is counted as what has to be mined, not as what is placed")
	void iceIsCountedAsItIsGathered() {
		WaterBudget budget = WaterNetwork.of(plan(2), WaterSpec.defaults()).budget();

		assertEquals(budget.channelBlocks(), budget.iceBlocks());
		assertEquals(budget.iceBlocks() * 9, budget.iceToMine());
		assertEquals(budget.iceBlocks(), budget.tally().get(WaterSpec.PACKED_ICE));

		WaterBudget blue = WaterNetwork.of(plan(2), WaterSpec.defaults().withIce(WaterSpec.BLUE_ICE))
				.budget();
		assertEquals(blue.iceBlocks() * 81, blue.iceToMine());
	}

	@Test
	@DisplayName("An empty plan costs nothing instead of throwing")
	void emptyPlanIsFree() {
		PerimeterPlan plan = plan(1);

		for (GridNode node : plan.nodes()) {
			plan.setStatus(node.key(), NodeStatus.REMOVED);
		}

		WaterBudget budget = WaterNetwork.of(plan, WaterSpec.defaults()).budget();

		assertEquals(0, budget.channelBlocks());
		assertEquals(0, budget.longestRun());
		assertFalse(budget.summary().isBlank());
	}
	@Test
	@DisplayName("Excluded nodes get no channel, and the channel still misses their bases")
	void excludedNodesAreOutsideTheNetwork() {
		for (WaterLayout layout : WaterLayout.values()) {
			PerimeterPlan plan = plan(2);
			plan.setStatus(new NodeKey(0, -2), NodeStatus.EXCLUDED);
			plan.setStatus(new NodeKey(1, -2), NodeStatus.EXCLUDED);
			plan.setStatus(new NodeKey(-2, 1), NodeStatus.EXCLUDED);
			WaterNetwork network = WaterNetwork.of(plan, WaterSpec.defaults().with(layout));

			assertFalse(network.distances().containsKey(new NodeKey(0, -2)),
					layout + " ran water to a node that is outside the perimeter");
			assertFalse(network.orphans().contains(new NodeKey(0, -2)),
					layout + " called an excluded node stranded, which it is not");
			assertEquals(plan.buildNodes().size() - 3, network.distances().size(),
					layout + " served the wrong number of nodes");
			assertTrue(network.blockedByPyramids().isEmpty(),
					layout + " dug through a base it stopped serving");
		}
	}

	@Test
	@DisplayName("A whole row of excluded nodes is left out without cutting the rest off")
	void anExcludedRowIsSkipped() {
		PerimeterPlan plan = plan(1);

		for (int i = -1; i <= 1; i++) {
			plan.setStatus(new NodeKey(i, -1), NodeStatus.EXCLUDED);
		}

		WaterNetwork network = WaterNetwork.of(plan, WaterSpec.defaults());

		assertEquals(6, network.distances().size(), "the other two rows are still served");
		assertTrue(network.blockedByPyramids().isEmpty(), "the trunk still misses the excluded row");
		assertTrue(network.disconnected().isEmpty(), "and everything served still reaches the middle");
	}

}
