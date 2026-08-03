package xyz.w4ve.beaconator.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import xyz.w4ve.beaconator.client.LayerFilter;
import xyz.w4ve.beaconator.client.PlanManager;
import xyz.w4ve.beaconator.config.BeaconatorConfig;
import xyz.w4ve.beaconator.config.CoverageStyle;
import xyz.w4ve.beaconator.model.CoverageBox;
import xyz.w4ve.beaconator.model.GridNode;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.model.PyramidLayer;

/**
 * Draws the plan in the world: one coverage volume per node, the beacon positions, and the
 * pyramid footprint.
 *
 * <p>There is deliberately no highlight of the schematic blocks themselves. Painting air and
 * dirt orange is the noise this mod exists to avoid.
 *
 * <p>This is the 1.21.11 and newer version of the file. It builds the same vertices as the
 * original and differs in two ways.
 *
 * <p>Drawing is split in two. 1.21.10 separated the frame into an extraction phase, where a mod
 * works out what it is going to draw, and a drawing phase, where it may touch nothing but what it
 * extracted. The frustum only exists in the first, so {@link #extract} does the culling and hands
 * the surviving nodes to {@link #render} through {@link #visible}. One frame deep, and the list is
 * dropped as soon as it has been drawn, so a frame that never draws cannot show a stale one.
 *
 * <p>Line width is no longer GL state, so it is set on {@link ShapeRenderer} rather than on the
 * driver.
 */
public final class PerimeterRenderer {
	/** The nodes {@link #extract} decided are worth drawing, for {@link #render} to draw. */
	private static List<GridNode> visible = List.of();

	private PerimeterRenderer() {
	}

	/**
	 * Picks this frame's nodes. Runs while the frustum and the world are still reachable, which is
	 * the whole reason this is a separate phase.
	 */
	public static void extract(WorldExtractionContext context) {
		visible = List.of();

		PerimeterPlan plan = PlanManager.plan();
		Minecraft mc = Minecraft.getInstance();

		if (plan == null || mc.player == null || mc.level == null || !PlanManager.inPlanDimension()) {
			return;
		}

		BeaconatorConfig config = BeaconatorConfig.get();

		if (!config.enabled) {
			return;
		}

		// The water lines are their own answer to "is there anything to draw": they live under the
		// perimeter and stand on their own, so having every overlay off must not take them with it.
		if (!config.renderCoverage && !config.renderWireframe && !config.renderBeaconMarkers
				&& !config.showBeams && !WaterRenderer.wants(plan, config)) {
			return;
		}

		List<GridNode> nodes = plan.nodes();

		if (nodes.isEmpty()) {
			return;
		}

		visible = cull(plan, nodes, mc, config, context.frustum());
	}

	public static void render(WorldRenderContext context) {
		List<GridNode> nodes = visible;
		visible = List.of();

		PerimeterPlan plan = PlanManager.plan();
		Minecraft mc = Minecraft.getInstance();

		if (plan == null || mc.level == null) {
			return;
		}

		BeaconatorConfig config = BeaconatorConfig.get();

		// Nothing extracted does not mean nothing to draw: the water lines are not culled per node,
		// so a frame where every node is off screen still has channels to put on the ground.
		if (nodes.isEmpty() && !(config.enabled && WaterRenderer.wants(plan, config))) {
			return;
		}
		Vec3 camera = mc.gameRenderer.getMainCamera().position();
		PoseStack matrices = context.matrices();

		if (matrices == null) {
			return;
		}

		matrices.pushPose();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		Matrix4f matrix = matrices.last().pose();

		drawFaces(plan, nodes, mc, config, matrix);
		drawLines(plan, nodes, mc, config, matrix);

		ShapeRenderer.lineWidth(1.0f);
		matrices.popPose();
	}

	// ------------------------------------------------------------------- faces

