package xyz.w4ve.beaconator.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import xyz.w4ve.beaconator.model.water.WaterFittings;

/**
 * Whether a fitting still needs doing, asked of the world rather than of the plan.
 *
 * <p>The point of drawing where a source or a plate goes is that it stops being drawn once it is
 * there. Otherwise a finished channel looks exactly like one nobody has started, which is the one
 * job the marks have, and the same rule a dug stretch of channel already follows.
 *
 * <p>Two ways of being done. The obvious one is that the block is already in the world. The other
 * is that <b>the water here is already moving the right way</b>: if you built the current out of
 * your own sources and cuts and it is running towards the drain through this block, then whatever
 * the mod would have put here is a suggestion about a problem that no longer exists. Asking the
 * fluid which way it is going is the only honest way to know that, and it is why a fitting carries
 * its own downstream direction.
 */
final class Fittings {
	private static final BlockPos.MutableBlockPos AT = new BlockPos.MutableBlockPos();

	private Fittings() {
	}

	/**
	 * @param water true to ask about a source, false to ask about a plate
	 * @return true when there is nothing left to do at this block
	 */
	static boolean done(Minecraft mc, WaterFittings.Fitting fitting, int y, boolean water) {
		if (mc.level == null || !mc.level.hasChunk(fitting.x() >> 4, fitting.z() >> 4)) {
			return false;
		}

		// A layer either side as well as the one the plan names. The channel Y is a setting, and a
		// channel dug one layer off it is still the channel you built: a plate sitting there is
		// done, and telling somebody otherwise while they look straight at their own plate is the
		// worst thing this can do.
		for (int layer = y - 1; layer <= y + 1; layer++) {
			AT.set(fitting.x(), layer, fitting.z());

			if (water) {
				if (mc.level.getFluidState(AT).isSource()) {
					return true;
				}
			} else if (mc.level.getBlockState(AT).getBlock() instanceof BasePressurePlateBlock) {
				return true;
			}
		}

		AT.set(fitting.x(), y, fitting.z());
		return flowingToTheDrain(mc, fitting);
	}

	/** True when the water already in this block is pushing the way the network wants it to. */
	private static boolean flowingToTheDrain(Minecraft mc, WaterFittings.Fitting fitting) {
		if (fitting.dx() == 0 && fitting.dz() == 0) {
			return false;
		}

		FluidState fluid = mc.level.getFluidState(AT);

		if (fluid.isEmpty()) {
			return false;
		}

		Vec3 flow = fluid.getFlow(mc.level, AT);
		return flow.x * fitting.dx() + flow.z * fitting.dz() > 0.0;
	}
}
