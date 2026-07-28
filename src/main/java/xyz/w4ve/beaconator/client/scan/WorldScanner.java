package xyz.w4ve.beaconator.client.scan;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import xyz.w4ve.beaconator.model.GridNode;
import xyz.w4ve.beaconator.model.MaterialTally;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.model.PyramidLayer;

/**
 * Compares the plan against the world to work out what is already built.
 *
 * <p>Only chunks the client has loaded can be read, so nodes past view distance come back as
 * unloaded rather than as empty. Reporting them as missing would be a lie, and would flip
 * finished nodes back to pending every time you walked away.
 */
public final class WorldScanner {
	private static final Map<String, Block> BLOCK_CACHE = new HashMap<>();

	private WorldScanner() {
	}

	public static NodeScan scanNode(Level level, PerimeterPlan plan, GridNode node) {
		if (!chunksLoaded(level, plan, node)) {
			return NodeScan.UNLOADED;
		}

		Counter counter = new Counter(level);
		plan.forEachBlock(node, counter);

		return new NodeScan(true, counter.expected, counter.found,
				counter.beaconsExpected, counter.beaconsFound, counter.missing);
	}

	/** Checks the corners of the pyramid base, which is the widest part of a node. */
	private static boolean chunksLoaded(Level level, PerimeterPlan plan, GridNode node) {
		var layers = plan.layersOf(node);
		PyramidLayer base = layers.get(layers.size() - 1);

		return level.hasChunk(base.minX() >> 4, base.minZ() >> 4)
				&& level.hasChunk(base.maxX() >> 4, base.minZ() >> 4)
				&& level.hasChunk(base.minX() >> 4, base.maxZ() >> 4)
				&& level.hasChunk(base.maxX() >> 4, base.maxZ() >> 4);
	}

	public static Block block(String id) {
		return BLOCK_CACHE.computeIfAbsent(id, key -> {
			ResourceLocation location = ResourceLocation.tryParse(key);
			return location == null ? null : BuiltInRegistries.BLOCK.get(location);
		});
	}

	private static final class Counter implements xyz.w4ve.beaconator.model.BlockSink {
		private final Level level;
		private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		private final MaterialTally missing = new MaterialTally();
		private int expected;
		private int found;
		private int beaconsExpected;
		private int beaconsFound;

		private Counter(Level level) {
			this.level = level;
		}

		@Override
		public void accept(int x, int y, int z, String blockId) {
			expected++;
			boolean isBeacon = PerimeterPlan.BEACON_BLOCK.equals(blockId);

			if (isBeacon) {
				beaconsExpected++;
			}

			Block wanted = block(blockId);

			if (wanted == null) {
				return;
			}

			cursor.set(x, y, z);
			BlockState state = level.getBlockState(cursor);

			if (matches(state, wanted, blockId)) {
				found++;

				if (isBeacon) {
					beaconsFound++;
				}
			} else {
				missing.add(blockId, 1);
			}
		}

		/**
		 * Whether what is in the world counts as the block the plan asked for.
		 *
		 * <p>Pyramids get the vanilla rule rather than an exact match: a base of mixed iron, gold,
		 * diamond, emerald and netherite powers a beacon just the same, and plenty of perimeters
		 * are built out of whatever was to hand. The configured block is still what assisted
		 * placement puts down and what the material list counts as missing.
		 */
		private boolean matches(BlockState state, Block wanted, String blockId) {
			if (state.is(wanted)) {
				return true;
			}

			boolean pyramidBlock = !PerimeterPlan.BEACON_BLOCK.equals(blockId)
					&& wanted.defaultBlockState().is(BlockTags.BEACON_BASE_BLOCKS);
			return pyramidBlock && state.is(BlockTags.BEACON_BASE_BLOCKS);
		}
	}
}
