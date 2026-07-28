package xyz.w4ve.beaconator.model;

/**
 * One layer of a beacon pyramid, as an inclusive block range.
 *
 * <p>{@code depth} is 1 for the layer right under the beacons and grows downwards.
 */
public record PyramidLayer(int depth, int y, int minX, int maxX, int minZ, int maxZ) {
	public int width() {
		return maxX - minX + 1;
	}

	public int length() {
		return maxZ - minZ + 1;
	}

	public int blockCount() {
		return width() * length();
	}
}
