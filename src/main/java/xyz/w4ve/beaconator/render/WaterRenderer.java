package xyz.w4ve.beaconator.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import xyz.w4ve.beaconator.client.water.WaterCache;
import xyz.w4ve.beaconator.client.water.WaterStretch;
import xyz.w4ve.beaconator.config.BeaconatorConfig;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.model.water.WaterFittings;
import xyz.w4ve.beaconator.model.water.WaterPlan;
import xyz.w4ve.beaconator.model.water.WaterSegment;

/**
 * The water lines in the world: one long box per run, at the layer they will be dug on.
 *
 * <p>Only ever emits vertices into a buffer somebody else opened and draws. That is deliberate:
 * how a mesh reaches the screen changed twice between 1.21 and 1.21.11, and every version of
 * {@link PerimeterRenderer} carries its own copy of that. Emitting into a buffer is the one thing
 * that reads the same in all of them, so this file ships unchanged everywhere.
 *
 * <p>Boxes per run, not per block. A ten thousand block channel drawn a block at a time is sixty
 * thousand quads a frame for something you look at from four hundred blocks up.
 */
public final class WaterRenderer {
	/**
	 * How close you have to be for a fitting to be drawn where it goes rather than as a mark
	 * saying one goes somewhere here.
	 *
	 * <p>Both are needed and neither does the other's job. A pressure plate is one sixteenth of a
	 * block tall: from the other side of a perimeter it is nothing at all, so far away it stays a
	 * post you can see against the sky. Standing in the trench with a stack of plates in hand the
	 * post is useless, and what you want is the block in its square, which is what
	 * {@link GhostBlocks} draws. Water is a box either way: a fluid cannot go through the block
	 * renderer, so up close it becomes a block sized one at the height water sits at.
	 */
	private static final double GHOST_RANGE = 48.0;

	/** Water sits a fifteenth short of the top of its block. */
	private static final double WATER_HEIGHT = 0.875;

	private WaterRenderer() {
	}

	/** True when a fitting is close enough to be worth drawing where it actually goes. */
	static boolean near(WaterFittings.Fitting fitting, double eyeX, double eyeZ) {
		double dx = eyeX - (fitting.x() + 0.5);
		double dz = eyeZ - (fitting.z() + 0.5);
		return dx * dx + dz * dz <= GHOST_RANGE * GHOST_RANGE;
	}

	/** A source, at the size and height the water will be. */
	private static void ghost(BufferBuilder buffer, Matrix4f matrix, WaterFittings.Fitting fitting,
			double y, double height, int colour, float opacity) {
		ShapeRenderer.boxFaces(buffer, matrix,
				fitting.x(), y, fitting.z(), fitting.x() + 1.0, y + height, fitting.z() + 1.0,
				ShapeRenderer.red(colour), ShapeRenderer.green(colour), ShapeRenderer.blue(colour),
				opacity);
	}

	/** True when there is anything to draw, which is what decides whether a buffer is worth opening. */
	public static boolean wants(PerimeterPlan plan, BeaconatorConfig config) {
		return config.renderWater && plan != null && !plan.water().isEmpty();
	}

