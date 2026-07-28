package xyz.w4ve.beaconator.client.map;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import xyz.w4ve.beaconator.client.PlanManager;
import xyz.w4ve.beaconator.io.PlanStore;
import xyz.w4ve.beaconator.model.PerimeterPlan;

/**
 * Holds the map tiles and keeps them fed with terrain.
 *
 * <p>Rasterising runs on the client tick rather than while the screen is open, which is the
 * difference between a map that fills itself in as you fly around and one that only ever shows
 * the patch you were standing on when you opened it.
 */
public final class MapStore {
	/** Tiles kept in memory. Each one is a megabyte of native image. */
	private static final int MAX_TILES = 96;
	/**
	 * Time budget per pass instead of a chunk count.
	 *
	 * <p>A fixed count was the wrong measure: walking around there is nothing to draw and the
	 * budget goes unused, but arriving in a world loads a thousand chunks at once and a small
	 * count takes many seconds to catch up. Fly during those seconds and the chunks behind you
	 * unload before their turn, which leaves exactly the rectangular holes this used to have.
	 */
	private static final long BUDGET_NANOS = 3_000_000L;
	/** Hard ceiling per pass, so a single frame can never be eaten whole. */
	private static final int MAX_CHUNKS_PER_PASS = 256;
	/** How often a chunk that is already drawn gets refreshed, so the perimeter you build shows up. */
	private static final long REFRESH_MS = 45_000L;
	/** How long to wait before retrying a chunk that came back with nothing in it. */
	private static final long EMPTY_RETRY_MS = 1_000L;
	private static final long TICK_MS = 50L;
	/** Blocks of slack around the plan that still get mapped. */
	private static final int MARGIN = 384;

	private static final Map<Long, MapTile> TILES = new HashMap<>();
	private static final Map<Integer, List<int[]>> SPIRALS = new HashMap<>();
	/** Chunks the client has just received, drawn before anything else. */
	private static final ArrayDeque<long[]> FRESH = new ArrayDeque<>();
	private static final int MAX_FRESH = 4096;

	private static String worldId = "";
	private static String dimension = "";
	private static long lastTick;
	private static int chunksThisSession;

	private MapStore() {
	}

	public static int tilesLoaded() {
		return TILES.size();
	}

	public static int chunksDrawn() {
		return chunksThisSession;
	}

	// -------------------------------------------------------------------- tick

