package xyz.w4ve.beaconator.model;

/**
 * The bit array Litematica stores block state indices in.
 *
 * <p>Values are packed back to back with no padding, so one value can straddle two longs. That
 * is the pre 1.16 packing style, not vanilla's current {@code PackedBitStorage}, and getting it
 * wrong shifts an entire schematic by a few bits. Hence the tests.
 */
public final class BitPacking {
	private BitPacking() {
	}

	/** Bits per entry for a palette of the given size. Litematica never goes below two. */
	public static int bitsFor(int paletteSize) {
		return Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));
	}

	public static long[] pack(int[] indices, int bits) {
		long totalBits = (long) indices.length * bits;
		long[] packed = new long[(int) ((totalBits + 63) / 64)];
		long mask = bits == 64 ? -1L : (1L << bits) - 1L;

		for (int index = 0; index < indices.length; index++) {
			long start = (long) index * bits;
			int startLong = (int) (start >> 6);
			int offset = (int) (start & 63);
			long value = indices[index] & mask;

			packed[startLong] |= value << offset;

			int endLong = (int) ((start + bits - 1) >> 6);

			if (endLong != startLong) {
				packed[endLong] |= value >>> (64 - offset);
			}
		}

		return packed;
	}

	/** Reads one entry, or -1 when the array is shorter than the index claims. */
	public static int read(long[] packed, int index, int bits) {
		long mask = bits == 64 ? -1L : (1L << bits) - 1L;
		long start = (long) index * bits;
		int startLong = (int) (start >> 6);
		int offset = (int) (start & 63);
		int endLong = (int) ((start + bits - 1) >> 6);

		if (startLong >= packed.length) {
			return -1;
		}

		if (startLong == endLong) {
			return (int) ((packed[startLong] >>> offset) & mask);
		}

		if (endLong >= packed.length) {
			return -1;
		}

		return (int) (((packed[startLong] >>> offset) | (packed[endLong] << (64 - offset))) & mask);
	}
}
