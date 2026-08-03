package xyz.w4ve.beaconator.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import xyz.w4ve.beaconator.BeaconatorClient;
import xyz.w4ve.beaconator.model.GridExtents;
import xyz.w4ve.beaconator.model.NodeData;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.model.RowAxis;
import xyz.w4ve.beaconator.model.water.WaterLayout;
import xyz.w4ve.beaconator.model.water.WaterPlan;
import xyz.w4ve.beaconator.model.water.WaterSegment;
import xyz.w4ve.beaconator.model.water.WaterSpec;

/**
 * Reads and writes plans under {@code config/beaconator/&lt;world&gt;/&lt;name&gt;.json}.
 *
 * <p>Plans are grouped per world (server address or singleplayer level name) because the same
 * plan name on two different servers means two different perimeters.
 */
public final class PlanStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private PlanStore() {
	}

	public static Path root() {
		return FabricLoader.getInstance().getConfigDir().resolve("beaconator");
	}

	public static Path worldDir(String worldId) {
		return root().resolve(sanitize(worldId));
	}

	public static Path planPath(String worldId, String name) {
		return worldDir(worldId).resolve(sanitize(name) + ".json");
	}

	public static List<String> list(String worldId) {
		Path dir = worldDir(worldId);
		List<String> names = new ArrayList<>();

		if (!Files.isDirectory(dir)) {
			return names;
		}

		try (Stream<Path> files = Files.list(dir)) {
			files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
				String file = p.getFileName().toString();
				names.add(file.substring(0, file.length() - ".json".length()));
			});
		} catch (IOException e) {
			BeaconatorClient.LOGGER.warn("Could not list plans in {}", dir, e);
		}

		names.sort(String::compareToIgnoreCase);
		return names;
	}

	public static void save(PerimeterPlan plan, String worldId) throws IOException {
		Path path = planPath(worldId, plan.name());
		Files.createDirectories(path.getParent());
		Files.writeString(path, GSON.toJson(toDto(plan)), StandardCharsets.UTF_8);
	}

	public static PerimeterPlan load(String worldId, String name) throws IOException {
		Path path = planPath(worldId, name);
		String json = Files.readString(path, StandardCharsets.UTF_8);
		PlanDto dto = GSON.fromJson(json, PlanDto.class);

		if (dto == null) {
			throw new IOException("Empty plan file: " + path);
		}

		return fromDto(dto, name);
	}

	public static boolean delete(String worldId, String name) throws IOException {
		return Files.deleteIfExists(planPath(worldId, name));
	}

	/**
	 * The plan as the same JSON that goes on disk. Used to put a plan on the wire: the format is
	 * already written, already versioned by being a plain DTO, and already survives a round trip,
	 * so a second encoding for the network would only be a second thing to keep in step.
	 */
	public static String toJson(PerimeterPlan plan) {
		return GSON.toJson(toDto(plan));
	}

	/** Reads back {@link #toJson}. Returns null when the text is not a plan. */
	public static PerimeterPlan fromJson(String json, String name) {
		try {
			PlanDto dto = GSON.fromJson(json, PlanDto.class);
			return dto == null ? null : fromDto(dto, name);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String sanitize(String raw) {
		String cleaned = raw.trim().replaceAll("[^A-Za-z0-9._-]", "_");
		return cleaned.isEmpty() ? "unnamed" : cleaned;
	}

	// ------------------------------------------------------------------ codec

	static PlanDto toDto(PerimeterPlan plan) {
		PlanDto dto = new PlanDto();
		dto.name = plan.name();
		dto.dimension = plan.dimension();
		dto.center = new int[] {plan.centerX(), plan.beaconY(), plan.centerZ()};
		dto.spacing = plan.spacing();
		dto.autoSpacing = plan.autoSpacing();
		dto.level = plan.level();
		dto.beaconsPerNode = plan.beaconsPerNode();
		dto.rowAxis = plan.rowAxis().name();
		dto.extents = new int[] {
			plan.extents().minI(), plan.extents().maxI(),
			plan.extents().minJ(), plan.extents().maxJ()
		};
		dto.pyramidBlock = plan.pyramidBlock();
		dto.markerBlock = plan.markerBlock();
		dto.innerCapBlock = plan.innerCapBlock();
		dto.placeMarker = plan.placeMarker();
		dto.water = toWaterDto(plan.water());
		dto.nodes = new LinkedHashMap<>();

		for (Map.Entry<NodeKey, NodeData> entry : plan.overrides().entrySet()) {
			NodeDto node = new NodeDto();
			node.status = entry.getValue().status().name();
			node.beacons = entry.getValue().beacons();
			node.y = entry.getValue().y();
			node.dx = entry.getValue().dx();
			node.dz = entry.getValue().dz();
			dto.nodes.put(entry.getKey().toString(), node);
		}

		return dto;
	}

	static PerimeterPlan fromDto(PlanDto dto, String fallbackName) {
		int[] center = dto.center == null ? new int[] {0, 64, 0} : dto.center;
		PerimeterPlan plan = new PerimeterPlan(
				dto.name == null ? fallbackName : dto.name,
				dto.dimension == null ? "minecraft:overworld" : dto.dimension,
				center[0], center[1], center[2]);

		plan.setLevel(dto.level == 0 ? 4 : dto.level);
		plan.setBeaconsPerNode(dto.beaconsPerNode == 0 ? 1 : dto.beaconsPerNode);
		plan.setRowAxis(dto.rowAxis == null ? RowAxis.Z : RowAxis.valueOf(dto.rowAxis));

		if (dto.spacing > 0) {
			plan.setSpacing(dto.spacing);
		}

		if (dto.autoSpacing) {
			plan.setAutoSpacing(true);
		}

		if (dto.extents != null && dto.extents.length == 4) {
			plan.setExtents(new GridExtents(dto.extents[0], dto.extents[1], dto.extents[2], dto.extents[3]));
		}

		if (dto.pyramidBlock != null) {
			plan.setPyramidBlock(dto.pyramidBlock);
		}

		if (dto.innerCapBlock != null) {
			plan.setInnerCapBlock(dto.innerCapBlock);
		}

		if (dto.markerBlock != null) {
			plan.setMarkerBlock(dto.markerBlock);
		}

		plan.setPlaceMarker(dto.placeMarker);
		readWater(dto.water, plan.water());

		if (dto.nodes != null) {
			for (Map.Entry<String, NodeDto> entry : dto.nodes.entrySet()) {
				NodeDto node = entry.getValue();

				if (node == null) {
					continue;
				}

				NodeStatus status = node.status == null ? NodeStatus.PENDING : NodeStatus.valueOf(node.status);
				plan.putOverride(NodeKey.parse(entry.getKey()),
						new NodeData(status, node.beacons, node.y, node.dx, node.dz));
			}
		}

		return plan;
	}

	// ------------------------------------------------------------------ water

	private static WaterDto toWaterDto(WaterPlan water) {
		// Nothing planned means nothing written, so a plan from before this existed and one where
		// nobody wants water lines look the same on disk.
		if (water == null || water.isEmpty()) {
			return null;
		}

		WaterSpec spec = water.spec();
		WaterDto dto = new WaterDto();
		dto.layout = spec.layout().name();
		dto.y = spec.y();
		dto.rowStep = spec.rowStep();
		dto.iceBlock = spec.iceBlock();
		dto.sourceEvery = spec.sourceEvery();
		dto.drain = spec.sink() == null ? null : new int[] {spec.sink().x(), spec.sink().z()};
		dto.edited = water.edited();
		dto.runs = new ArrayList<>();

		for (WaterSegment run : water.runs()) {
			dto.runs.add(new int[] {run.x1(), run.z1(), run.x2(), run.z2(), run.kind().ordinal()});
		}

		return dto;
	}

	private static void readWater(WaterDto dto, WaterPlan water) {
		if (dto == null) {
			return;
		}

		WaterSpec spec = WaterSpec.defaults();

		if (dto.layout != null) {
			spec = spec.with(WaterLayout.valueOf(dto.layout));
		}

		if (dto.iceBlock != null) {
			spec = spec.withIce(dto.iceBlock);
		}

		spec = new WaterSpec(spec.layout(), dto.y == 0 ? WaterSpec.BOTTOM_LAYER : dto.y,
				Math.max(1, dto.rowStep), spec.iceBlock(), Math.max(1, dto.sourceEvery), null);

		if (dto.drain != null && dto.drain.length == 2) {
			spec = spec.drainingAt(dto.drain[0], dto.drain[1]);
		}

		water.setSpec(spec);

		if (dto.runs != null) {
			WaterSegment.Kind[] kinds = WaterSegment.Kind.values();

			for (int[] run : dto.runs) {
				if (run == null || run.length < 4) {
					continue;
				}

				int kind = run.length > 4 ? Math.clamp(run[4], 0, kinds.length - 1) : 0;
				water.add(new WaterSegment(run[0], run[1], run[2], run[3], kinds[kind]));
			}
		}

		if (!dto.edited) {
			// add() marks the plan as touched by hand, which is only true of what came in that way.
			water.clearEditedFlag();
		}
	}

	/** On disk shape of a plan. Kept separate so the model stays free of serialization concerns. */
	static class PlanDto {
		String name;
		String dimension;
		int[] center;
		int spacing;
		boolean autoSpacing;
		int level;
		int beaconsPerNode;
		String rowAxis;
		int[] extents;
		String pyramidBlock;
		String markerBlock;
		String innerCapBlock;
		boolean placeMarker;
		WaterDto water;
		Map<String, NodeDto> nodes;
	}

	/** The water lines. Absent in every plan written before they existed, which reads as none. */
	static class WaterDto {
		String layout;
		int y;
		int rowStep;
		String iceBlock;
		int sourceEvery;
		int[] drain;
		boolean edited;
		/** One run as {@code x1, z1, x2, z2, kind}, which is small enough to read in the file. */
		List<int[]> runs;
	}

	static class NodeDto {
		String status;
		Integer beacons;
		Integer y;
		/** Offset off the grid. Absent in plans written before nodes could be moved, so 0. */
		int dx;
		int dz;
	}
}
