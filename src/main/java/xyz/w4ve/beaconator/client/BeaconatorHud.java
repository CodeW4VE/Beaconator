package xyz.w4ve.beaconator.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import xyz.w4ve.beaconator.client.scan.NodeScan;
import xyz.w4ve.beaconator.client.scan.ScanCache;
import xyz.w4ve.beaconator.config.BeaconatorConfig;
import xyz.w4ve.beaconator.model.GridNode;
import xyz.w4ve.beaconator.model.MaterialTally;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.client.net.ClientSync;

/** Small readout of the active plan, so the numbers are on screen while you edit. */
public final class BeaconatorHud {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFAAAAAA;
	private static final int RED = 0xFFFF6060;
	private static final int GOLD = 0xFFFFC24A;

	private BeaconatorHud() {
	}

	public static void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		PerimeterPlan plan = PlanManager.plan();
		BeaconatorConfig config = BeaconatorConfig.get();

		if (plan == null || !config.enabled || !config.showHud || mc.options.hideGui
				|| mc.screen != null) {
			return;
		}

		List<String> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();

		String title = "Beaconator: " + plan.name();

		// Worth a word on screen: on a shared plan every click you make is seen by everyone else.
		if (ClientSync.shared()) {
			title += " [" + Lang.t("hud.shared") + "]";
		}

		if (PlanManager.editMode()) {
			title += " [" + Lang.t("hud.edit") + "]";
		}

		lines.add(title);
		colors.add(PlanManager.editMode() ? GOLD : WHITE);

		if (!PlanManager.inPlanDimension()) {
			lines.add(Lang.t("wrong_dimension", plan.dimension()));
			colors.add(RED);
		}

		int ring = plan.ring();
		lines.add(ring >= 0
				? Lang.t("hud.grid", plan.extents().columns(), plan.extents().rows(), ring)
				: Lang.t("hud.grid_custom", plan.extents().columns(), plan.extents().rows()));
		colors.add(GREY);

		lines.add(Lang.t("hud.setup", plan.beaconsPerNode(), plan.level(), plan.spacing())
				+ (plan.autoSpacing() ? Lang.t("hud.auto") : ""));
		colors.add(GREY);

		int gap = Math.max(plan.gapAcrossRow(), plan.gapAlongRow());

		if (gap > 0) {
			lines.add(Lang.t("grid.gap", gap));
			colors.add(RED);
		} else if (gap < 0) {
			lines.add(Lang.t("grid.overlap", -gap));
			colors.add(GREY);
		} else {
			lines.add(Lang.t("grid.fits"));
			colors.add(GREY);
		}

		lines.add(Lang.t("hud.states", plan.countByStatus(NodeStatus.PENDING),
				plan.countByStatus(NodeStatus.PLACED), plan.countByStatus(NodeStatus.EXCLUDED),
				plan.countByStatus(NodeStatus.REMOVED)));
		colors.add(GREY);

		NodeKey hovered = PlanManager.hovered();

		if (hovered != null) {
			GridNode node = plan.nodeAt(hovered);
			StringBuilder line = new StringBuilder(Lang.t("hud.node", hovered.toString(),
					node.x(), node.y(), node.z()))
					.append(" [").append(Lang.state(plan.statusAt(hovered))).append("]");

			NodeScan scan = ScanCache.get(hovered);

			if (scan != null && scan.loaded()) {
				line.append(' ').append(scan.found()).append('/').append(scan.expected());
			}

			lines.add(line.toString());
			colors.add(GOLD);
		}

		if (LayerFilter.active()) {
			lines.add(Lang.t("display.layers", LayerFilter.describe()));
			colors.add(GOLD);
		}

		if (config.easyPlace) {
			String wanted = EasyPlace.wantedBlock();
			lines.add(Lang.t("hud.easy_place",
					wanted == null ? Lang.t("hud.nothing_here") : shortName(wanted)));
			colors.add(wanted == null ? GREY : GOLD);
		}

		if (config.showMaterials) {
			MaterialTally[] totals = ScanCache.missingTotals();
			int left = totals[0].total() + totals[1].total();

			if (left > 0) {
				lines.add(Lang.t("hud.missing", String.format("%,d", left)));
				colors.add(GREY);

				int shown = 0;

				for (Map.Entry<String, Integer> entry : totals[0].counts().entrySet()) {
					if (shown++ >= 3) {
						break;
					}

					int unknown = totals[1].get(entry.getKey());
					lines.add("  " + shortName(entry.getKey()) + ": "
							+ String.format("%,d", entry.getValue() + unknown));
					colors.add(GREY);
				}
			} else if (!plan.tally().isEmpty()) {
				lines.add(Lang.t("hud.nothing_missing"));
				colors.add(GREY);
			}
		}

		draw(graphics, mc, config, lines, colors);
	}

	private static String shortName(String blockId) {
		int colon = blockId.indexOf(':');
		return colon < 0 ? blockId : blockId.substring(colon + 1);
	}

	private static void draw(GuiGraphics graphics, Minecraft mc, BeaconatorConfig config,
			List<String> lines, List<Integer> colors) {
		int lineHeight = mc.font.lineHeight + 1;
		int width = 0;

		for (String line : lines) {
			width = Math.max(width, mc.font.width(line));
		}

		int height = lines.size() * lineHeight;
		boolean right = config.hudCorner == 1 || config.hudCorner == 3;
		boolean bottom = config.hudCorner == 2 || config.hudCorner == 3;

		int x = right ? graphics.guiWidth() - width - config.hudOffsetX : config.hudOffsetX;
		int y = bottom ? graphics.guiHeight() - height - config.hudOffsetY : config.hudOffsetY;

		graphics.fill(x - 2, y - 2, x + width + 2, y + height, 0x80000000);

		for (int index = 0; index < lines.size(); index++) {
			graphics.drawString(mc.font, lines.get(index), x, y + index * lineHeight, colors.get(index), false);
		}
	}
}