	/**
	 * The channel itself: a flat box a block tall sitting where the water goes.
	 *
	 * @return true when anything was emitted
	 */
	public static boolean emitFaces(PerimeterPlan plan, BeaconatorConfig config, Matrix4f matrix,
			BufferBuilder buffer) {
		if (!wants(plan, config)) {
			return false;
		}

		WaterPlan water = plan.water();
		double y = water.spec().waterY();
		double half = Math.clamp(config.waterWidth, 0.1f, 1.0f) / 2.0;
		double limit = (double) config.maxRenderDistance * config.maxRenderDistance;
		Minecraft mc = Minecraft.getInstance();
		double eyeX = mc.player == null ? 0 : mc.player.getX();
		double eyeZ = mc.player == null ? 0 : mc.player.getZ();
		boolean any = false;

		for (WaterSegment run : water.runs()) {
			if (mc.player != null && distanceSquared(run, eyeX, eyeZ) > limit) {
				continue;
			}

			boolean bad = WaterCache.blocked(plan, run);

			// Drawn a stretch at a time rather than a run at a time, because a run is dug in
			// stretches: what is already running has to go dark while the rest of the same line
			// stays lit, or the map stops telling you anything the day you start digging.
			for (WaterStretch stretch : WaterStretch.of(run)) {
				if (stretch.state().done()) {
					continue;
				}

				int colour = bad ? config.colorWaterBad : stretch.state().colour(config);
				double minX = Math.min(stretch.x1(), stretch.x2()) + 0.5 - half;
				double maxX = Math.max(stretch.x1(), stretch.x2()) + 0.5 + half;
				double minZ = Math.min(stretch.z1(), stretch.z2()) + 0.5 - half;
				double maxZ = Math.max(stretch.z1(), stretch.z2()) + 0.5 + half;

				ShapeRenderer.boxFaces(buffer, matrix, minX, y, minZ, maxX, y + 1.0, maxZ,
						ShapeRenderer.red(colour), ShapeRenderer.green(colour),
						ShapeRenderer.blue(colour), config.waterOpacity);
				any = true;
			}
		}

		// The fittings, when they are asked for: a source is a post in the channel, a stop is a flat
		// pad across it. Small, because they are a proposal sitting on top of a real plan.
		if (config.showFittings) {
			WaterFittings fittings = WaterCache.fittings(plan);
			int sourceColour = config.colorWater;

			for (WaterFittings.Fitting source : fittings.sources()) {
				// Poured already: nothing left to say about it. Only asked of the ones close
				// enough to be reading the world for anyway.
				if (Fittings.done(mc, source, (int) y, true)) {
					continue;
				}

				if (near(source, eyeX, eyeZ)) {
					// The block itself, where a source ends up: full width, and the height water
					// actually sits at. Close enough to build from, so it is drawn to be built
					// from rather than to be spotted.
					ghost(buffer, matrix, source, y, WATER_HEIGHT, sourceColour,
							config.waterOpacity);
				} else {
					ShapeRenderer.boxFaces(buffer, matrix,
							source.x() + 0.3, y, source.z() + 0.3,
							source.x() + 0.7, y + 1.6, source.z() + 0.7,
							ShapeRenderer.red(sourceColour), ShapeRenderer.green(sourceColour),
							ShapeRenderer.blue(sourceColour), config.waterOpacity);
				}

				any = true;
			}

			// Close up a plate is drawn as the block itself by GhostBlocks, so all this has to do
			// is put a mark where one goes for as long as you are too far away to see a block one
			// sixteenth of a block tall.
			for (WaterFittings.Fitting stop : fittings.stops()) {
				if (GhostBlocks.available() && near(stop, eyeX, eyeZ)) {
					continue;
				}

				if (Fittings.done(mc, stop, (int) y, false)) {
					continue;
				}

				int stopColour = config.colorWaterDrain;
				ShapeRenderer.boxFaces(buffer, matrix,
						stop.x() + 0.1, y + 0.9, stop.z() + 0.1,
						stop.x() + 0.9, y + 1.05, stop.z() + 0.9,
						ShapeRenderer.red(stopColour), ShapeRenderer.green(stopColour),
						ShapeRenderer.blue(stopColour), 0.9f);
				any = true;
			}
		}

		// Chunk borders with something on them that momentum cannot survive. Drawn whether or not
		// the fittings are shown, and tall, because unlike a bucket in the wrong place these are
		// worth the walk out there before the ice goes in.
		WaterFittings marks = WaterCache.fittings(plan);

		if (marks != null) {
			int badColour = config.colorWaterBad;

			for (int[] warning : marks.warnings()) {
				ShapeRenderer.boxFaces(buffer, matrix,
						warning[0] + 0.35, y, warning[1] + 0.35,
						warning[0] + 0.65, y + 3.0, warning[1] + 0.65,
						ShapeRenderer.red(badColour), ShapeRenderer.green(badColour),
						ShapeRenderer.blue(badColour), config.waterOpacity);
				any = true;
			}
		}

		// The drain, standing up out of the channel so it can be found from across the site. It is
		// the one block of the whole network whose exact position matters to anything but the maths.
		int[] drain = WaterCache.drain(plan);

		if (drain != null) {
			int colour = config.colorWaterDrain;
			ShapeRenderer.boxFaces(buffer, matrix,
					drain[0] + 0.15, y, drain[1] + 0.15,
					drain[0] + 0.85, y + 6.0, drain[1] + 0.85,
					ShapeRenderer.red(colour), ShapeRenderer.green(colour), ShapeRenderer.blue(colour),
					config.waterOpacity);
			any = true;
		}

		return any;
	}

