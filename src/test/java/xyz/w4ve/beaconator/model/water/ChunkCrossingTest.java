package xyz.w4ve.beaconator.model.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xyz.w4ve.beaconator.model.GridExtents;
import xyz.w4ve.beaconator.model.PerimeterPlan;

/**
 * The two things about the fittings that are not a matter of taste: everything flows to the drain,
 * and a chunk border carries moving water and nothing else.
 *
 * <p>Both come from the same place. An item in flowing water is pushed every tick; an item on bare
 * ice keeps only the speed it already had, and a chunk that unloads and comes back does not give
 * it back. So a current that points the wrong way, a stretch with nothing pushing it, and a plate
 * on a border are all the same bug wearing different hats, and they get tests that walk a real
 * network block by block rather than a couple of hand picked runs.
 */
class ChunkCrossingTest {
	private static PerimeterPlan plan(int ring) {
		PerimeterPlan plan = new PerimeterPlan("test", "minecraft:overworld", 0, -55, 0);
		plan.setExtents(GridExtents.ring(ring));
		plan.setAutoSpacing(false);
		plan.setSpacing(101);
		plan.setLevel(4);
		plan.setBeaconsPerNode(2);
		return plan;
	}

	/** A plan with its network laid out, which is what the fittings are worked out from. */
	private static WaterNetwork network(PerimeterPlan plan) {
		plan.water().generate(plan);
		return plan.water().network(plan);
	}

	private static Set<Long> keysOf(List<WaterFittings.Fitting> fittings) {
		Set<Long> keys = new HashSet<>();

		for (WaterFittings.Fitting fitting : fittings) {
			keys.add(fitting.key());
		}

		return keys;
	}

	private static Set<Long> marks(List<int[]> blocks) {
		Set<Long> keys = new HashSet<>();

		for (int[] block : blocks) {
			keys.add(ChunkCrossings.key(block[0], block[1]));
		}

		return keys;
	}

	// ------------------------------------------------------------- the borders

	@Test
	@DisplayName("A run counts a border every sixteen blocks, wherever it starts")
	void findsTheBorders() {
		// x = 0 to 47 is three chunks: two steps between them.
		List<ChunkCrossings.Crossing> crossings = ChunkCrossings.stepsAcross(
				List.of(new WaterSegment(0, 8, 47, 8, WaterSegment.Kind.SPINE)));

		assertEquals(2, crossings.size());
		assertEquals(15, crossings.get(0).outX(), "the last block of the first chunk");
		assertEquals(16, crossings.get(0).inX(), "the first block of the second");
		assertEquals(31, crossings.get(1).outX());
	}

	@Test
	@DisplayName("A run inside one chunk crosses nothing")
	void shortRunHasNoBorders() {
		assertTrue(ChunkCrossings.stepsAcross(
				List.of(new WaterSegment(1, 1, 14, 1, WaterSegment.Kind.SPINE))).isEmpty());
	}

	@Test
	@DisplayName("Negative coordinates split where the game splits them, not at zero")
	void negativeChunksLineUp() {
		// -17 is in chunk -2 and -16 is in chunk -1: the floor division, not truncation towards
		// zero, which is the bug this test exists to catch.
		List<ChunkCrossings.Crossing> crossings = ChunkCrossings.stepsAcross(
				List.of(new WaterSegment(-20, 0, -10, 0, WaterSegment.Kind.SPINE)));

		assertEquals(1, crossings.size());
		assertEquals(-17, crossings.get(0).outX());
		assertEquals(-16, crossings.get(0).inX());
	}

	@Test
	@DisplayName("Two runs that meet across a border are found, even though neither steps over it")
	void bordersBetweenRunsAreFound() {
		// Drawn by hand: one run stops at the last block of a chunk, the next starts at the first
		// block of the next one. Nothing walks that step, so following the runs would miss it.
		List<WaterSegment> runs = List.of(new WaterSegment(4, 8, 15, 8, WaterSegment.Kind.SPINE),
				new WaterSegment(16, 8, 40, 8, WaterSegment.Kind.SPINE));

		assertTrue(ChunkCrossings.stepsAcross(runs).stream()
				.noneMatch(crossing -> crossing.outX() == 15), "no run steps over it");
		assertEquals(2, ChunkCrossings.seams(runs).size(), "but the channel touches two borders");
	}

	// --------------------------------------------------------- the whole thing

	@Test
	@DisplayName("Every fitting pushes towards the drain, never away from it")
	void everythingFlowsToTheDrain() {
		PerimeterPlan plan = plan(3);
		WaterNetwork network = network(plan);
		WaterFittings fittings = WaterFittings.of(network);
		Map<Long, Integer> distance = network.blockDistances();

		// The one that would have caught the whole class of bug: a fitting whose downstream step
		// does not get closer to the drain is a current pointing the wrong way.
		for (WaterFittings.Fitting fitting : fittings.sources()) {
			assertDownhill(distance, fitting, "source");
		}

		for (WaterFittings.Fitting fitting : fittings.stops()) {
			assertDownhill(distance, fitting, "plate");
		}
	}

