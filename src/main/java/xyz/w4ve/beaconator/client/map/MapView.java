package xyz.w4ve.beaconator.client.map;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import xyz.w4ve.beaconator.client.Lang;
import xyz.w4ve.beaconator.client.PlanManager;
import xyz.w4ve.beaconator.config.BeaconatorConfig;
import xyz.w4ve.beaconator.model.CoverageBox;
import xyz.w4ve.beaconator.model.GridGenerator;
import xyz.w4ve.beaconator.model.GridNode;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.client.scan.ScanCache;

/**
 * Pan, zoom and the overlay drawn on top of the terrain: every node's coverage as a rectangle,
 * coloured by state.
 *
 * <p>This is where a perimeter is actually planned. Nodes sit a hundred blocks apart, so
 * deciding which ones are inside and which get a marker is a thing you do looking down at the
 * whole shape, not by squinting at boxes from the ground.
 */
public final class MapView {
	/** What a drag selection does to the nodes it covers. */
	public enum SelectionMode {
		REMOVE(NodeStatus.REMOVED),
		EXCLUDE(NodeStatus.EXCLUDED),
		RESET(NodeStatus.PENDING);

		private final NodeStatus status;

		SelectionMode(NodeStatus status) {
			this.status = status;
		}

		public NodeStatus status() {
			return status;
		}
	}

	private double centerX;
	private double centerZ;
	private double zoom = 1.0;
	private boolean centred;
	private boolean showCoverage = true;
	private boolean showGrid = true;
	private NodeKey hovered;

	private SelectionMode selectionMode;
	private double selectStartX;
	private double selectStartZ;
	private double selectEndX;
	private double selectEndZ;

	public boolean showCoverage() {
		return showCoverage;
	}

	public void toggleCoverage() {
		showCoverage = !showCoverage;
	}

	public boolean showGrid() {
		return showGrid;
	}

	public void toggleGrid() {
		showGrid = !showGrid;
	}

	public NodeKey hovered() {
		return hovered;
	}

