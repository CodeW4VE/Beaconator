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
	private WaterRenderer() {
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
			WaterFittings fittings = WaterFittings.of(water);
			int sourceColour = config.colorWater;
			int stopColour = config.colorWaterDrain;

			for (int[] source : fittings.sources()) {
				ShapeRenderer.boxFaces(buffer, matrix,
						source[0] + 0.3, y, source[1] + 0.3,
						source[0] + 0.7, y + 1.6, source[1] + 0.7,
						ShapeRenderer.red(sourceColour), ShapeRenderer.green(sourceColour),
						ShapeRenderer.blue(sourceColour), config.waterOpacity);
				any = true;
			}

			for (int[] stop : fittings.stops()) {
				ShapeRenderer.boxFaces(buffer, matrix,
						stop[0] + 0.1, y + 0.9, stop[1] + 0.1,
						stop[0] + 0.9, y + 1.05, stop[1] + 0.9,
						ShapeRenderer.red(stopColour), ShapeRenderer.green(stopColour),
						ShapeRenderer.blue(stopColour), 0.9f);
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
						ShapeRenderer.blue(colour), 1.0f);
				any = true;
			}
		}

		return any;
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
