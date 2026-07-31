package xyz.w4ve.beaconator.io;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.model.water.WaterBudget;
import xyz.w4ve.beaconator.model.water.WaterLayout;
import xyz.w4ve.beaconator.model.water.WaterNetwork;
import xyz.w4ve.beaconator.model.water.WaterSpec;

/**
 * Prices the water network on a real saved plan. Skipped unless one is handed in:
 *
 * <pre>
 *   ./gradlew test -Dbeaconator.plan=config/beaconator/&lt;world&gt;/&lt;name&gt;.json
 * </pre>
 *
 * <p>A made up grid says nothing useful here, because what the network costs depends entirely on
 * which nodes survived the editing. This prints the shapes side by side so the choice is made on
 * the perimeter that is actually being built.
 */
class RealPlanBudgetTest {
	@Test
	@DisplayName("Prices the water network on a plan handed in on the command line")
	void pricesRealPlan() throws IOException {
		String property = System.getProperty("beaconator.plan");
		assumeTrue(property != null && !property.isBlank(), "no plan given");

		Path path = Path.of(property);
		assumeTrue(Files.isRegularFile(path), "file not found: " + path);

		PerimeterPlan plan = PlanStore.fromJson(Files.readString(path, StandardCharsets.UTF_8),
				path.getFileName().toString().replace(".json", ""));

		System.out.println("Plan " + plan.name() + " at " + plan.centerX() + ", " + plan.beaconY()
				+ ", " + plan.centerZ());
		System.out.println("  grid      " + plan.extents().columns() + " x " + plan.extents().rows()
				+ " = " + plan.extents().nodeCount() + " nodes, spacing " + plan.spacing());
		System.out.println("  live      " + plan.buildNodes().size() + " ("
				+ plan.countByStatus(NodeStatus.PENDING) + " pending, "
				+ plan.countByStatus(NodeStatus.PLACED) + " placed, "
				+ plan.countByStatus(NodeStatus.EXCLUDED) + " excluded, "
				+ plan.countByStatus(NodeStatus.REMOVED) + " removed)");
		System.out.println();
		System.out.printf("  %-22s %9s %9s %9s %9s %8s %8s %7s %7s%n",
				"shape", "channel", "ice", "shulkers", "buckets", "longest", "average", "turns",
				"ideal");

		for (String[] variant : new String[][] {
			{"fishbone, every row", "FISHBONE", "1", WaterSpec.PACKED_ICE},
			{"fishbone, every 2nd", "FISHBONE", "2", WaterSpec.PACKED_ICE},
			{"fishbone, every 3rd", "FISHBONE", "3", WaterSpec.PACKED_ICE},
			{"tree, every row", "TREE", "1", WaterSpec.PACKED_ICE},
			{"fishbone, blue ice", "FISHBONE", "1", WaterSpec.BLUE_ICE}
		}) {
			WaterSpec spec = WaterSpec.defaults()
					.with(WaterLayout.valueOf(variant[1]))
					.withRowStep(Integer.parseInt(variant[2]))
					.withIce(variant[3]);
			WaterNetwork network = WaterNetwork.of(plan, spec);
			WaterBudget budget = network.budget();

			System.out.printf("  %-22s %9d %9d %9d %9d %8d %8d %7d %7d%n",
					variant[0],
					budget.channelBlocks(),
					budget.iceToMine(),
					WaterBudget.shulkers(budget.iceToMine()),
					budget.waterSources(),
					budget.trip().longest(),
					budget.trip().average(),
					budget.trip().maxTurns(),
					budget.trip().idealLongest());

			if (budget.nodesOrphaned() > 0) {
				System.out.println("       " + budget.nodesOrphaned() + " nodes left without a line");
			}

			assertTrue(network.blockedByPyramids().isEmpty(),
					variant[0] + " digs through " + network.blockedByPyramids().size() + " pyramid bases");
			assertTrue(network.disconnected().isEmpty(),
					variant[0] + " leaves " + network.disconnected().size() + " nodes on an island");
		}

		System.out.println();
		System.out.println("  ice is what has to be mined, not what is placed: packed ice is nine");
		System.out.println("  blocks of ice each, blue ice is eighty one.");
	}
}
