package xyz.w4ve.beaconator.io;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;

/**
 * Reads a schematic off disk and prints what was detected. Skipped unless a file is handed in:
 *
 * <pre>
 *   ./gradlew test -Dbeaconator.schematic=/path/to/perimeter.litematic
 * </pre>
 *
 * <p>Useful for checking the importer against a perimeter that was built by hand, which no
 * bundled fixture can stand in for.
 */
class RealSchematicTest {
	@Test
	@DisplayName("Imports a schematic handed in on the command line")
	void importsRealSchematic() throws IOException {
		String property = System.getProperty("beaconator.schematic");
		assumeTrue(property != null && !property.isBlank(), "no schematic given");

		Path path = Path.of(property);
		assumeTrue(Files.isRegularFile(path), "file not found: " + path);

		PerimeterPlan plan = LitematicIO.read(path, "imported", "minecraft:overworld", null);

		System.out.println("Detected from " + path.getFileName() + ":");
		System.out.println("  grid      " + plan.extents().columns() + " x " + plan.extents().rows()
				+ " = " + plan.extents().nodeCount() + " nodes");
		System.out.println("  centre    " + plan.centerX() + ", " + plan.beaconY() + ", " + plan.centerZ());
		System.out.println("  spacing   " + plan.spacing());
		System.out.println("  beacons   " + plan.beaconsPerNode() + " per node along " + plan.rowAxis());
		System.out.println("  level     " + plan.level());
		System.out.println("  pyramid   " + plan.pyramidBlock());
		System.out.println("  marker    " + plan.markerBlock());
		System.out.println("  states    " + plan.countByStatus(NodeStatus.PENDING) + " pending, "
				+ plan.countByStatus(NodeStatus.EXCLUDED) + " excluded, "
				+ plan.countByStatus(NodeStatus.REMOVED) + " removed");

		for (Map.Entry<String, Integer> entry : plan.tally().counts().entrySet()) {
			System.out.println("  " + entry.getKey() + ": " + entry.getValue());
		}

		assertTrue(plan.extents().nodeCount() > 0);
		assertTrue(plan.tally().total() > 0);
	}
}
