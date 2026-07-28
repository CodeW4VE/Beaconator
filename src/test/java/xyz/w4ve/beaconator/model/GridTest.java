package xyz.w4ve.beaconator.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GridTest {
	@Test
	@DisplayName("Rings step 1, 9, 25, 49 nodes, never 4")
	void ringSizes() {
		assertEquals(1, GridExtents.ring(0).nodeCount());
		assertEquals(9, GridExtents.ring(1).nodeCount());
		assertEquals(25, GridExtents.ring(2).nodeCount());
		assertEquals(49, GridExtents.ring(3).nodeCount());
		assertEquals(81, GridExtents.ring(4).nodeCount());

		for (int n = 0; n <= 10; n++) {
			assertEquals((2 * n + 1) * (2 * n + 1), GridExtents.ring(n).nodeCount());
		}
	}

	@Test
	@DisplayName("A ring adds 8n nodes over the previous one")
	void ringIncrements() {
		for (int n = 1; n <= 10; n++) {
			int added = GridExtents.ring(n).nodeCount() - GridExtents.ring(n - 1).nodeCount();
			assertEquals(8 * n, added);
		}
	}

	@Test
	@DisplayName("Nodes sit at exact multiples of the spacing from the centre")
	void nodePositions() {
		List<GridNode> nodes = GridGenerator.nodes(1000, 64, -500, 100, GridExtents.ring(1));

		assertEquals(9, nodes.size());
		GridNode first = nodes.get(0);
		assertEquals(-1, first.i());
		assertEquals(-1, first.j());
		assertEquals(900, first.x());
		assertEquals(-600, first.z());

		GridNode centre = GridGenerator.nodeAt(1000, 64, -500, 100, 0, 0);
		assertEquals(1000, centre.x());
		assertEquals(-500, centre.z());
		assertEquals(64, centre.y());
	}

	@Test
	@DisplayName("Nearest node rounds to the closest grid intersection")
	void nearestNode() {
		assertEquals(new NodeKey(0, 0), GridGenerator.nearestKey(0, 0, 100, 40, -40));
		assertEquals(new NodeKey(1, 0), GridGenerator.nearestKey(0, 0, 100, 60, 10));
		assertEquals(new NodeKey(-2, 3), GridGenerator.nearestKey(0, 0, 100, -190, 310));
	}

	@Test
	@DisplayName("Sides can be grown on their own for rectangular grids")
	void independentSides() {
		GridExtents extents = GridExtents.ring(2)
				.expand(GridSide.EAST, 1)
				.expand(GridSide.NORTH, 3);

		assertEquals(6, extents.columns());
		assertEquals(8, extents.rows());
		assertEquals(48, extents.nodeCount());
		assertEquals(-1, extents.asRing(), "no longer a square ring");
	}

	@Test
	@DisplayName("Shrinking a side never eats the centre node")
	void shrinkStopsAtCentre() {
		GridExtents extents = GridExtents.ring(2).expand(GridSide.EAST, -5);

		assertEquals(0, extents.maxI());
		assertEquals(3, extents.columns());
	}

	@Test
	@DisplayName("The grid always contains its centre")
	void extentsMustContainCentre() {
		assertThrows(IllegalArgumentException.class, () -> new GridExtents(1, 2, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> GridExtents.ring(-1));
	}

	@Test
	@DisplayName("Node keys survive the round trip through their saved form")
	void nodeKeyParsing() {
		assertEquals(new NodeKey(-6, 8), NodeKey.parse(new NodeKey(-6, 8).toString()));
	}
}
