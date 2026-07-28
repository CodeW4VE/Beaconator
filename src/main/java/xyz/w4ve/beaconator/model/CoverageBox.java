package xyz.w4ve.beaconator.model;

/**
 * The volume a beacon actually applies its effects in, in block coordinates.
 *
 * <p>This mirrors vanilla {@code BeaconBlockEntity.applyEffects} on 1.21, which builds
 * the box as:
 *
 * <pre>
 *   double d = levels * 10 + 10;
 *   AABB box = new AABB(pos).inflate(d).expandTowards(0.0, level.getHeight(), 0.0);
 *   level.getEntitiesOfClass(Player.class, box);
 * </pre>
 *
 * <p>Two consequences worth spelling out, because they are easy to get wrong:
 *
 * <ul>
 *   <li>Horizontally the box spans {@code 2r + 1} blocks, from {@code beacon - r} to
 *       {@code beacon + r} inclusive. Inflating by {@code r} a box that is already one
 *       block wide is what produces the odd side length.
 *   <li>Vertically it is <b>not</b> symmetric. It reaches {@code r} blocks below the
 *       beacon, but upwards vanilla expands it by a whole world height, so in practice
 *       it is unbounded above. Drawing a cube would misreport exactly the axis that
 *       matters while digging.
 * </ul>
 *
 * <p>Player hitboxes are 0.6 blocks wide and 1.8 tall and vanilla only needs an
 * intersection, so the true edges are a fraction of a block more generous than the
 * numbers here. This class reports the conservative, guaranteed coverage.
 */
public final class CoverageBox {
	private final int minX;
	private final int maxX;
	private final int minZ;
	private final int maxZ;
	/** Lowest block covered. Everything above is covered too. */
	private final int minY;
	private final int range;

	private CoverageBox(int minX, int maxX, int minZ, int maxZ, int minY, int range) {
		this.minX = minX;
		this.maxX = maxX;
		this.minZ = minZ;
		this.maxZ = maxZ;
		this.minY = minY;
		this.range = range;
	}

	/** Horizontal reach in blocks for a pyramid of {@code level} layers: {@code 10 * level + 10}. */
	public static int rangeFor(int level) {
		if (level < 1 || level > 4) {
			throw new IllegalArgumentException("Beacon pyramid level must be 1..4, got " + level);
		}

		return 10 * level + 10;
	}

	/** Side length in blocks of the covered square for a given pyramid level: {@code 2r + 1}. */
	public static int sideFor(int level) {
		return 2 * rangeFor(level) + 1;
	}

	/** Coverage of a single beacon sitting at the given block position. */
	public static CoverageBox forBeacon(int x, int y, int z, int level) {
		int r = rangeFor(level);
		return new CoverageBox(x - r, x + r, z - r, z + r, y - r, r);
	}

	/**
	 * Combined coverage of a row of {@code beacons} beacons starting at the given position
	 * and growing towards positive {@code axis}. All of them share one pyramid, so they all
	 * run at the same level.
	 */
	public static CoverageBox forRow(int x, int y, int z, int level, int beacons, RowAxis axis) {
		if (beacons < 1) {
			throw new IllegalArgumentException("A node needs at least one beacon, got " + beacons);
		}

		int r = rangeFor(level);
		int extra = beacons - 1;
		int minX = x - r;
		int maxX = x + r + (axis == RowAxis.X ? extra : 0);
		int minZ = z - r;
		int maxZ = z + r + (axis == RowAxis.Z ? extra : 0);
		return new CoverageBox(minX, maxX, minZ, maxZ, y - r, r);
	}

	public int minX() {
		return minX;
	}

	public int maxX() {
		return maxX;
	}

	public int minZ() {
		return minZ;
	}

	public int maxZ() {
		return maxZ;
	}

	/** Lowest covered block. Coverage is unbounded above this. */
	public int minY() {
		return minY;
	}

	public int range() {
		return range;
	}

	public int width() {
		return maxX - minX + 1;
	}

	public int depth() {
		return maxZ - minZ + 1;
	}

	public boolean coversBlock(int x, int y, int z) {
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY;
	}

	/** True when both boxes share at least one block column. */
	public boolean overlaps(CoverageBox other) {
		return minX <= other.maxX && maxX >= other.minX && minZ <= other.maxZ && maxZ >= other.minZ;
	}

	/** Render edge: the box drawn in world runs from {@code minX} to {@code maxX + 1}. */
	public double renderMinX() {
		return minX;
	}

	public double renderMaxX() {
		return maxX + 1.0;
	}

	public double renderMinZ() {
		return minZ;
	}

	public double renderMaxZ() {
		return maxZ + 1.0;
	}

	public double renderMinY() {
		return minY;
	}

	/**
	 * Top edge to draw. Coverage is unbounded upwards, so the caller passes the world
	 * ceiling (or any cap it prefers) and gets that back.
	 */
	public double renderMaxY(double worldTop) {
		return Math.max(worldTop, minY + 1.0);
	}

	@Override
	public String toString() {
		return "CoverageBox[x=" + minX + ".." + maxX + ", z=" + minZ + ".." + maxZ + ", y>=" + minY + "]";
	}
}
