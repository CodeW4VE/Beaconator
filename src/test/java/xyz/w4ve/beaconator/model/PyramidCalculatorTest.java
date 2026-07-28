package xyz.w4ve.beaconator.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
	@DisplayName("Five beacons in a row stretch the pyramid to 9x13")
	void fiveBeaconLayers() {
		List<PyramidLayer> layers = PyramidCalculator.layers(0, 64, 0, 4, 5, RowAxis.Z);

		assertEquals(3, layers.get(0).width());
		assertEquals(7, layers.get(0).length());
		assertEquals(5, layers.get(1).width());
		assertEquals(9, layers.get(1).length());
		assertEquals(7, layers.get(2).width());
		assertEquals(11, layers.get(2).length());
		assertEquals(9, layers.get(3).width());
		assertEquals(13, layers.get(3).length());
		assertEquals(21 + 45 + 77 + 117, PyramidCalculator.totalBlocks(5, 4));
		assertEquals(260, PyramidCalculator.totalBlocks(5, 4));
	}

	@Test
	@DisplayName("The row axis decides which side of the pyramid stretches")
	void rowAxisStretchesTheRightSide() {
		List<PyramidLayer> alongX = PyramidCalculator.layers(0, 64, 0, 4, 5, RowAxis.X);

		assertEquals(13, alongX.get(3).width());
		assertEquals(9, alongX.get(3).length());
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
		assertEquals(9, PyramidCalculator.blocksInLayer(1, 1));
		assertEquals(12, PyramidCalculator.blocksInLayer(1, 2));
		assertEquals(21, PyramidCalculator.blocksInLayer(1, 5));
		assertEquals(81, PyramidCalculator.blocksInLayer(4, 1));
		assertEquals(90, PyramidCalculator.blocksInLayer(4, 2));
		assertEquals(117, PyramidCalculator.blocksInLayer(4, 5));
		assertEquals(188, PyramidCalculator.totalBlocks(2, 4));
	}

	@Test
	@DisplayName("Out of range beacon counts and levels are rejected")
	void rejectsBadInput() {
		assertThrows(IllegalArgumentException.class, () -> PyramidCalculator.totalBlocks(6, 4));
		assertThrows(IllegalArgumentException.class, () -> PyramidCalculator.totalBlocks(0, 4));
		assertThrows(IllegalArgumentException.class, () -> PyramidCalculator.totalBlocks(1, 5));
		assertThrows(IllegalArgumentException.class, () -> PyramidCalculator.totalBlocks(1, 0));
	}
}
