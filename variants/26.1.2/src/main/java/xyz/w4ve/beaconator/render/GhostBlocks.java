package xyz.w4ve.beaconator.render;

/**
 * The 26.1 stand in: no ghost blocks here, and the marks stay marks.
 *
 * <p>Drawing a real block means handing a block state to the game and letting it put the model on
 * the screen for you. Up to 1.21.11 that is one call. In 26.1 there is no such call any more:
 * {@code BlockRenderDispatcher} is gone, block geometry is extracted and uploaded through a
 * different path entirely, and reproducing it here would be a port of the thing rather than a use
 * of it. That is the same rewrite that keeps 26.2 unshipped.
 *
 * <p>So on 26.x a plate is drawn the way it always was, as a mark hovering over its square, and
 * {@link #available()} says so out loud rather than leaving a hole where a block should be. The
 * numbers, the border rule and everything else in the water section are the same on every version:
 * this is only about how a plate looks when you are standing next to it.
 */
public final class GhostBlocks {
	private GhostBlocks() {
	}

	/** False here, so the mark is never hidden in favour of a block that is not coming. */
	public static boolean available() {
		return false;
	}
}
