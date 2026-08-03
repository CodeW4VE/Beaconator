package xyz.w4ve.beaconator.client.map;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import xyz.w4ve.beaconator.client.Lang;
import xyz.w4ve.beaconator.client.PlanManager;
import xyz.w4ve.beaconator.config.BeaconatorConfig;
import xyz.w4ve.beaconator.model.CoverageBox;
import xyz.w4ve.beaconator.model.GridNode;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.model.PyramidLayer;
import xyz.w4ve.beaconator.client.scan.ScanCache;
import xyz.w4ve.beaconator.client.water.WaterCache;
import xyz.w4ve.beaconator.client.water.WaterScan;
import xyz.w4ve.beaconator.client.water.WaterStretch;
import xyz.w4ve.beaconator.model.water.WaterFittings;
import xyz.w4ve.beaconator.model.water.WaterPlan;
import xyz.w4ve.beaconator.model.water.WaterSegment;

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

	/**
	 * How close to its own row or column a dragged node has to land to snap back onto it.
	 *
	 * <p>Most nodes are moved because of something small in the way, and a node that stays lined
	 * up with its row keeps the water channel that serves the row straight. Snapping means you get
	 * that alignment by aiming roughly, instead of by counting blocks. Hold control to move free.
	 */
	private static final int SNAP_BLOCKS = 4;

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

	/**
	 * The currents map rather than the beacons map.
	 *
	 * <p>Same view, same pan and zoom, so turning the page does not lose your place. What changes
	 * is what is bright: with a hundred nodes drawn at full strength there is no seeing a channel
	 * one block wide, so the nodes step back and the water comes forward.
	 */
	private boolean waterMode;
	private boolean drawing;
	private int runStartX;
	private int runStartZ;
	private int runEndX;
	private int runEndZ;

	private boolean moveMode;
	/** The node being dragged right now, or the last one moved while move mode stays on. */
	private NodeKey moveKey;
	private boolean moving;
	private int moveStartDx;
	private int moveStartDz;
	private double moveGrabX;
	private double moveGrabZ;

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

	// ----------------------------------------------------------------- water

	public boolean waterMode() {
		return waterMode;
	}

	public void setWaterMode(boolean water) {
		waterMode = water;

		if (!water) {
			drawing = false;
		}
	}

	/** The block under the cursor, as {@code {x, z}}. */
	public int[] blockAt(double mouseX, double mouseY, int x, int y, int width, int height) {
		return new int[] {
			(int) Math.floor(screenToWorldX(mouseX, x, width)),
			(int) Math.floor(screenToWorldZ(mouseY, y, height))
		};
	}

	public boolean drawingRun() {
		return drawing;
	}

	public void beginRun(int blockX, int blockZ) {
		drawing = true;
		runStartX = blockX;
		runStartZ = blockZ;
		runEndX = blockX;
		runEndZ = blockZ;
	}

	/**
	 * Follows the cursor, snapped to whichever axis the drag has gone furthest along.
	 *
	 * <p>Channels are axis aligned, so a free hand line would have to be corrected afterwards
	 * anyway. Snapping while you drag means what you see being drawn is what gets built.
	 */
	public void updateRun(int blockX, int blockZ) {
		if (!drawing) {
			return;
		}

		if (Math.abs(blockX - runStartX) >= Math.abs(blockZ - runStartZ)) {
			runEndX = blockX;
			runEndZ = runStartZ;
		} else {
			runEndX = runStartX;
			runEndZ = blockZ;
		}
	}

	/** @return the run that was drawn, or null when the drag never left the block it started on */
	public WaterSegment endRun() {
		if (!drawing) {
			return null;
		}

		drawing = false;

		if (runEndX == runStartX && runEndZ == runStartZ) {
			return null;
		}

		return new WaterSegment(runStartX, runStartZ, runEndX, runEndZ, WaterSegment.Kind.SPINE);
	}

	public void cancelRun() {
		drawing = false;
	}

	/**
	 * The channels: the runs, the run being drawn, the drain, and a cross on every node the network
	 * does not reach.
	 *
	 * <p>Drawn one block wide however far out the view is, because a channel that vanishes when you
	 * zoom out to see the whole perimeter is no use for planning one.
	 */
	private void drawWater(GuiGraphics graphics, PerimeterPlan plan, int x, int y, int width, int height) {
		BeaconatorConfig config = BeaconatorConfig.get();
		WaterPlan water = plan.water();


		// The pyramid bases. On the beacons map they are noise; here they are the whole geometry of
		// the problem, because they sit on the same layer as the channel and a run through one is a
		// broken beacon. Excluded nodes included, in their own colour: they are not served any more
		// but they are still very much in the way.
		for (GridNode node : plan.nodes()) {
			NodeStatus status = plan.statusAt(node.key());

			if (status == NodeStatus.REMOVED) {
				continue;
			}

			List<PyramidLayer> layers = plan.layersOf(node);
			PyramidLayer base = layers.get(layers.size() - 1);
			int left = (int) Math.round(worldToScreenX(base.minX(), x, width));
			int top = (int) Math.round(worldToScreenZ(base.minZ(), y, height));
			int right = (int) Math.round(worldToScreenX(base.maxX() + 1.0, x, width));
			int bottom = (int) Math.round(worldToScreenZ(base.maxZ() + 1.0, y, height));
			int colour = status == NodeStatus.EXCLUDED ? config.colorExcluded : config.colorPending;

			graphics.fill(left, top, Math.max(right, left + 1), Math.max(bottom, top + 1),
					0x60000000 | (colour & 0xFFFFFF));
			graphics.hLine(left, right, top, 0xC0000000 | (colour & 0xFFFFFF));
			graphics.hLine(left, right, bottom, 0xC0000000 | (colour & 0xFFFFFF));
			graphics.vLine(left, top, bottom, 0xC0000000 | (colour & 0xFFFFFF));
			graphics.vLine(right, top, bottom, 0xC0000000 | (colour & 0xFFFFFF));
		}

		// Stretch by stretch, and what is already running is simply not drawn. That is the whole
		// progress display: the map empties out as the channel gets built.
		for (WaterSegment run : water.runs()) {
			boolean bad = WaterCache.blocked(plan, run);

			for (WaterStretch stretch : WaterStretch.of(run)) {
				if (stretch.state().done()) {
					continue;
				}

				drawRun(graphics, stretch.x1(), stretch.z1(), stretch.x2(), stretch.z2(),
						x, y, width, height,
						bad ? config.colorWaterBad
								: stretch.state().colour(config));
			}
		}

		if (drawing) {
			drawRun(graphics, runStartX, runStartZ, runEndX, runEndZ, x, y, width, height,
					0xFFFFFFFF);
		}

		// Nothing planned means nothing is stranded. Without this, an empty network reports every
		// node as an orphan and the map fills with red crosses about water nobody asked for yet.
		if (water.isEmpty()) {
			return;
		}

		// Nodes the water never reaches. Cheap to draw and the one thing about a network that is
		// impossible to see by looking at it: a row whose spine you erased looks fine.
		for (NodeKey key : WaterCache.orphans(plan)) {
			GridNode node = plan.nodeAt(key);
			int nodeX = (int) Math.round(worldToScreenX(node.x() + 0.5, x, width));
			int nodeZ = (int) Math.round(worldToScreenZ(node.z() + 0.5, y, height));
			graphics.hLine(nodeX - 3, nodeX + 3, nodeZ, config.colorWaterBad);
			graphics.vLine(nodeX, nodeZ - 3, nodeZ + 3, config.colorWaterBad);
		}

		if (config.showFittings) {
			WaterFittings fittings = WaterFittings.of(water);

			for (int[] source : fittings.sources()) {
				int sx = (int) Math.round(worldToScreenX(source[0] + 0.5, x, width));
				int sz = (int) Math.round(worldToScreenZ(source[1] + 0.5, y, height));
				graphics.fill(sx - 1, sz - 1, sx + 2, sz + 2, 0xFF000000 | (config.colorWater & 0xFFFFFF));
			}

			for (int[] stop : fittings.stops()) {
				int sx = (int) Math.round(worldToScreenX(stop[0] + 0.5, x, width));
				int sz = (int) Math.round(worldToScreenZ(stop[1] + 0.5, y, height));
				graphics.fill(sx - 2, sz - 2, sx + 3, sz + 3,
						0xFF000000 | (config.colorWaterDrain & 0xFFFFFF));
			}
		}

		// Nodes whose beacon already has water on it: the drop into the channel is working there.
		for (GridNode node : plan.buildNodes()) {
			if (!WaterScan.fed(node.key())) {
				continue;
			}

			int nodeX = (int) Math.round(worldToScreenX(node.x() + 0.5, x, width));
			int nodeZ = (int) Math.round(worldToScreenZ(node.z() + 0.5, y, height));
			graphics.hLine(nodeX - 5, nodeX + 5, nodeZ - 5, config.colorWater);
			graphics.hLine(nodeX - 5, nodeX + 5, nodeZ + 5, config.colorWater);
			graphics.vLine(nodeX - 5, nodeZ - 5, nodeZ + 5, config.colorWater);
			graphics.vLine(nodeX + 5, nodeZ - 5, nodeZ + 5, config.colorWater);
		}

		int[] drain = WaterCache.drain(plan);

		if (drain != null) {
			int drainX = (int) Math.round(worldToScreenX(drain[0] + 0.5, x, width));
			int drainZ = (int) Math.round(worldToScreenZ(drain[1] + 0.5, y, height));
			graphics.fill(drainX - 4, drainZ - 4, drainX + 5, drainZ + 5, 0xFF000000);
			graphics.fill(drainX - 3, drainZ - 3, drainX + 4, drainZ + 4, config.colorWaterDrain);
		}
	}

	private void drawRun(GuiGraphics graphics, int x1, int z1, int x2, int z2,
			int x, int y, int width, int height, int colour) {
		int left = (int) Math.round(worldToScreenX(Math.min(x1, x2), x, width));
		int right = (int) Math.round(worldToScreenX(Math.max(x1, x2) + 1.0, x, width));
		int top = (int) Math.round(worldToScreenZ(Math.min(z1, z2), y, height));
		int bottom = (int) Math.round(worldToScreenZ(Math.max(z1, z2) + 1.0, y, height));

		// A run is one block wide, and one block is well under a pixel at the zoom you plan a
		// perimeter at. Never let it round away to nothing.
		graphics.fill(left, top, Math.max(right, left + 1), Math.max(bottom, top + 1), colour);
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
			NodeKey key = plan.keyNear((minX + maxX) / 2.0, (minZ + maxZ) / 2.0);

			if (key != null) {
				keys.add(key);
			}
		}

		return keys;
	}

	// ------------------------------------------------------------------- moving

	public boolean moveMode() {
		return moveMode;
	}

	public void toggleMoveMode() {
		moveMode = !moveMode;

		if (!moveMode) {
			moving = false;
			moveKey = null;
		}
	}

	/** The node the arrow keys will nudge: the one being dragged, or the last one dragged. */
	public NodeKey moveKey() {
		return moving || moveMode ? moveKey : null;
	}

	public void setMoveKey(NodeKey key) {
		moveKey = key;
	}

	public boolean movingNode() {
		return moving;
	}

	/**
	 * Grabs the node under the cursor. The offset it already has is kept, so a second drag
	 * carries on from where the first one left it instead of starting over from the grid.
	 */
	public boolean beginMove(PerimeterPlan plan, double mouseX, double mouseY,
			int x, int y, int width, int height) {
		NodeKey key = nodeAt(plan, mouseX, mouseY, x, y, width, height);

		if (key == null) {
			return false;
		}

		int[] offset = plan.offsetAt(key);
		moveKey = key;
		moving = true;
		moveStartDx = offset[0];
		moveStartDz = offset[1];
		moveGrabX = screenToWorldX(mouseX, x, width);
		moveGrabZ = screenToWorldZ(mouseY, y, height);
		return true;
	}

	/** @return the offset the node ended up with, or null when nothing is being dragged */
	public int[] updateMove(PerimeterPlan plan, double mouseX, double mouseY,
			int x, int y, int width, int height, boolean free) {
		if (!moving || moveKey == null) {
			return null;
		}

		int dx = moveStartDx + (int) Math.round(screenToWorldX(mouseX, x, width) - moveGrabX);
		int dz = moveStartDz + (int) Math.round(screenToWorldZ(mouseY, y, height) - moveGrabZ);
		return applyMove(plan, moveKey, dx, dz, free);
	}

	public void endMove() {
		moving = false;
	}

	/** Nudges the node the arrow keys are aimed at. No snapping: the steps are exact already. */
	public int[] nudge(PerimeterPlan plan, int dx, int dz) {
		if (moveKey == null) {
			return null;
		}

		int[] offset = plan.offsetAt(moveKey);
		return applyMove(plan, moveKey, offset[0] + dx, offset[1] + dz, true);
	}

	private int[] applyMove(PerimeterPlan plan, NodeKey key, int dx, int dz, boolean free) {
		if (!free) {
			dx = Math.abs(dx) <= SNAP_BLOCKS ? 0 : dx;
			dz = Math.abs(dz) <= SNAP_BLOCKS ? 0 : dz;
		}

		PlanManager.moveNode(key, dx, dz);
		return plan.offsetAt(key);
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
		return plan.keyNear(screenToWorldX(mouseX, x, width), screenToWorldZ(mouseY, y, height));
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

			if (showCoverage && status != NodeStatus.REMOVED && !waterMode) {
				int alpha = isHovered ? 0x60 : 0x30;
				graphics.fill(left, top, right, bottom, alpha << 24 | (color & 0xFFFFFF));
			}

			if (showGrid) {
				int edge = (waterMode ? 0x40 : status == NodeStatus.REMOVED ? 0x50 : 0xCC) << 24
						| (color & 0xFFFFFF);
				graphics.hLine(left, right - 1, top, edge);
				graphics.hLine(left, right - 1, bottom - 1, edge);
				graphics.vLine(left, top, bottom - 1, edge);
				graphics.vLine(right - 1, top, bottom - 1, edge);
			}

			if (status != NodeStatus.REMOVED) {
				// The beacons themselves, so you can see where they really sit.
				int beaconX = (int) Math.round(worldToScreenX(node.x() + 0.5, x, width));
				int beaconZ = (int) Math.round(worldToScreenZ(node.z() + 0.5, y, height));

				// On the currents map the beacons are drawn bigger, not dimmer. What steps back is
				// the hundred block coverage box; the beacons themselves are the landmarks you read
				// the whole map by, and losing them was the first thing anyone noticed.
				int dot = waterMode ? 2 : 1;
				graphics.fill(beaconX - dot - 1, beaconZ - dot - 1, beaconX + dot + 2, beaconZ + dot + 2,
						0xC0000000);
				graphics.fill(beaconX - dot, beaconZ - dot, beaconX + dot + 1, beaconZ + dot + 1,
						0xFF000000 | (color & 0xFFFFFF));
			}
		}

		if (waterMode) {
			drawWater(graphics, plan, x, y, width, height);
		}

		drawMoved(graphics, plan, x, y, width, height);
		drawSelection(graphics, plan, x, y, width, height);
		drawPlayer(graphics, x, y, width, height);
		graphics.disableScissor();
	}

	/**
	 * Where each moved node came from: a mark on the cell the grid gave it and a line to where it
	 * actually is. Without it a perimeter with a few nudged nodes just looks like a grid that was
	 * measured badly, and there is no way to tell a deliberate nudge from a mistake.
	 */
	private void drawMoved(GuiGraphics graphics, PerimeterPlan plan, int x, int y, int width, int height) {
		for (NodeKey key : plan.movedKeys()) {
			if (plan.statusAt(key) == NodeStatus.REMOVED) {
				continue;
			}

			GridNode home = plan.gridNodeAt(key);
			GridNode node = plan.nodeAt(key);
			int homeX = (int) Math.round(worldToScreenX(home.x() + 0.5, x, width));
			int homeZ = (int) Math.round(worldToScreenZ(home.z() + 0.5, y, height));
			int nodeX = (int) Math.round(worldToScreenX(node.x() + 0.5, x, width));
			int nodeZ = (int) Math.round(worldToScreenZ(node.z() + 0.5, y, height));
			int colour = key.equals(moveKey) ? 0xFFFFC64D : 0xFFB0783C;

			// The two legs of the offset rather than a diagonal: the move is read in blocks east
			// and blocks south, which is how it gets built.
			graphics.hLine(Math.min(homeX, nodeX), Math.max(homeX, nodeX), homeZ, colour);
			graphics.vLine(nodeX, Math.min(homeZ, nodeZ), Math.max(homeZ, nodeZ), colour);
			graphics.fill(homeX - 1, homeZ - 1, homeX + 2, homeZ + 2, 0x80000000 | (colour & 0xFFFFFF));
		}
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
		boolean excluded = status == NodeStatus.EXCLUDED;

		if (config.showProgressColor && key != null && status != NodeStatus.REMOVED
				&& (!excluded || config.shadeExcluded)) {

			if (ScanCache.finished(key)) {
				return excluded ? config.colorExcludedDone : config.colorPlaced;
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