	private static void drawFaces(PerimeterPlan plan, List<GridNode> nodes, Minecraft mc,
			BeaconatorConfig config, Matrix4f matrix) {
		// Beams and gap strips are their own settings and do not belong to the coverage volumes.
		// They used to live behind this check, so turning the coverage off took the beams with it.
		if (!config.renderCoverage && !config.showBeams && !plan.hasCoverageGaps()
				&& !WaterRenderer.wants(plan, config)) {
			return;
		}

		BufferBuilder buffer = Tesselator.getInstance()
				.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		boolean any = false;
		NodeKey hovered = PlanManager.hovered();

		for (GridNode node : nodes) {
			NodeStatus status = plan.statusAt(node.key());

			if (!config.renderCoverage || !isVisible(plan, node, status, mc, config)) {
				continue;
			}

			// Removed nodes are outlines only, so they can be picked back up without adding noise.
			if (status == NodeStatus.REMOVED) {
				continue;
			}

			int color = colorFor(status, node.key().equals(hovered), config, node.key());
			CoverageBox box = plan.coverageOf(node);
			double[] span = verticalSpan(box, node, mc, config);

			ShapeRenderer.boxFaces(buffer, matrix,
					box.renderMinX(), span[0], box.renderMinZ(),
					box.renderMaxX(), span[1], box.renderMaxZ(),
					ShapeRenderer.red(color), ShapeRenderer.green(color), ShapeRenderer.blue(color),
					config.coverageOpacity);
			any = true;
		}

		any |= drawBeams(plan, nodes, mc, config, matrix, buffer);
		any |= drawGapStrips(plan, nodes, mc, config, matrix, buffer);
		any |= WaterRenderer.emitFaces(plan, config, matrix, buffer);

		MeshData mesh = buffer.build();

		if (mesh != null && any) {
			Pipelines.draw(config.seeThrough ? Pipelines.FACES_SEE_THROUGH : Pipelines.FACES, mesh);
		} else if (mesh != null) {
			mesh.close();
		}
	}

	/**
	 * A beam standing on every beacon position, the way a real beacon does, so you can see where
	 * to dig from across the site instead of hunting for a faint box.
	 *
	 * <p>Red means the beacon is not there yet, green means it is, grey means the node is
	 * excluded. Same read as the boxes, but visible from anywhere and through the terrain in
	 * front of you.
	 *
	 * <p>It runs to the sky like the real thing and thickens with distance on purpose. A beam of
	 * a fixed 96 blocks and a fixed 0.44 wide is a one pixel scratch from the other side of a
	 * perimeter, which is exactly where you need to see it from.
	 */
	private static boolean drawBeams(PerimeterPlan plan, List<GridNode> nodes, Minecraft mc,
			BeaconatorConfig config, Matrix4f matrix, BufferBuilder buffer) {
		if (!config.showBeams) {
			return false;
		}

		boolean any = false;
		Vec3 eye = mc.gameRenderer.getMainCamera().position();
		double focal = focalLength(mc);

		for (GridNode node : nodes) {
			NodeStatus status = plan.statusAt(node.key());

			if (status == NodeStatus.REMOVED || !LayerFilter.allows(node.y())) {
				continue;
			}

			// Same three way read as the boxes, and this is where it earns its keep: a yellow
			// beam across the site is a node that looks built and is one beacon short. Excluded
			// nodes included, muted towards grey, because those get built as well.
			boolean excluded = status == NodeStatus.EXCLUDED;
			int base = excluded ? config.colorBeamExcluded : config.colorBeamPending;
			int color;

			if (excluded && !config.shadeExcluded) {
				color = base;
			} else if (xyz.w4ve.beaconator.client.scan.ScanCache.finished(node.key())) {
				color = excluded ? config.colorExcludedDone : config.colorBeamPlaced;
			} else if (xyz.w4ve.beaconator.client.scan.ScanCache.partial(node.key())) {
				color = excluded ? blend(base, config.colorBeamPartial, 0.6f) : config.colorBeamPartial;
			} else {
				color = status == NodeStatus.PLACED ? config.colorBeamPlaced : base;
			}

			float r = ShapeRenderer.red(color);
			float g = ShapeRenderer.green(color);
			float b = ShapeRenderer.blue(color);

			for (int[] pos : plan.beaconPositionsOf(node)) {
				double centreX = pos[0] + 0.5;
				double centreZ = pos[2] + 0.5;
				double bottom = pos[1] - Math.max(0, config.beamDepth);
				double top = pos[1] + Math.max(1, config.beamHeight);
				double half = beamHalf(config, focal, eye.distanceTo(new Vec3(centreX, pos[1], centreZ)));

				ShapeRenderer.boxFaces(buffer, matrix,
						centreX - half, bottom, centreZ - half,
						centreX + half, top, centreZ + half,
						r, g, b, config.beamOpacity);
				any = true;
			}
		}

		return any;
	}