	private static void assertDownhill(Map<Long, Integer> distance, WaterFittings.Fitting fitting,
			String what) {
		String where = what + " at " + fitting.x() + "," + fitting.z();
		Integer here = distance.get(fitting.key());

		assertTrue(fitting.dx() != 0 || fitting.dz() != 0,
				where + " has no direction at all, and only the drain is allowed that");

		Integer next = distance.get(
				ChunkCrossings.key(fitting.x() + fitting.dx(), fitting.z() + fitting.dz()));

		assertTrue(here != null && next != null && next < here,
				where + " points away from the drain: " + here + " to " + next);
	}

	@Test
	@DisplayName("Both sides of every border carry moving water, and never a plate")
	void everyBorderGetsASource() {
		PerimeterPlan plan = plan(2);
		WaterNetwork network = network(plan);
		WaterFittings fittings = WaterFittings.of(network);
		Set<Long> wet = fittings.wet();
		Map<Long, Long> downstream = network.downstream();
		int checked = 0;

		// Asked of the flow rather than of the runs: the block that leaves a chunk is whichever
		// one has its downstream neighbour on the other side of a border.
		for (Map.Entry<Long, Long> step : downstream.entrySet()) {
			long from = step.getKey();
			long to = step.getValue();

			if (!ChunkCrossings.separates(ChunkCrossings.x(from), ChunkCrossings.z(from),
					ChunkCrossings.x(to), ChunkCrossings.z(to))) {
				continue;
			}

			assertTrue(wet.contains(from) && wet.contains(to),
					"the border at " + ChunkCrossings.x(from) + "," + ChunkCrossings.z(from)
							+ " is not under moving water on both sides");
			checked++;
		}

		assertTrue(checked > 20, "and this perimeter crosses plenty of borders: " + checked);
	}

	@Test
	@DisplayName("No plate of a whole perimeter sits on a border")
	void platesKeepOffTheBorders() {
		PerimeterPlan plan = plan(3);
		WaterNetwork network = network(plan);
		WaterFittings fittings = WaterFittings.of(network);
		Set<Long> guarded = ChunkCrossings.guarded(network.segments());

		assertFalse(guarded.isEmpty(), "a perimeter this size crosses plenty of chunks");
		assertTrue(fittings.crossings() > 100, "and it counts them: " + fittings.crossings());

		for (WaterFittings.Fitting stop : fittings.stops()) {
			assertFalse(guarded.contains(stop.key()),
					"a plate at " + stop.x() + "," + stop.z() + " sits on a chunk border");
		}
	}

	@Test
	@DisplayName("No stretch of channel is further past a source than the spacing allows")
	void nothingRunsDry() {
		PerimeterPlan plan = plan(3);
		WaterNetwork network = network(plan);
		WaterSpec spec = network.spec();
		Set<Long> sources = keysOf(WaterFittings.of(network).sources());
		Map<Long, Long> downstream = network.downstream();

		// Walked the way the water goes: from every block, count the steps back upstream to the
		// nearest source. A gap longer than the spacing is a stretch with nothing pushing in it.
		for (long block : network.blocks()) {
			if (!downstream.containsKey(block) && !sources.contains(block)) {
				continue;
			}

			int since = 0;
			long at = block;

			while (!sources.contains(at) && since <= spec.sourceEvery()) {
				Long behind = upstreamOf(at, downstream);

				if (behind == null) {
					break;
				}

				at = behind;
				since++;
			}

			assertTrue(sources.contains(at) || since <= spec.sourceEvery(),
					"the channel at " + ChunkCrossings.x(block) + "," + ChunkCrossings.z(block)
							+ " is " + since + " blocks past a source");
		}
	}

	private static Long upstreamOf(long block, Map<Long, Long> downstream) {
		int x = ChunkCrossings.x(block);
		int z = ChunkCrossings.z(block);

		for (long neighbour : new long[] {ChunkCrossings.key(x + 1, z), ChunkCrossings.key(x - 1, z),
				ChunkCrossings.key(x, z + 1), ChunkCrossings.key(x, z - 1)}) {
			Long theirs = downstream.get(neighbour);

			if (theirs != null && theirs == block) {
				return neighbour;
			}
		}

		return null;
	}

	@Test
	@DisplayName("Every source has something cutting the current behind it")
	void everySourceIsCutOffBehind() {
		PerimeterPlan plan = plan(2);
		WaterNetwork network = network(plan);
		WaterFittings fittings = WaterFittings.of(network);
		Map<Long, Long> downstream = network.downstream();
		Set<Long> plates = keysOf(fittings.stops());
		int cut = 0;
		int ends = 0;

		for (WaterFittings.Fitting source : fittings.sources()) {
			Long behind = upstreamOf(source.key(), downstream);

			if (behind == null) {
				ends++;
				continue;
			}

			// The plate may have walked further back than one block to get off a border.
			boolean found = false;
			long at = behind;

			for (int back = 0; back < 16 && !found; back++) {
				found = plates.contains(at);
				Long next = upstreamOf(at, downstream);

				if (next == null) {
					break;
				}

				at = next;
			}

			if (found) {
				cut++;
			}
		}

		assertTrue(cut > fittings.sources().size() - ends - 5,
				"most sources should be cut off behind: " + cut + " of "
						+ (fittings.sources().size() - ends));
	}

