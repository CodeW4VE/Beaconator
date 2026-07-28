package xyz.w4ve.beaconator.client.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.w4ve.beaconator.model.GridDetector;
import xyz.w4ve.beaconator.model.PerimeterPlan;

/**
 * Builds a plan out of a perimeter that is already standing in the world.
 *
 * <p>Beacons are block entities, so the loaded chunks already hold a list of them and there is
 * no need to walk millions of blocks. Whatever is past view distance is simply not seen: grow
 * the grid with the wheel afterwards and the rest of the nodes land where they should.
 */
public final class WorldGridDetector {
	private WorldGridDetector() {
	}

	public record Result(PerimeterPlan plan, int beaconsFound, int chunksRead) {
	}

	public static Result detect(Minecraft mc, String name, int radiusBlocks) {
		ClientLevel level = mc.level;

		if (level == null || mc.player == null) {
			throw new IllegalStateException("Not in a world");
		}

		List<int[]> beacons = new ArrayList<>();
		int centerChunkX = mc.player.blockPosition().getX() >> 4;
		int centerChunkZ = mc.player.blockPosition().getZ() >> 4;
		int radiusChunks = Math.max(1, radiusBlocks >> 4);
		int chunksRead = 0;

		for (int chunkX = centerChunkX - radiusChunks; chunkX <= centerChunkX + radiusChunks; chunkX++) {
			for (int chunkZ = centerChunkZ - radiusChunks; chunkZ <= centerChunkZ + radiusChunks; chunkZ++) {
				if (!level.hasChunk(chunkX, chunkZ)) {
					continue;
				}

				LevelChunk chunk = level.getChunk(chunkX, chunkZ);
				chunksRead++;

				for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
					if (entry.getValue() instanceof BeaconBlockEntity) {
						BlockPos pos = entry.getKey();
						beacons.add(new int[] {pos.getX(), pos.getY(), pos.getZ()});
					}
				}
			}
		}

		if (beacons.isEmpty()) {
			throw new IllegalStateException("No beacons in the loaded chunks around you");
		}

		String pyramidBlock = null;
		String markerBlock = null;
		List<int[]> markers = new ArrayList<>();
		int level4 = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int[] beacon : beacons) {
			cursor.set(beacon[0], beacon[1] + 1, beacon[2]);
			BlockState above = level.getBlockState(cursor);

			if (!above.isAir()) {
				markers.add(new int[] {beacon[0], beacon[1] + 1, beacon[2]});

				if (markerBlock == null) {
					markerBlock = idOf(above);
				}
			}

			cursor.set(beacon[0], beacon[1] - 1, beacon[2]);
			BlockState below = level.getBlockState(cursor);

			if (below.isAir()) {
				continue;
			}

			String belowId = idOf(below);

			if (pyramidBlock == null) {
				pyramidBlock = belowId;
			}

			int depth = 0;

			while (depth < 4) {
				cursor.set(beacon[0], beacon[1] - depth - 1, beacon[2]);

				if (!idOf(level.getBlockState(cursor)).equals(pyramidBlock)) {
					break;
				}

				depth++;
			}

			level4 = Math.max(level4, depth);
		}

		GridDetector.Input input = new GridDetector.Input(beacons, markers, pyramidBlock, markerBlock,
				Math.max(1, level4));
		PerimeterPlan plan = GridDetector.detect(name, level.dimension().location().toString(), input);
		return new Result(plan, beacons.size(), chunksRead);
	}

	private static String idOf(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}
}