	/** Edges of the same boxes, so a run stays readable where two of them overlap. */
	public static boolean emitLines(PerimeterPlan plan, BeaconatorConfig config, Matrix4f matrix,
			BufferBuilder buffer) {
		if (!wants(plan, config)) {
			return false;
		}

		WaterPlan water = plan.water();
		double y = water.spec().waterY();
		double limit = (double) config.maxRenderDistance * config.maxRenderDistance;
		Minecraft mc = Minecraft.getInstance();
		double eyeX = mc.player == null ? 0 : mc.player.getX();
		double eyeZ = mc.player == null ? 0 : mc.player.getZ();
		boolean any = false;

		// The edge stays stronger than the face it outlines, which is what keeps two overlapping
		// runs apart, but it follows the opacity setting rather than ignoring it. Pinned at one, a
		// channel turned down to barely there kept a solid outline and nothing looked any fainter.
		float edgeAlpha = Math.min(1.0f, config.waterOpacity * 1.6f);

		for (WaterSegment run : water.runs()) {
			if (mc.player != null && distanceSquared(run, eyeX, eyeZ) > limit) {
				continue;
			}

			boolean bad = WaterCache.blocked(plan, run);

			for (WaterStretch stretch : WaterStretch.of(run)) {
				if (stretch.state().done()) {
					continue;
				}

				int colour = bad ? config.colorWaterBad : stretch.state().colour(config);
				ShapeRenderer.rectEdges(buffer, matrix,
						Math.min(stretch.x1(), stretch.x2()), y,
						Math.min(stretch.z1(), stretch.z2()),
						Math.max(stretch.x1(), stretch.x2()) + 1.0,
						Math.max(stretch.z1(), stretch.z2()) + 1.0,
						ShapeRenderer.red(colour), ShapeRenderer.green(colour),
						ShapeRenderer.blue(colour), edgeAlpha);
				any = true;
			}
		}

		// The ghosts get their edges too, and they are what makes them readable: a face at half
		// opacity inside a trench you are standing in is a smudge, and the twelve lines of a box
		// are what tell you which square it is in.
		if (config.showFittings) {
			WaterFittings fittings = WaterCache.fittings(plan);

			if (fittings != null) {
				for (WaterFittings.Fitting source : fittings.sources()) {
					if (near(source, eyeX, eyeZ)) {
						ghostEdges(buffer, matrix, source, y, WATER_HEIGHT, config.colorWater,
								edgeAlpha);
						any = true;
					}
				}

				// The plate is a real block up close, and a real block outlined in the colour of
				// the thing it is not would only make it harder to read.
			}
		}

		return any;
	}

	private static void ghostEdges(BufferBuilder buffer, Matrix4f matrix,
			WaterFittings.Fitting fitting, double y, double height, int colour, float alpha) {
		ShapeRenderer.boxEdges(buffer, matrix,
				fitting.x(), y, fitting.z(), fitting.x() + 1.0, y + height, fitting.z() + 1.0,
				ShapeRenderer.red(colour), ShapeRenderer.green(colour), ShapeRenderer.blue(colour),
				alpha);
	}

	/** Distance from the player to the nearest point of a run, squared. */
	private static double distanceSquared(WaterSegment run, double x, double z) {
		double nearestX = Math.clamp(x, Math.min(run.x1(), run.x2()), Math.max(run.x1(), run.x2()));
		double nearestZ = Math.clamp(z, Math.min(run.z1(), run.z2()), Math.max(run.z1(), run.z2()));
		double dx = x - nearestX;
		double dz = z - nearestZ;
		return dx * dx + dz * dz;
	}
}