	@Test
	@DisplayName("No block of channel is left empty: water in it or a plate on it, everywhere")
	void nothingIsLeftEmpty() {
		PerimeterPlan plan = plan(3);
		WaterNetwork network = network(plan);
		WaterFittings fittings = WaterFittings.of(network);
		Set<Long> wet = fittings.wet();
		Set<Long> plates = keysOf(fittings.stops());

		// His rule, and the one an item cares about: one bare block of ice in the middle of a
		// working channel is the hardest thing in the network to find once drops go missing.
		for (long block : network.blocks()) {
			assertTrue(wet.contains(block) || plates.contains(block),
					"nothing at all at " + ChunkCrossings.x(block) + "," + ChunkCrossings.z(block));
		}
	}

	@Test
	@DisplayName("Two sources never touch, so no pair of them can pool together")
	void sourcesNeverTouch() {
		PerimeterPlan plan = plan(3);
		Set<Long> sources = keysOf(WaterFittings.of(network(plan)).sources());

		for (long source : sources) {
			int x = ChunkCrossings.x(source);
			int z = ChunkCrossings.z(source);

			for (long neighbour : new long[] {ChunkCrossings.key(x + 1, z),
					ChunkCrossings.key(x, z + 1)}) {
				assertFalse(sources.contains(neighbour),
						"two sources touching at " + x + "," + z + ": that is a pool, not a current");
			}
		}
	}

	// ------------------------------------------------------------ the schematic

	@Test
	@DisplayName("A plate is part of the schematic, so assisted placement can put one down")
	void platesAreInTheSchematic() {
		PerimeterPlan plan = plan(2);
		WaterPlan water = plan.water();
		water.generate(plan);

		WaterFittings.Fitting plate = water.fittings(plan).stops().get(0);
		int y = water.spec().waterY();

		assertEquals("minecraft:stone_pressure_plate", plan.blockAt(plate.x(), y, plate.z()),
				"the plan wants a plate here, so easy place has to know about it");
		assertNull(plan.blockAt(plate.x(), y + 1, plate.z()), "and only on the channel's own layer");
		assertTrue(water.plateAt(plan, plate.x(), y, plate.z()));
	}

	@Test
	@DisplayName("An empty network puts nothing in the schematic")
	void noWaterNoPlates() {
		PerimeterPlan plan = plan(1);
		assertFalse(plan.water().plateAt(plan, 0, WaterSpec.BOTTOM_LAYER + 1, 0));
	}

	@Test
	@DisplayName("Editing the runs moves the plates the schematic knows about")
	void schematicFollowsTheRuns() {
		PerimeterPlan plan = plan(2);
		WaterPlan water = plan.water();
		water.generate(plan);

		WaterFittings.Fitting plate = water.fittings(plan).stops().get(0);
		int y = water.spec().waterY();
		assertTrue(water.plateAt(plan, plate.x(), y, plate.z()));

		// The cache behind this is keyed on the revision, and erasing bumps it. Get that wrong and
		// easy place goes on offering plates for a run that is not there any more.
		water.clear();
		assertFalse(water.plateAt(plan, plate.x(), y, plate.z()));
	}

	// ----------------------------------------------------------------- the bill

	@Test
	@DisplayName("The bill counts the fittings that are actually placed")
	void budgetCountsWhatIsPlaced() {
		PerimeterPlan plan = plan(2);
		WaterPlan water = plan.water();
		water.generate(plan);

		WaterFittings fittings = water.fittings(plan);
		WaterBudget budget = water.network(plan).budget();

		assertEquals(fittings.sources().size(), budget.waterSources(),
				"the bill and the marks in the world have to be the same buckets");
		assertEquals(fittings.stops().size(), budget.flowStops(),
				"and the same plates");
	}

	@Test
	@DisplayName("A corner on a border is reported and left where it is")
	void cornersAreReportedNotMoved() {
		// The spine ends at x = 16, the first block of a chunk, and the trunk turns there.
		List<WaterSegment> runs = List.of(new WaterSegment(4, 8, 16, 8, WaterSegment.Kind.SPINE),
				new WaterSegment(16, 8, 16, 60, WaterSegment.Kind.TRUNK));

		assertTrue(marks(ChunkCrossings.cornersOnBorders(runs))
				.contains(ChunkCrossings.key(16, 8)), "the corner on the border is flagged");
		assertEquals(1, ChunkCrossings.cornersOnBorders(runs).size(),
				"and only that one, not every end of every run");
	}
}