	public static void tick(Minecraft minecraft) {
		PerimeterPlan plan = PlanManager.plan();
		Level level = minecraft.level;

		if (plan == null || level == null || minecraft.player == null || !PlanManager.inPlanDimension()) {
			return;
		}

		String currentWorld = PlanManager.worldId();
		String currentDimension = level.dimension().location().toString();

		if (!currentWorld.equals(worldId) || !currentDimension.equals(dimension)) {
			closeAll();
			worldId = currentWorld;
			dimension = currentDimension;
		}

		long now = System.currentTimeMillis();

		if (now - lastTick < TICK_MS) {
			return;
		}

		lastTick = now;
		int[] bounds = plan.coverageBounds();

		if (bounds == null) {
			return;
		}

		long deadlineForFresh = System.nanoTime() + BUDGET_NANOS;

		// Chunks that just arrived go first: waiting for the sweep to reach them is what made
		// the chunk you were walking into show up long after the ones beside it.
		while (!FRESH.isEmpty() && System.nanoTime() < deadlineForFresh) {
			long[] chunk = FRESH.poll();
			int chunkX = (int) chunk[0];
			int chunkZ = (int) chunk[1];

			if (!withinPlan(bounds, chunkX, chunkZ) || !level.hasChunk(chunkX, chunkZ)) {
				continue;
			}

			MapTile tile = tileFor(chunkX, chunkZ, true);

			if (tile != null && tile.rasteriseChunk(level, level.getChunk(chunkX, chunkZ), chunkX, chunkZ)) {
				chunksThisSession++;
			}
		}

		int playerChunkX = minecraft.player.getBlockX() >> 4;
		int playerChunkZ = minecraft.player.getBlockZ() >> 4;
		int radius = Math.max(2, minecraft.options.getEffectiveRenderDistance());
		List<int[]> spiral = spiral(radius);
		List<int[]> stale = new ArrayList<>();
		long deadline = System.nanoTime() + BUDGET_NANOS;
		int drawn = 0;

		for (int[] offset : spiral) {
			if (drawn >= MAX_CHUNKS_PER_PASS || System.nanoTime() > deadline) {
				break;
			}

			int chunkX = playerChunkX + offset[0];
			int chunkZ = playerChunkZ + offset[1];

			if (!withinPlan(bounds, chunkX, chunkZ) || !level.hasChunk(chunkX, chunkZ)) {
				continue;
			}

			MapTile tile = tileFor(chunkX, chunkZ, true);

			if (tile == null) {
				continue;
			}

			long drawnAt = tile.chunkDrawnAt(chunkX, chunkZ);

			if (drawnAt == 0L) {
				// A chunk that arrived without its heightmap comes back empty. Give it a moment
				// rather than burning the whole budget on it every single pass.
				if (now - tile.chunkEmptyAt(chunkX, chunkZ) < EMPTY_RETRY_MS) {
					continue;
				}

				if (tile.rasteriseChunk(level, level.getChunk(chunkX, chunkZ), chunkX, chunkZ)) {
					chunksThisSession++;
				}

				drawn++;
			} else if (now - drawnAt > REFRESH_MS) {
				stale.add(new int[] {chunkX, chunkZ});
			}
		}

		// Nothing new nearby, so spend what is left keeping the drawn ones up to date.
		for (int[] chunk : stale) {
			if (drawn >= MAX_CHUNKS_PER_PASS || System.nanoTime() > deadline) {
				break;
			}

			MapTile tile = tileFor(chunk[0], chunk[1], false);

			if (tile != null && now - tile.chunkEmptyAt(chunk[0], chunk[1]) >= EMPTY_RETRY_MS) {
				tile.rasteriseChunk(level, level.getChunk(chunk[0], chunk[1]), chunk[0], chunk[1]);
				drawn++;
			}
		}

		trim();
	}

	private static boolean withinPlan(int[] bounds, int chunkX, int chunkZ) {
		int minX = (bounds[0] - MARGIN) >> 4;
		int minZ = (bounds[1] - MARGIN) >> 4;
		int maxX = (bounds[2] + MARGIN) >> 4;
		int maxZ = (bounds[3] + MARGIN) >> 4;
		return chunkX >= minX && chunkX <= maxX && chunkZ >= minZ && chunkZ <= maxZ;
	}

