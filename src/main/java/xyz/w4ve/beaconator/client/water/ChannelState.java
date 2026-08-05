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
	 * <p>Three colours for the same three ideas the pyramids use, untouched, started, finished bar
	 * one step, but the channel's own, set on the Water tab next to the rest of the channel. They
	 * used to be the node colours: the same yellow meant two different things and the button that
	 * claimed to colour the channel only ever moved one third of it.
	 */
	public int colour(xyz.w4ve.beaconator.config.BeaconatorConfig config) {
		return switch (this) {
			case OPEN -> config.colorWaterOpen;
			case FLOORED -> config.colorWaterFloored;
			default -> config.colorWater;
		};
	}
}
