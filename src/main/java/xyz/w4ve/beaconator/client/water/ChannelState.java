package xyz.w4ve.beaconator.client.water;

/**
 * What the world says about one block of channel.
 *
 * <p>Four states rather than done and not done, because a channel is built in three passes and
 * knowing which pass a stretch is waiting for is the whole point: you dig the tunnel, you floor it
 * with ice, and you put the water in. A stretch that is dug and unfloored looks exactly like one
 * nobody has touched from any distance, which is how you end up walking a kilometre of finished
 * tunnel wondering where the ice ran out.
 */
public enum ChannelState {
	/** Too far away to read. Never reported as missing: that would unbuild what you dug. */
	UNKNOWN,
	/** Still solid. This is what is left to mine. */
	SOLID,
	/** Dug out, no ice under it yet. */
	OPEN,
	/** Ice is down, waiting for water. */
	FLOORED,
	/** Water is in. Nothing left to do here, so nothing is drawn. */
	FLOWING;

	public boolean done() {
		return this == FLOWING;
	}

	/**
	 * How this state is drawn, in the world and on the map alike.
	 *
	 * <p>The same three colours the pyramids use for the same three ideas: untouched, started,
	 * finished bar one step. One thing to learn instead of two.
	 */
	public int colour(xyz.w4ve.beaconator.config.BeaconatorConfig config) {
		return switch (this) {
			case OPEN -> config.colorPartial;
			case FLOORED -> config.colorPlaced;
			default -> config.colorWater;
		};
	}
}
