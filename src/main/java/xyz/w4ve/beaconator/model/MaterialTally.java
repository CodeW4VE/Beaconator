package xyz.w4ve.beaconator.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** How many blocks of each kind a plan asks for. Insertion ordered so output is stable. */
public final class MaterialTally {
	private final Map<String, Integer> counts = new LinkedHashMap<>();

	public void add(String blockId, int amount) {
		if (amount == 0) {
			return;
		}

		counts.merge(blockId, amount, Integer::sum);
	}

	public int get(String blockId) {
		return counts.getOrDefault(blockId, 0);
	}

	public Map<String, Integer> counts() {
		return Collections.unmodifiableMap(counts);
	}

	public int total() {
		int total = 0;

		for (int count : counts.values()) {
			total += count;
		}

		return total;
	}

	public boolean isEmpty() {
		return counts.isEmpty();
	}

	@Override
	public String toString() {
		return counts.toString();
	}
}
