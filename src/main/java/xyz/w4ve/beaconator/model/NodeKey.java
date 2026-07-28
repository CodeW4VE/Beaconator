package xyz.w4ve.beaconator.model;

/** Grid coordinates of a node, {@code (0, 0)} being the centre. */
public record NodeKey(int i, int j) {
	@Override
	public String toString() {
		return i + "," + j;
	}

	/** Parses the {@code "i,j"} form used as a key when the plan is saved. */
	public static NodeKey parse(String s) {
		int comma = s.indexOf(',');

		if (comma < 0) {
			throw new IllegalArgumentException("Not a node key: " + s);
		}

		return new NodeKey(
				Integer.parseInt(s.substring(0, comma).trim()),
				Integer.parseInt(s.substring(comma + 1).trim()));
	}
}
