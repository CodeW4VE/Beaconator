package xyz.w4ve.beaconator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import xyz.w4ve.beaconator.client.water.WaterCache;
import xyz.w4ve.beaconator.config.BeaconatorConfig;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.model.water.WaterFittings;

/**
 * The plates, drawn as the block rather than as a box the shape of one.
 *
 * <p>Everything else this mod draws is its own geometry in its own colours, because a coverage
 * volume is a hundred blocks across and a wireframe of one is the only way to see it. A pressure
 * plate is not like that. It is a real block that goes in a real square, and when you are standing
 * in the trench with a stack of them in your hand what you want is to see the thing you are about
 * to place, the way a schematic mod shows it. So this one asks Minecraft to render the block.
 *
 * <p>Water is the exception and cannot be done this way: fluids do not go through the block
 * renderer at all, they are meshed against the world by the liquid renderer, and a source that is
 * not in the world yet has nothing to be meshed against. Those stay a translucent box in
 * {@link WaterRenderer}, which is close enough to what a source looks like anyway.
 */
public final class GhostBlocks {
	private static final BlockState PLATE = Blocks.STONE_PRESSURE_PLATE.defaultBlockState();

	/** Faint enough to read as a plan, solid enough to see against grey stone at eye level. */
	private static final float ALPHA = 0.55f;

	private GhostBlocks() {
	}

	/**
	 * Whether this version of the game can be asked to draw a block for us.
	 *
	 * <p>True everywhere the single block call exists, which is 1.21 through 1.21.11. The 26.x copy
	 * of this file says false, and the marks stay marks there.
	 */
	public static boolean available() {
		return true;
	}

	/**
	 * Draws a plate in every square that needs one nearby.
	 *
	 * <p>Called with the matrix already translated by the camera, before the overlay turns the
	 * depth mask and blending off: these are opaque blocks and want the plain state the game
	 * renders everything else with.
	 */
	public static void render(PerimeterPlan plan, BeaconatorConfig config, PoseStack matrices,
			MultiBufferSource consumers, Vec3 camera) {
		if (consumers == null || !config.enabled || !config.showFittings
				|| !WaterRenderer.wants(plan, config)) {
			return;
		}

		WaterFittings fittings = WaterCache.fittings(plan);

		if (fittings == null) {
			return;
		}

		int y = plan.water().spec().waterY();
		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null) {
			return;
		}

		MultiBufferSource ghostly = ghostly(consumers);
		boolean any = false;

		for (WaterFittings.Fitting plate : fittings.stops()) {
			if (!WaterRenderer.near(plate, camera.x, camera.z)) {
				continue;
			}

			// A ghost is a thing that is missing. The moment you put the plate down the ghost has
			// to go, or the schematic says the same thing whether you have done it or not, which
			// is the one job it has. Same rule the channel already follows: a stretch with water
			// in it stops being drawn.
			if (Fittings.done(mc, plate, y, false)) {
				continue;
			}

			matrices.pushPose();
			matrices.translate(plate.x(), y, plate.z());
			mc.getBlockRenderer().renderSingleBlock(PLATE, matrices, ghostly,
					LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			matrices.popPose();
			any = true;
		}

		// Straight away, rather than whenever the frame gets around to it: the overlay is about to
		// turn the depth mask off and drop into its own shader, and a batch flushed in the middle
		// of that comes out inside out.
		if (any && consumers instanceof MultiBufferSource.BufferSource source) {
			source.endBatch();
		}
	}

	/**
	 * Everything the model asks to draw, sent to the translucent layer with its alpha scaled.
	 *
	 * <p>Both halves are needed. Scaling the colour alone does nothing on the solid layer, which
	 * does not blend at all, and moving to the translucent layer alone leaves the block opaque
	 * because the model's own colours are. Together they are what makes it read as a plan rather
	 * than as a plate somebody already placed, which is the whole point of drawing it.
	 */
	private static MultiBufferSource ghostly(MultiBufferSource consumers) {
		// The layer the game itself uses for a block that has to be see through in the world, which
		// is the one a piston is pushing. Not the chunk translucent layer: that one belongs to the
		// terrain batch and moved out of RenderType entirely in 1.21.11.
		return layer -> new Ghost(consumers.getBuffer(RenderType.translucentMovingBlock()));
	}

	/** A vertex consumer that dims every colour it is handed. */
	private record Ghost(VertexConsumer delegate) implements VertexConsumer {
		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			delegate.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			delegate.setColor(red, green, blue, (int) (alpha * ALPHA));
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			delegate.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			delegate.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			delegate.setUv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			delegate.setNormal(x, y, z);
			return this;
		}
	}
}
