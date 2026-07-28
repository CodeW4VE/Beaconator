package xyz.w4ve.beaconator.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BitPackingTest {
	@Test
	@DisplayName("Bit width follows the palette size, with a floor of two")
	void bitWidths() {
		assertEquals(2, BitPacking.bitsFor(1));
		assertEquals(2, BitPacking.bitsFor(2));
		assertEquals(2, BitPacking.bitsFor(4));
		assertEquals(3, BitPacking.bitsFor(5));
		assertEquals(3, BitPacking.bitsFor(8));
		assertEquals(4, BitPacking.bitsFor(9));
		assertEquals(8, BitPacking.bitsFor(256));
		assertEquals(9, BitPacking.bitsFor(257));
	}

	@Test
	@DisplayName("Values survive packing, including the ones straddling two longs")
	void roundTrip() {
		Random random = new Random(20260727L);

		for (int paletteSize : new int[] {2, 4, 5, 7, 16, 17, 64, 300}) {
			int bits = BitPacking.bitsFor(paletteSize);
			int[] values = new int[1000];

			for (int index = 0; index < values.length; index++) {
				values[index] = random.nextInt(paletteSize);
			}

			long[] packed = BitPacking.pack(values, bits);

			for (int index = 0; index < values.length; index++) {
				assertEquals(values[index], BitPacking.read(packed, index, bits),
						"palette " + paletteSize + ", index " + index);
			}
		}
	}

	@Test
	@DisplayName("A 4 entry palette packs four values per byte")
	void packedSize() {
		int[] values = new int[64];
		long[] packed = BitPacking.pack(values, 2);
		assertEquals(2, packed.length);
	}
}
