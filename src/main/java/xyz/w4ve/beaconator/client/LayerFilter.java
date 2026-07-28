package xyz.w4ve.beaconator.client;

/**
 * Restricts the plan to a slice of the world along Y, the way schematic mods do layers.
 *
 * <p>Placement and the pyramid outlines both honour it, so you can work one course at a time
 * without the layer above getting in the way.
 */
public final class LayerFilter {
	public enum Mode {
		ALL,
		SINGLE,
		RANGE
	}

	private static Mode mode = Mode.ALL;
	private static int min;
	private static int max;

	private LayerFilter() {
	}

	public static Mode mode() {
		return mode;
	}

	public static int min() {
		return min;
	}

	public static int max() {
		return max;
	}

	public static boolean active() {
		return mode != Mode.ALL;
	}

	public static boolean allows(int y) {
		return switch (mode) {
			case ALL -> true;
			case SINGLE -> y == min;
			case RANGE -> y >= min && y <= max;
		};
	}

	public static void all() {
		mode = Mode.ALL;
	}

	public static void single(int y) {
		mode = Mode.SINGLE;
		min = y;
		max = y;
	}

	public static void range(int from, int to) {
		mode = Mode.RANGE;
		min = Math.min(from, to);
		max = Math.max(from, to);
	}

	/** Moves the active layer up or down, which is how you walk a pyramid course by course. */
	public static void shift(int amount) {
		switch (mode) {
			case ALL -> {
			}
			case SINGLE -> single(min + amount);
			case RANGE -> range(min + amount, max + amount);
		}
	}

	public static String describe() {
		return switch (mode) {
			case ALL -> Lang.t("layers.all");
			case SINGLE -> Lang.t("layers.single", min);
			case RANGE -> Lang.t("layers.range", min, max);
		};
	}
}
