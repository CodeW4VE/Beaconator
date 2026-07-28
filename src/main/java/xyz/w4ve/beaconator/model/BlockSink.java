package xyz.w4ve.beaconator.model;

/**
 * Receives the blocks a plan asks for, one at a time.
 *
 * <p>Callback instead of a list because a full perimeter is around 40k blocks and both the
 * material list and assisted placement walk it often.
 */
@FunctionalInterface
public interface BlockSink {
	void accept(int x, int y, int z, String blockId);
}
