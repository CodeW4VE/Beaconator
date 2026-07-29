package xyz.w4ve.beaconator.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PyramidCalculatorTest {
	@Test
	@DisplayName("A single beacon builds the classic square pyramid")
	void singleBeaconLayers() {
		List<PyramidLayer> layers = PyramidCalculator.layers(0, 64, 0, 4, 1, RowAxis.Z);

		assertEquals(4, layers.size());
		assertEquals(3, layers.get(0).width());
		assertEquals(3, layers.get(0).length());
		assertEquals(9, layers.get(3).width());
		assertEquals(9, layers.get(3).length());
		assertEquals(164, PyramidCalculator.totalBlocks(1, 4));
	}

	@Test
	@DisplayName("Five beacons go in a 2x3, not a row, and save 16 blocks a node")
	void fiveBeaconLayers() {
		List<PyramidLayer> layers = PyramidCalculator.layers(0, 64, 0, 4, 5, RowAxis.Z);

		// 2 across, 3 along: layer k is (2k+2) x (2k+3).
		assertEquals(4, layers.get(0).width());
		assertEquals(5, layers.get(0).length());
		assertEquals(6, layers.get(1).width());
		assertEquals(7, layers.get(1).length());
		assertEquals(8, layers.get(2).width());
		assertEquals(9, layers.get(2).length());
		assertEquals(10, layers.get(3).width());
		assertEquals(11, layers.get(3).length());
		assertEquals(20 + 42 + 72 + 110, PyramidCalculator.totalBlocks(5, 4));
		assertEquals(244, PyramidCalculator.totalBlocks(5, 4));
	}

	@Test
	@DisplayName("The footprint chosen is the cheapest one that holds the beacons")
	void footprintIsTheCheapest() {
		assertEquals(new PyramidCalculator.Footprint(1, 1), PyramidCalculator.footprint(1, 4));
		assertEquals(new PyramidCalculator.Footprint(1, 2), PyramidCalculator.footprint(2, 4));
		assertEquals(new PyramidCalculator.Footprint(1, 3), PyramidCalculator.footprint(3, 4));
		// The one that matters: four in a square, not in a row.
		assertEquals(new PyramidCalculator.Footprint(2, 2), PyramidCalculator.footprint(4, 4));
		assertEquals(new PyramidCalculator.Footprint(2, 3), PyramidCalculator.footprint(5, 4));
		assertEquals(new PyramidCalculator.Footprint(2, 3), PyramidCalculator.footprint(6, 4));

		assertEquals(216, PyramidCalculator.totalBlocks(4, 4));
		// Six is free: same 2x3 pyramid as five, one more beacon on it.
		assertEquals(PyramidCalculator.totalBlocks(5, 4), PyramidCalculator.totalBlocks(6, 4));
	}

	@Test
	@DisplayName("No arrangement of the same beacons beats the one we pick")
	void nothingBeatsTheChosenFootprint() {
		for (int beacons = 1; beacons <= PyramidCalculator.MAX_BEACONS_PER_NODE; beacons++) {
			for (int level = 1; level <= PyramidCalculator.MAX_LEVEL; level++) {
				int ours = PyramidCalculator.totalBlocks(beacons, level);

				for (int along = 1; along <= beacons; along++) {
					int across = Math.ceilDiv(beacons, along);
					int rival = 0;

					for (int depth = 1; depth <= level; depth++) {
						rival += (2 * depth + across) * (2 * depth + along);
					}

					assertTrue(ours <= rival,
							beacons + " beacons at level " + level + ": " + across + "x" + along
									+ " costs " + rival + ", we picked " + ours);
				}
			}
		}
	}

	@Test
	@DisplayName("The row axis decides which side of the pyramid stretches")
	void rowAxisStretchesTheRightSide() {
		List<PyramidLayer> alongX = PyramidCalculator.layers(0, 64, 0, 4, 5, RowAxis.X);

		// The 3 of the 2x3 runs along X now, so the base is wider than it is long.
		assertEquals(11, alongX.get(3).width());
		assertEquals(10, alongX.get(3).length());
	}

	@Test
	@DisplayName("Layers sit right under the beacons and stay centred on the row")
	void layerPositions() {
		List<PyramidLayer> layers = PyramidCalculator.layers(100, 64, 200, 4, 2, RowAxis.Z);
		PyramidLayer top = layers.get(0);

		assertEquals(63, top.y());
		assertEquals(99, top.minX());
		assertEquals(101, top.maxX());
		assertEquals(199, top.minZ());
		// One extra block because the second beacon sits at z + 1.
		assertEquals(202, top.maxZ());

		PyramidLayer base = layers.get(3);
		assertEquals(60, base.y());
		assertEquals(96, base.minX());
		assertEquals(104, base.maxX());
		assertEquals(196, base.minZ());
		assertEquals(205, base.maxZ());
		assertEquals(90, base.blockCount());
	}

	@Test
	@DisplayName("Beacons of a node line up along the row axis")
	void beaconPositions() {
		List<int[]> row = PyramidCalculator.beaconPositions(10, 64, 20, 3, RowAxis.X);

		assertEquals(3, row.size());
		assertEquals(10, row.get(0)[0]);
		assertEquals(11, row.get(1)[0]);
		assertEquals(12, row.get(2)[0]);
		assertEquals(20, row.get(2)[2]);
	}

	@Test
	@DisplayName("Layer counts for every supported combination")
	void layerCounts() {
		assertEquals(9, PyramidCalculator.blocksInLayer(1, 1, 4));
		assertEquals(12, PyramidCalculator.blocksInLayer(1, 2, 4));
		// Five beacons live in a 2x3, so the top layer is 4x5 rather than the old row's 3x7.
		assertEquals(20, PyramidCalculator.blocksInLayer(1, 5, 4));
		assertEquals(81, PyramidCalculator.blocksInLayer(4, 1, 4));
		assertEquals(90, PyramidCalculator.blocksInLayer(4, 2, 4));
		assertEquals(110, PyramidCalculator.blocksInLayer(4, 5, 4));
		assertEquals(188, PyramidCalculator.totalBlocks(2, 4));
	}

	@Test
	@DisplayName("Out of range beacon counts and levels are rejected")
	void rejectsBadInput() {
		assertThrows(IllegalArgumentException.class, () -> PyramidCalculator.totalBlocks(7, 4));
		assertThrows(IllegalArgumentException.class, () -> PyramidCalculator.totalBlocks(0, 4));
		assertThrows(IllegalArgumentException.class, () -> PyramidCalculator.totalBlocks(1, 5));
		assertThrows(IllegalArgumentException.class, () -> PyramidCalculator.totalBlocks(1, 0));
	}
}
