package xyz.w4ve.beaconator.render;

/**
 * The 1.21.11 stand in: no ghost blocks here, and the marks stay marks.
 *
 * <p>Asking the game to draw a block is still one call on this version, but drawing it <i>see
 * through</i> is not. The ghost works by pushing every quad of the model through the layer the
 * game uses for a block a piston is moving, with the alpha scaled on the way past, and both halves
 * of that moved in 1.21.11: the render types are no longer static methods on {@code RenderType},
 * and {@code VertexConsumer} grew a method, so the wrapper does not compile either. An opaque
 * ghost is worse than none at all, because a solid plate sitting in the channel reads as one
 * somebody already placed.
 *
 * <p>So on 1.21.11 a plate is drawn the way it always was, as a mark hovering over its square, and
 * {@link #available()} says so rather than leaving a hole where a block should be. Everything else
 * about the water section, the numbers and the border rule included, is the same on every version.
 * Finding the new home of those two is a job for the same sitting as the 26.x renderer.
 */
public final class GhostBlocks {
	private GhostBlocks() {
	}

	/** False here, so the mark is never hidden in favour of a block that is not coming. */
	public static boolean available() {
		return false;
	}
}
