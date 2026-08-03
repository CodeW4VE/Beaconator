package xyz.w4ve.beaconator.model.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xyz.w4ve.beaconator.model.GridExtents;
import xyz.w4ve.beaconator.model.PerimeterPlan;

/**
 * The water lines as a document you edit, rather than a calculation you look at.
 *
 * <p>What matters here is that a run somebody drew and a run the fishbone laid are counted the same
 * way. The moment those two answer to different code, the numbers on the screen stop being about
 * what is going to be dug.
 */
class WaterPlanTest {
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
	@DisplayName("A generated network measures the same through the document as it does on its own")
	void generatedIsMeasuredTheSame() {
		PerimeterPlan plan = plan(2);
		WaterPlan water = plan.water();
		water.generate(plan);

		WaterNetwork direct = WaterNetwork.of(plan, water.spec());
		WaterNetwork document = water.network(plan);

		assertEquals(direct.channelBlocks(), document.channelBlocks());
		assertEquals(direct.segments().size(), document.segments().size());
		assertTrue(document.orphans().isEmpty(), "a generated network serves every node");
		assertTrue(document.disconnected().isEmpty(), "a generated network is one piece");
		assertEquals(direct.longestRun(), document.longestRun(),
				"the trip is the same whichever way it is measured");
	}

	@Test
	@DisplayName("Erasing a row's spine leaves its nodes without a line, and says so")
	void erasingStrandsTheRow() {
		PerimeterPlan plan = plan(1);
		WaterPlan water = plan.water();
		water.generate(plan);
		int served = water.network(plan).distances().size();

		// The spine of the northern row, picked by the block a node of that row sits next to.
		WaterSegment spine = water.runAt(0, -101 + 5 + 1);
		assertNotNull(spine, "the row the nodes are on has a spine");
		assertTrue(water.removeAt(spine.x1(), spine.z1()));

		WaterNetwork after = water.network(plan);
		assertFalse(after.orphans().isEmpty(), "the row it served is now on its own");
		assertTrue(after.distances().size() < served, "fewer nodes are reached than before");
	}

	@Test
	@DisplayName("A run drawn by hand serves the node it passes")
	void drawnRunsCount() {
		PerimeterPlan plan = plan(0);
		WaterPlan water = plan.water();

		// One node at the origin, its base spanning x -4..4. A channel one block clear of it, run
		// out to where the sorter is, is the whole network a single node needs.
		water.setSpec(water.spec().drainingAt(60, 0));
		water.add(new WaterSegment(5, 0, 60, 0, WaterSegment.Kind.SPINE));

		WaterNetwork network = water.network(plan);
		assertTrue(network.orphans().isEmpty(), "the node is served by the run beside it");
		assertEquals(1, network.distances().size());
		assertTrue(water.edited(), "drawing marks the plan as touched by hand");
	}

	@Test
	@DisplayName("A run that misses the pyramid entirely leaves the node an orphan")
	void aRunThatMissesServesNobody() {
		PerimeterPlan plan = plan(0);
		WaterPlan water = plan.water();
		water.add(new WaterSegment(40, 40, 90, 40, WaterSegment.Kind.SPINE));

		assertEquals(1, water.network(plan).orphans().size());
	}

	@Test
	@DisplayName("Generating throws away what was drawn, and only then")
	void generatingReplacesDrawing() {
		PerimeterPlan plan = plan(1);
		WaterPlan water = plan.water();
		water.add(new WaterSegment(5, 0, 60, 0, WaterSegment.Kind.SPINE));
		assertTrue(water.edited());

		water.generate(plan);
		assertFalse(water.edited(), "a freshly generated network is nobody's hand work yet");
		assertFalse(water.isEmpty());

		water.clear();
		assertTrue(water.isEmpty());
	}

	@Test
	@DisplayName("Every change bumps the revision, so a cache cannot show yesterday's network")
	void everyChangeIsVisibleToACache() {
		PerimeterPlan plan = plan(0);
		WaterPlan water = plan.water();
		int start = water.revision();

		water.add(new WaterSegment(5, 0, 60, 0, WaterSegment.Kind.SPINE));
		assertTrue(water.revision() > start);
		int afterAdd = water.revision();

		water.setSpec(water.spec().drainingAt(60, 0));
		assertTrue(water.revision() > afterAdd);
		int afterSpec = water.revision();

		water.removeAt(30, 0);
		assertTrue(water.revision() > afterSpec);
	}
	@Test
	@DisplayName("Erasing takes out the stretch between junctions, not the whole run")
	void erasingStopsAtTheJunctions() {
		PerimeterPlan plan = plan(0);
		WaterPlan water = plan.water();
		// A hundred block spine crossed by a trunk at x=50.
		water.add(new WaterSegment(0, 0, 100, 0, WaterSegment.Kind.SPINE));
		water.add(new WaterSegment(50, -20, 50, 20, WaterSegment.Kind.TRUNK));

		assertTrue(water.removeAt(20, 0), "there is a run under there");

		// The west half went, up to and including the crossing; the east half and the trunk stayed.
		assertEquals(2, water.runs().size());
		assertNull(water.runAt(20, 0), "the stretch that was clicked is gone");
		assertNotNull(water.runAt(80, 0), "the far side of the junction is still there");
		assertNotNull(water.runAt(50, 0), "the junction block itself belongs to the trunk");
		assertNotNull(water.runAt(50, 15), "the trunk is untouched");
	}

	@Test
	@DisplayName("A run that crosses nothing is erased whole")
	void aLoneRunGoesEntirely() {
		PerimeterPlan plan = plan(0);
		WaterPlan water = plan.water();
		water.add(new WaterSegment(0, 0, 100, 0, WaterSegment.Kind.SPINE));

		assertTrue(water.removeAt(20, 0));
		assertTrue(water.isEmpty());
	}

	@Test
	@DisplayName("Erasing between two junctions leaves both ends behind")
	void themiddleStretchOnly() {
		PerimeterPlan plan = plan(0);
		WaterPlan water = plan.water();
		water.add(new WaterSegment(0, 0, 100, 0, WaterSegment.Kind.SPINE));
		water.add(new WaterSegment(30, -10, 30, 10, WaterSegment.Kind.TRUNK));
		water.add(new WaterSegment(70, -10, 70, 10, WaterSegment.Kind.TRUNK));

		water.removeAt(50, 0);

		assertNotNull(water.runAt(10, 0), "west of the first junction stays");
		assertNotNull(water.runAt(90, 0), "east of the second junction stays");
		assertNull(water.runAt(50, 0), "the stretch between them is gone");
		assertEquals(4, water.runs().size());
	}

}