	/**
	 * Half width to draw a beam at: the configured thickness up close, widened with distance so a
	 * beam never falls below {@link BeaconatorConfig#beamMinPixels} on screen.
	 */
	private static double beamHalf(BeaconatorConfig config, double focal, double distance) {
		double half = Math.clamp(config.beamWidth, 0.05f, 0.5f);

		if (config.beamMinPixels <= 0.0f || focal <= 0.0) {
			return half;
		}

		return Math.max(half, config.beamMinPixels * distance / (2.0 * focal));
	}

	/** Pixels per radian at the centre of the screen, for sizing things in screen space. */
	private static double focalLength(Minecraft mc) {
		int height = mc.getWindow().getHeight();

		if (height <= 0) {
			return 0.0;
		}

		double fov = Math.toRadians(Math.clamp(mc.options.fov().get().doubleValue(), 30.0, 120.0));
		return height / (2.0 * Math.tan(fov / 2.0));
	}

	/**
	 * Red strips over the ground that no beacon reaches. Only drawn when the spacing was set
	 * wider than the coverage, which is the one way to end up with holes in a perimeter.
	 */
	private static boolean drawGapStrips(PerimeterPlan plan, List<GridNode> nodes, Minecraft mc,
			BeaconatorConfig config, Matrix4f matrix, BufferBuilder buffer) {
		if (!plan.hasCoverageGaps()) {
			return false;
		}

		boolean any = false;
		float r = ShapeRenderer.red(config.colorGap);
		float g = ShapeRenderer.green(config.colorGap);
		float b = ShapeRenderer.blue(config.colorGap);
		float alpha = Math.min(1.0f, config.coverageOpacity * 3.0f);
		int gapX = plan.rowAxis() == xyz.w4ve.beaconator.model.RowAxis.X
				? plan.gapAlongRow() : plan.gapAcrossRow();
		int gapZ = plan.rowAxis() == xyz.w4ve.beaconator.model.RowAxis.Z
				? plan.gapAlongRow() : plan.gapAcrossRow();

		for (GridNode node : nodes) {
			NodeStatus status = plan.statusAt(node.key());

			if (!status.isBuilt() || !isVisible(plan, node, status, mc, config)) {
				continue;
			}

			CoverageBox box = plan.coverageOf(node);
			double y = node.y() + 0.05;

			if (gapX > 0 && plan.extents().contains(node.i() + 1, node.j())
					&& plan.statusAt(new NodeKey(node.i() + 1, node.j())).isBuilt()) {
				ShapeRenderer.flatQuad(buffer, matrix,
						box.renderMaxX(), y, box.renderMinZ(),
						box.renderMaxX() + gapX, box.renderMaxZ(), r, g, b, alpha);
				any = true;
			}

			if (gapZ > 0 && plan.extents().contains(node.i(), node.j() + 1)
					&& plan.statusAt(new NodeKey(node.i(), node.j() + 1)).isBuilt()) {
				ShapeRenderer.flatQuad(buffer, matrix,
						box.renderMinX(), y, box.renderMaxZ(),
						box.renderMaxX(), box.renderMaxZ() + gapZ, r, g, b, alpha);
				any = true;
			}
		}

		return any;
	}

	// ------------------------------------------------------------------- lines