	public void centreOnPlayer() {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player != null) {
			centerX = minecraft.player.getX();
			centerZ = minecraft.player.getZ();
			centred = true;
		}
	}

	/** Frames the whole plan inside the given area. */
	public void fit(PerimeterPlan plan, int width, int height) {
		int[] bounds = plan.coverageBounds();

		if (bounds == null) {
			return;
		}

		centerX = (bounds[0] + bounds[2]) / 2.0;
		centerZ = (bounds[1] + bounds[3]) / 2.0;
		double blocksWide = bounds[2] - bounds[0] + 1.0;
		double blocksTall = bounds[3] - bounds[1] + 1.0;
		zoom = Math.min(width / blocksWide, height / blocksTall) * 0.92;
		centred = true;
	}

	public void ensureCentred(PerimeterPlan plan, int width, int height) {
		if (!centred) {
			fit(plan, width, height);
		}
	}

	public void zoomBy(double amount, double mouseX, double mouseY, int x, int y, int width, int height) {
		double worldBeforeX = screenToWorldX(mouseX, x, width);
		double worldBeforeZ = screenToWorldZ(mouseY, y, height);
		zoom = Math.clamp(zoom * (amount > 0 ? 1.2 : 1 / 1.2), 0.01, 16.0);
		// Keep the block under the cursor where it was, the way every map does it.
		centerX += worldBeforeX - screenToWorldX(mouseX, x, width);
		centerZ += worldBeforeZ - screenToWorldZ(mouseY, y, height);
	}

	public void drag(double dx, double dy) {
		centerX -= dx / zoom;
		centerZ -= dy / zoom;
	}

	public double screenToWorldX(double screenX, int x, int width) {
		return centerX + (screenX - (x + width / 2.0)) / zoom;
	}

	public double screenToWorldZ(double screenY, int y, int height) {
		return centerZ + (screenY - (y + height / 2.0)) / zoom;
	}

	public double worldToScreenX(double worldX, int x, int width) {
		return x + width / 2.0 + (worldX - centerX) * zoom;
	}

	public double worldToScreenZ(double worldZ, int y, int height) {
		return y + height / 2.0 + (worldZ - centerZ) * zoom;
	}

	// ------------------------------------------------------------- selection

	public boolean selecting() {
		return selectionMode != null;
	}

	public SelectionMode selectionMode() {
		return selectionMode;
	}

	/** Starts a drag selection at the cursor. */
	public void beginSelection(SelectionMode mode, double mouseX, double mouseY,
			int x, int y, int width, int height) {
		selectionMode = mode;
		selectStartX = screenToWorldX(mouseX, x, width);
		selectStartZ = screenToWorldZ(mouseY, y, height);
		selectEndX = selectStartX;
		selectEndZ = selectStartZ;
	}

	public void updateSelection(double mouseX, double mouseY, int x, int y, int width, int height) {
		if (selectionMode == null) {
			return;
		}

		selectEndX = screenToWorldX(mouseX, x, width);
		selectEndZ = screenToWorldZ(mouseY, y, height);
	}

	public void cancelSelection() {
		selectionMode = null;
	}

	/**
	 * Nodes whose beacon sits inside the current selection rectangle.
	 *
	 * <p>Matching on the beacon rather than on the coverage box is deliberate: coverage boxes are
	 * a hundred blocks wide and overlap, so a small drag would sweep up half the perimeter.
	 */
	public List<NodeKey> selectedNodes(PerimeterPlan plan) {
		List<NodeKey> keys = new ArrayList<>();

		if (selectionMode == null || plan == null) {
			return keys;
		}

		double minX = Math.min(selectStartX, selectEndX);
		double maxX = Math.max(selectStartX, selectEndX);
		double minZ = Math.min(selectStartZ, selectEndZ);
		double maxZ = Math.max(selectStartZ, selectEndZ);

		for (GridNode node : plan.nodes()) {
			if (node.x() >= minX && node.x() <= maxX && node.z() >= minZ && node.z() <= maxZ) {
				keys.add(node.key());
			}
		}

		// A shift click without dragging should still do something: take the node under it.
		if (keys.isEmpty() && maxX - minX < plan.spacing() && maxZ - minZ < plan.spacing()) {
			NodeKey key = GridGenerator.nearestKey(plan.centerX(), plan.centerZ(), plan.spacing(),
					(minX + maxX) / 2.0, (minZ + maxZ) / 2.0);

			if (plan.extents().contains(key.i(), key.j())) {
				keys.add(key);
			}
		}

		return keys;
	}

	private void drawSelection(GuiGraphics graphics, PerimeterPlan plan, int x, int y, int width, int height) {
		if (selectionMode == null) {
			return;
		}

		int left = (int) Math.round(worldToScreenX(Math.min(selectStartX, selectEndX), x, width));
		int right = (int) Math.round(worldToScreenX(Math.max(selectStartX, selectEndX), x, width));
		int top = (int) Math.round(worldToScreenZ(Math.min(selectStartZ, selectEndZ), y, height));
		int bottom = (int) Math.round(worldToScreenZ(Math.max(selectStartZ, selectEndZ), y, height));

		int colour = switch (selectionMode) {
			case REMOVE -> 0xFF6060;
			case EXCLUDE -> 0xC8CDD6;
			case RESET -> 0x57E36B;
		};

		graphics.fill(left, top, right, bottom, 0x33000000 | colour);
		graphics.hLine(left, right, top, 0xFF000000 | colour);
		graphics.hLine(left, right, bottom, 0xFF000000 | colour);
		graphics.vLine(left, top, bottom, 0xFF000000 | colour);
		graphics.vLine(right, top, bottom, 0xFF000000 | colour);

		// Highlight what is actually caught, so the count is never a surprise.
		for (NodeKey key : selectedNodes(plan)) {
			GridNode node = plan.nodeAt(key);
			int bx = (int) Math.round(worldToScreenX(node.x() + 0.5, x, width));
			int bz = (int) Math.round(worldToScreenZ(node.z() + 0.5, y, height));
			graphics.fill(bx - 4, bz - 4, bx + 4, bz + 4, 0xFF000000 | colour);
		}
	}

	/** The node under the cursor, or null when it falls outside the grid. */
	public NodeKey nodeAt(PerimeterPlan plan, double mouseX, double mouseY, int x, int y, int width, int height) {
		double worldX = screenToWorldX(mouseX, x, width);
		double worldZ = screenToWorldZ(mouseY, y, height);
		NodeKey key = GridGenerator.nearestKey(plan.centerX(), plan.centerZ(), plan.spacing(), worldX, worldZ);
		return plan.extents().contains(key.i(), key.j()) ? key : null;
	}

	public void render(GuiGraphics graphics, PerimeterPlan plan,
			int x, int y, int width, int height, int mouseX, int mouseY) {
		graphics.enableScissor(x, y, x + width, y + height);
		graphics.fill(x, y, x + width, y + height, 0xFF12100F);
		drawTiles(graphics, x, y, width, height);

		hovered = nodeAt(plan, mouseX, mouseY, x, y, width, height);
		BeaconatorConfig config = BeaconatorConfig.get();

		for (GridNode node : plan.nodes()) {
			NodeStatus status = plan.statusAt(node.key());
			boolean isHovered = node.key().equals(hovered);
			int color = colorFor(status, isHovered, config, node.key());
			CoverageBox box = plan.coverageOf(node);

			int left = (int) Math.round(worldToScreenX(box.renderMinX(), x, width));
			int top = (int) Math.round(worldToScreenZ(box.renderMinZ(), y, height));
			int right = (int) Math.round(worldToScreenX(box.renderMaxX(), x, width));
			int bottom = (int) Math.round(worldToScreenZ(box.renderMaxZ(), y, height));

			if (right < x || left > x + width || bottom < y || top > y + height) {
				continue;
			}

			if (showCoverage && status != NodeStatus.REMOVED) {
				int alpha = isHovered ? 0x60 : 0x30;
				graphics.fill(left, top, right, bottom, alpha << 24 | (color & 0xFFFFFF));
			}

			if (showGrid) {
				int edge = (status == NodeStatus.REMOVED ? 0x50 : 0xCC) << 24 | (color & 0xFFFFFF);
				graphics.hLine(left, right - 1, top, edge);
				graphics.hLine(left, right - 1, bottom - 1, edge);
				graphics.vLine(left, top, bottom - 1, edge);
				graphics.vLine(right - 1, top, bottom - 1, edge);
			}

			if (status != NodeStatus.REMOVED) {
				// The beacons themselves, so you can see where they really sit.
				int beaconX = (int) Math.round(worldToScreenX(node.x() + 0.5, x, width));
				int beaconZ = (int) Math.round(worldToScreenZ(node.z() + 0.5, y, height));
				graphics.fill(beaconX - 1, beaconZ - 1, beaconX + 2, beaconZ + 2, 0xFF000000 | (color & 0xFFFFFF));
			}
		}

		drawSelection(graphics, plan, x, y, width, height);
		drawPlayer(graphics, x, y, width, height);
		graphics.disableScissor();
	}

	/**
	 * Draws the terrain tiles that fall inside the view.
	 *
	 * <p>When the view is zoomed far enough out that it spans a lot of tiles, only the ones
	 * already in memory are drawn: pulling dozens of PNGs off disk mid frame is not worth a
	 * blurry overview.
	 */
	private void drawTiles(GuiGraphics graphics, int x, int y, int width, int height) {
		int minTileX = Math.floorDiv((int) Math.floor(screenToWorldX(x, x, width)), MapTile.SIZE);
		int maxTileX = Math.floorDiv((int) Math.ceil(screenToWorldX(x + width, x, width)), MapTile.SIZE);
		int minTileZ = Math.floorDiv((int) Math.floor(screenToWorldZ(y, y, height)), MapTile.SIZE);
		int maxTileZ = Math.floorDiv((int) Math.ceil(screenToWorldZ(y + height, y, height)), MapTile.SIZE);

		// Reading a tile off disk is a few milliseconds and only happens once, so the only case
		// worth refusing is a view so far out that it would pull in the entire world at once.
		int span = (maxTileX - minTileX + 1) * (maxTileZ - minTileZ + 1);
		boolean allowDiskReads = span <= 256;
		int drawSize = (int) Math.ceil(MapTile.SIZE * zoom) + 1;

		for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
			for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
				MapTile tile = allowDiskReads
						? MapStore.tile(tileX, tileZ, false)
						: MapStore.tileIfLoaded(tileX, tileZ);

				if (tile == null) {
					continue;
				}

				int screenX = (int) Math.floor(worldToScreenX(tile.originX(), x, width));
				int screenZ = (int) Math.floor(worldToScreenZ(tile.originZ(), y, height));
				graphics.blit(tile.textureId(), screenX, screenZ, drawSize, drawSize,
						0.0f, 0.0f, MapTile.SIZE, MapTile.SIZE, MapTile.SIZE, MapTile.SIZE);
			}
		}
	}

	private void drawPlayer(GuiGraphics graphics, int x, int y, int width, int height) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null) {
			return;
		}

		int px = (int) Math.round(worldToScreenX(minecraft.player.getX(), x, width));
		int pz = (int) Math.round(worldToScreenZ(minecraft.player.getZ(), y, height));
		graphics.fill(px - 3, pz - 3, px + 3, pz + 3, 0xFF000000);
		graphics.fill(px - 2, pz - 2, px + 2, pz + 2, 0xFFFF4444);
	}

	private static int colorFor(NodeStatus status, boolean hovered, BeaconatorConfig config,
			NodeKey key) {
		if (hovered) {
			return config.colorHover;
		}

		// The same three way read the world render uses, excluded nodes included: they get built
		// too, so flat grey left no way to see which ones are done.
		if (config.showProgressColor && key != null && status != NodeStatus.REMOVED) {
			boolean excluded = status == NodeStatus.EXCLUDED;

			if (ScanCache.finished(key)) {
				return excluded ? blend(config.colorExcluded, config.colorPlaced, 0.6f)
						: config.colorPlaced;
			}

			if (ScanCache.partial(key)) {
				return excluded ? blend(config.colorExcluded, config.colorPartial, 0.6f)
						: config.colorPartial;
			}
		}

		return switch (status) {
			case PENDING -> config.colorPending;
			case PLACED -> config.colorPlaced;
			case EXCLUDED -> config.colorExcluded;
			case REMOVED -> config.colorRemoved;
		};
	}

	private static int blend(int from, int to, float amount) {
		float clamped = Math.clamp(amount, 0.0f, 1.0f);
		int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * clamped);
		int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * clamped);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * clamped);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** Blocks per screen pixel, for the scale readout. */
	public double zoom() {
		return zoom;
	}

	public String describe(PerimeterPlan plan) {
		// The map itself is always one pixel per block; this is only how far out the view is.
		return String.format("%.0f, %.0f  ·  zoom %s  ·  %d %s",
				centerX, centerZ,
				zoom >= 1 ? String.format("%.1fx", zoom) : String.format("1:%.0f", 1 / zoom),
				plan.extents().nodeCount(), Lang.t("nodes"));
	}

	/** Convenience for the screen: the plan currently being edited. */
	public static PerimeterPlan plan() {
		return PlanManager.plan();
	}
}
