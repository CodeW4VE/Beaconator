package xyz.w4ve.beaconator.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.w4ve.beaconator.model.GridExtents;
import xyz.w4ve.beaconator.model.GridNode;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.model.RowAxis;

/**
 * Round trips a plan through a real .litematic file, which is the only way to know the bit
 * packing and the region layout are right.
 */
class LitematicIOTest {
	@TempDir
	Path folder;

	private static Set<String> blocksOf(PerimeterPlan plan) {
		Set<String> blocks = new HashSet<>();

		for (GridNode node : plan.nodes()) {
			plan.forEachBlock(node, (x, y, z, id) -> blocks.add(x + "," + y + "," + z + "=" + id));
		}

		return blocks;
	}

	private static PerimeterPlan samplePlan() {
		PerimeterPlan plan = new PerimeterPlan("Test Perimeter", "minecraft:overworld", 604, 64, 704);
		plan.setLevel(4);
		plan.setSpacing(100);
		plan.setBeaconsPerNode(2);
		plan.setRowAxis(RowAxis.Z);
		plan.setPyramidBlock("minecraft:emerald_block");
		plan.setExtents(new GridExtents(-2, 2, -2, 3));
		plan.setStatus(new NodeKey(-2, -2), NodeStatus.EXCLUDED);
		plan.setStatus(new NodeKey(-1, -2), NodeStatus.EXCLUDED);
		plan.setStatus(new NodeKey(2, 3), NodeStatus.REMOVED);
		return plan;
	}

	@Test
	@DisplayName("A plan written to a schematic reads back as the same blocks")
	void roundTrip() throws IOException {
		PerimeterPlan original = samplePlan();
		Path file = folder.resolve("test.litematic");

		LitematicIO.write(file, original, "tvtvirus");
		assertTrue(Files.size(file) > 0);

		PerimeterPlan reloaded = LitematicIO.read(file, "Test Perimeter", "minecraft:overworld", null);

		assertEquals(100, reloaded.spacing());
		assertEquals(2, reloaded.beaconsPerNode());
		assertEquals(4, reloaded.level());
		assertEquals(RowAxis.Z, reloaded.rowAxis());
		assertEquals("minecraft:emerald_block", reloaded.pyramidBlock());
		assertEquals("minecraft:black_stained_glass", reloaded.markerBlock());
		assertEquals(2, reloaded.countByStatus(NodeStatus.EXCLUDED));
		assertEquals(original.tally().counts(), reloaded.tally().counts());
		assertEquals(blocksOf(original), blocksOf(reloaded));
	}

	@Test
	@DisplayName("Larger palettes and single beacon nodes survive too")
	void roundTripSingleBeacon() throws IOException {
		PerimeterPlan original = new PerimeterPlan("Solo", "minecraft:overworld", 0, 70, 0);
		original.setSpacing(101);
		original.setBeaconsPerNode(1);
		original.setRing(1);
		original.setPyramidBlock("minecraft:iron_block");
		original.setStatus(new NodeKey(1, 1), NodeStatus.EXCLUDED);

		Path file = folder.resolve("solo.litematic");
		LitematicIO.write(file, original, "tvtvirus");
		PerimeterPlan reloaded = LitematicIO.read(file, "Solo", "minecraft:overworld", null);

		assertEquals(blocksOf(original), blocksOf(reloaded));
		assertEquals(1, reloaded.beaconsPerNode());
		assertEquals(101, reloaded.spacing());
	}

	@Test
	@DisplayName("A schematic without beacons is refused with a clear reason")
	void refusesNonPerimeters() throws IOException {
		PerimeterPlan plan = samplePlan();
		Path file = folder.resolve("empty.litematic");
		plan.setExtents(GridExtents.ring(0));
		plan.setStatus(new NodeKey(0, 0), NodeStatus.REMOVED);

		assertThrows(IOException.class, () -> LitematicIO.write(file, plan, "tvtvirus"));
	}
}