	private static void drawLines(PerimeterPlan plan, List<GridNode> nodes, Minecraft mc,
			BeaconatorConfig config, Matrix4f matrix) {
		if (!config.renderWireframe && !config.renderBeaconMarkers && !config.renderPyramidFootprint
				&& !WaterRenderer.wants(plan, config)) {
			return;
		}

		ShapeRenderer.lineWidth(config.lineWidth);
		BufferBuilder buffer = Tesselator.getInstance()
				.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
		boolean any = false;
		NodeKey hovered = PlanManager.hovered();

		for (GridNode node : nodes) {
			NodeStatus status = plan.statusAt(node.key());

			if (!isVisible(plan, node, status, mc, config)) {
				continue;
			}

			boolean isHovered = node.key().equals(hovered);
			int color = colorFor(status, isHovered, config, node.key());
			float r = ShapeRenderer.red(color);
			float g = ShapeRenderer.green(color);
			float b = ShapeRenderer.blue(color);
			float alpha = status == NodeStatus.REMOVED ? config.wireframeOpacity * 0.35f : config.wireframeOpacity;

			if (config.renderWireframe) {
				CoverageBox box = plan.coverageOf(node);
				double[] span = verticalSpan(box, node, mc, config);
				ShapeRenderer.boxEdges(buffer, matrix,
						box.renderMinX(), span[0], box.renderMinZ(),
						box.renderMaxX(), span[1], box.renderMaxZ(),
						r, g, b, alpha);
				any = true;
			}

			if (status == NodeStatus.REMOVED) {
				continue;
			}

			if (config.renderPyramidFootprint) {
				PyramidLayer outline = outlineLayer(plan, node);

				if (outline != null) {
					ShapeRenderer.rectEdges(buffer, matrix,
							outline.minX(), outline.y(), outline.minZ(),
							outline.maxX() + 1.0, outline.maxZ() + 1.0,
							r, g, b, alpha);
					any = true;
				}
			}

			if (config.renderBeaconMarkers && LayerFilter.allows(node.y())) {
				for (int[] pos : plan.beaconPositionsOf(node)) {
					ShapeRenderer.boxEdges(buffer, matrix,
							pos[0], pos[1], pos[2],
							pos[0] + 1.0, pos[1] + 1.0, pos[2] + 1.0,
							r, g, b, alpha);
				}

				any = true;
			}
		}

		any |= WaterRenderer.emitLines(plan, config, matrix, buffer);

		MeshData mesh = buffer.build();

		if (mesh != null && any) {
			Pipelines.draw(config.seeThrough ? Pipelines.LINES_SEE_THROUGH : Pipelines.LINES, mesh);
		} else if (mesh != null) {
			mesh.close();
		}
	}

	// ------------------------------------------------------------------ shared

	/**
	 * Picks the nodes worth drawing this frame: in the plan, within draw distance, and actually
	 * on screen.
	 *
	 * <p>A perimeter can run to a couple of thousand nodes, and drawing every one of them means
	 * rebuilding tens of thousands of vertices per frame for boxes that are behind you. The
	 * frustum test is what keeps that honest; the cap is a last resort for the cases where you
	 * really are looking at the whole thing at once.
	 */
	private static List<GridNode> cull(PerimeterPlan plan, List<GridNode> nodes, Minecraft mc,
			BeaconatorConfig config, Frustum frustum) {
		List<GridNode> visible = new ArrayList<>();
		double limit = (double) config.maxRenderDistance * config.maxRenderDistance;
		boolean editing = PlanManager.editMode();

		for (GridNode node : nodes) {
			NodeStatus status = plan.statusAt(node.key());

			if (status == NodeStatus.REMOVED && !editing) {
				continue;
			}

			double dx = mc.player.getX() - (node.x() + 0.5);
			double dz = mc.player.getZ() - (node.z() + 0.5);

			if (dx * dx + dz * dz > limit) {
				continue;
			}

			if (frustum != null && !frustum.isVisible(bounds(plan, node, mc, config))) {
				continue;
			}

			visible.add(node);
		}

		if (visible.size() > config.maxNodesDrawn) {
			visible.sort(Comparator.comparingDouble(node -> {
				double ndx = mc.player.getX() - node.x();
				double ndz = mc.player.getZ() - node.z();
				return ndx * ndx + ndz * ndz;
			}));
			return visible.subList(0, config.maxNodesDrawn);
		}

		return visible;
	}