	/** Chunk offsets ordered by distance, so the ground under your feet is drawn first. */
	private static List<int[]> spiral(int radius) {
		return SPIRALS.computeIfAbsent(radius, key -> {
			List<int[]> offsets = new ArrayList<>();

			for (int x = -key; x <= key; x++) {
				for (int z = -key; z <= key; z++) {
					offsets.add(new int[] {x, z});
				}
			}

			offsets.sort(Comparator.comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1]));
			return offsets;
		});
	}

	// ------------------------------------------------------------------- tiles

	private static MapTile tileFor(int chunkX, int chunkZ, boolean create) {
		int tileX = Math.floorDiv(chunkX << 4, MapTile.SIZE);
		int tileZ = Math.floorDiv(chunkZ << 4, MapTile.SIZE);
		return tile(tileX, tileZ, create);
	}

	/**
	 * The tile covering these tile coordinates, loading it off disk when it exists.
	 *
	 * @param create whether to make an empty tile when there is nothing saved
	 */
	public static MapTile tile(int tileX, int tileZ, boolean create) {
		long key = MapTile.tileKey(tileX, tileZ);
		MapTile tile = TILES.get(key);

		if (tile != null) {
			tile.touch();
			return tile;
		}

		if (worldId.isEmpty()) {
			return null;
		}

		MapTile fresh = new MapTile(tileX, tileZ);
		boolean loaded = fresh.load(dir());

		if (!loaded && !create) {
			fresh.close();
			return null;
		}

		TILES.put(key, fresh);
		return fresh;
	}

	/**
	 * Draws every loaded chunk around the player right now, ignoring the time budget and whether
	 * a chunk was already drawn. This is the button for when you want the map filled in on the
	 * spot rather than as it comes.
	 *
	 * @return how many chunks were drawn
	 */
	public static int redrawLoaded(Minecraft minecraft) {
		PerimeterPlan plan = PlanManager.plan();
		Level level = minecraft.level;

		if (plan == null || level == null || minecraft.player == null || !PlanManager.inPlanDimension()) {
			return 0;
		}

		int[] bounds = plan.coverageBounds();

		if (bounds == null) {
			return 0;
		}

		int playerChunkX = minecraft.player.getBlockX() >> 4;
		int playerChunkZ = minecraft.player.getBlockZ() >> 4;
		int radius = Math.max(2, minecraft.options.getEffectiveRenderDistance());
		int drawn = 0;

		for (int[] offset : spiral(radius)) {
			int chunkX = playerChunkX + offset[0];
			int chunkZ = playerChunkZ + offset[1];

			if (!withinPlan(bounds, chunkX, chunkZ) || !level.hasChunk(chunkX, chunkZ)) {
				continue;
			}

			MapTile tile = tileFor(chunkX, chunkZ, true);

			if (tile != null) {
				tile.rasteriseChunk(level, level.getChunk(chunkX, chunkZ), chunkX, chunkZ);
				drawn++;
			}
		}

		chunksThisSession += drawn;
		trim();
		return drawn;
	}

	/** Queues a chunk the client has just loaded, to be drawn on the next tick. */
	public static void onChunkLoaded(int chunkX, int chunkZ) {
		if (FRESH.size() >= MAX_FRESH) {
			return;
		}

		FRESH.add(new long[] {chunkX, chunkZ});
	}

	/** Only returns a tile that is already in memory, never touching the disk. */
	public static MapTile tileIfLoaded(int tileX, int tileZ) {
		MapTile tile = TILES.get(MapTile.tileKey(tileX, tileZ));

		if (tile != null) {
			tile.touch();
		}

		return tile;
	}

	/** Drops the least recently used tiles once there are too many in memory. */
	private static void trim() {
		if (TILES.size() <= MAX_TILES) {
			return;
		}

		List<MapTile> byAge = new ArrayList<>(TILES.values());
		byAge.sort(Comparator.comparingLong(MapTile::lastUsed));

		for (int index = 0; index < byAge.size() - MAX_TILES; index++) {
			MapTile tile = byAge.get(index);
			tile.save(dir());
			TILES.remove(MapTile.tileKey(tile.tileX(), tile.tileZ()));
			tile.close();
		}
	}

	// ------------------------------------------------------------ persistence

	private static Path dir() {
		return PlanStore.worldDir(worldId).resolve("map").resolve(sanitize(dimension));
	}

	private static String sanitize(String raw) {
		String cleaned = raw.replaceAll("[^A-Za-z0-9._-]", "_");
		return cleaned.isEmpty() ? "world" : cleaned;
	}

	public static void saveAll() {
		if (worldId.isEmpty()) {
			return;
		}

		for (MapTile tile : TILES.values()) {
			tile.save(dir());
		}
	}

	public static void closeAll() {
		saveAll();

		for (MapTile tile : TILES.values()) {
			tile.close();
		}

		TILES.clear();
		FRESH.clear();
		chunksThisSession = 0;
	}
}
