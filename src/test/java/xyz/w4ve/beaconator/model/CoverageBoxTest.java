package xyz.w4ve.beaconator.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoverageBoxTest {
	@Test
	@DisplayName("Range follows 10 * level + 10, as vanilla computes it")
	void ranges() {
		assertEquals(20, CoverageBox.rangeFor(1));
		assertEquals(30, CoverageBox.rangeFor(2));
		assertEquals(40, CoverageBox.rangeFor(3));
		assertEquals(50, CoverageBox.rangeFor(4));
		assertEquals(101, CoverageBox.sideFor(4));
		assertThrows(IllegalArgumentException.class, () -> CoverageBox.rangeFor(5));
	}

	@Test
	@DisplayName("A level 4 beacon covers 101 blocks a side, centred on itself")
	void singleBeaconBox() {
		CoverageBox box = CoverageBox.forBeacon(0, 64, 0, 4);

		assertEquals(-50, box.minX());
		assertEquals(50, box.maxX());
		assertEquals(-50, box.minZ());
		assertEquals(50, box.maxZ());
		assertEquals(101, box.width());
		assertEquals(101, box.depth());
	}

	@Test
	@DisplayName("Coverage reaches range blocks down and is unbounded up")
	void verticalIsNotSymmetric() {
		CoverageBox box = CoverageBox.forBeacon(0, 64, 0, 4);

		assertEquals(14, box.minY());
		assertTrue(box.coversBlock(0, 14, 0));
		assertFalse(box.coversBlock(0, 13, 0));
		// Vanilla expands the box by a whole world height upwards.
		assertTrue(box.coversBlock(0, 5000, 0));
	}

	@Test
	@DisplayName("A group of beacons widens the box by its footprint")
	void rowBox() {
		// Five beacons are a 2x3, so along Z that is 2 wide and 3 long, not a 1x5 row.
		CoverageBox box = CoverageBox.forRow(0, 64, 0, 4, 5, RowAxis.Z);

		assertEquals(102, box.width());
		assertEquals(103, box.depth());
		assertEquals(52, box.maxZ());

		CoverageBox alongX = CoverageBox.forRow(0, 64, 0, 4, 2, RowAxis.X);
		assertEquals(102, alongX.width());
		assertEquals(101, alongX.depth());
	}

	@Test
	@DisplayName("A negative rotation hangs the group off the other side of the node")
	void negativeRotation() {
		CoverageBox forward = CoverageBox.forRow(0, 64, 0, 4, 2, RowAxis.X);
		CoverageBox backward = CoverageBox.forRow(0, 64, 0, 4, 2, RowAxis.X_NEGATIVE);

		assertEquals(forward.width(), backward.width());
		// Same box, mirrored about the node: the second beacon is at -1 rather than +1.
		assertEquals(51, forward.maxX());
		assertEquals(-51, backward.minX());
		assertEquals(50, backward.maxX());
	}

	@Test
	@DisplayName("Spacing of 2r+1 touches exactly, less overlaps, more leaves a gap")
	void spacingBehaviour() {
		CoverageBox first = CoverageBox.forBeacon(0, 64, 0, 4);
		CoverageBox exact = CoverageBox.forBeacon(101, 64, 0, 4);
		CoverageBox overlapping = CoverageBox.forBeacon(100, 64, 0, 4);
		CoverageBox gapped = CoverageBox.forBeacon(102, 64, 0, 4);

		assertFalse(first.overlaps(exact));
		assertEquals(50, first.maxX());
		assertEquals(51, exact.minX());
		assertTrue(first.overlaps(overlapping));
		assertFalse(first.overlaps(gapped));
		// The uncovered strip with a spacing of 102.
		assertEquals(1, gapped.minX() - first.maxX() - 1);
	}

	@Test
	@DisplayName("Render edges cover the full block, not its corner")
	void renderEdges() {
		CoverageBox box = CoverageBox.forBeacon(0, 64, 0, 4);

		assertEquals(-50.0, box.renderMinX());
		assertEquals(51.0, box.renderMaxX());
		assertEquals(14.0, box.renderMinY());
		assertEquals(320.0, box.renderMaxY(320.0));
	}
}