	/**
	 * World space box of everything drawn for a node, used for the frustum test.
	 *
	 * <p>The beam has to be in here. Leave it out and a node whose base is off the bottom of the
	 * screen gets culled whole, so the beams blink away the moment you look up at them.
	 */
	private static AABB bounds(PerimeterPlan plan, GridNode node, Minecraft mc, BeaconatorConfig config) {
		CoverageBox box = plan.coverageOf(node);
		double[] span = verticalSpan(box, node, mc, config);
		double minY = Math.min(span[0], node.y() - plan.level());
		double maxY = Math.max(span[1], node.y() + 2.0);

		if (config.showBeams) {
			minY = Math.min(minY, node.y() - Math.max(0, config.beamDepth));
			maxY = Math.max(maxY, node.y() + Math.max(1, config.beamHeight));
		}

		return new AABB(box.renderMinX(), minY, box.renderMinZ(), box.renderMaxX(), maxY, box.renderMaxZ());
	}

	private static boolean isVisible(PerimeterPlan plan, GridNode node, NodeStatus status,
			Minecraft mc, BeaconatorConfig config) {
		return status != NodeStatus.REMOVED || PlanManager.editMode();
	}

	/**
	 * Which pyramid course to outline: the layer you are working on when a layer filter is set,
	 * the base otherwise.
	 */
	private static PyramidLayer outlineLayer(PerimeterPlan plan, GridNode node) {
		List<PyramidLayer> layers = plan.layersOf(node);

		if (!LayerFilter.active()) {
			return layers.get(layers.size() - 1);
		}

		for (int index = layers.size() - 1; index >= 0; index--) {
			if (LayerFilter.allows(layers.get(index).y())) {
				return layers.get(index);
			}
		}

		return null;
	}

	/** Vertical span to draw for a coverage volume, per the chosen style. */
	private static double[] verticalSpan(CoverageBox box, GridNode node, Minecraft mc,
			BeaconatorConfig config) {
		int thickness = Math.max(1, config.slabThickness);
		int slabY = LayerFilter.active() ? LayerFilter.min() : node.y();

		return switch (config.coverageStyle) {
			case SLAB -> new double[] {slabY, slabY + thickness};
			case FLOOR -> new double[] {box.renderMinY(), box.renderMinY() + thickness};
			case FULL -> new double[] {box.renderMinY(), box.renderMaxY(mc.level.getMaxY())};
		};
	}

	private static int colorFor(NodeStatus status, boolean hovered, BeaconatorConfig config) {
		return colorFor(status, hovered, config, null);
	}

	/**
	 * Colour of a node. Pending nodes drift from white towards green as their blocks go in, so a
	 * half built pyramid reads differently from one nobody has touched.
	 */
	private static int colorFor(NodeStatus status, boolean hovered, BeaconatorConfig config, NodeKey key) {
		if (hovered) {
			return config.colorHover;
		}

		// Three states you can read at a glance instead of a gradient you have to interpret:
		// untouched, started, and actually finished.
		// Excluded nodes get built too, pyramid and marker and all, so they need the same three
		// way read. Muted towards their own grey, so they still say "not one of ours" at a
		// glance: leaving them flat grey meant there was no way to tell a finished one from an
		// untouched one, which is half the perimeter on a big site.
		boolean excluded = status == NodeStatus.EXCLUDED;

		if (config.showProgressColor && key != null && status != NodeStatus.REMOVED
				&& (!excluded || config.shadeExcluded)) {

			if (xyz.w4ve.beaconator.client.scan.ScanCache.finished(key)) {
				return excluded ? config.colorExcludedDone : config.colorPlaced;
			}

			if (xyz.w4ve.beaconator.client.scan.ScanCache.partial(key)) {
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
}
