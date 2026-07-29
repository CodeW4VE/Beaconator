package xyz.w4ve.beaconator.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import xyz.w4ve.beaconator.BeaconatorMod;
import xyz.w4ve.beaconator.io.PlanStore;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.net.NodePayload;
import xyz.w4ve.beaconator.net.PlanListPayload;
import xyz.w4ve.beaconator.net.PlanPayload;
import xyz.w4ve.beaconator.net.PlanRequestPayload;

/**
 * The plans the server holds for everyone.
 *
 * <p>Anyone with the mod can put a plan up, open one, and mark nodes on it. The people digging a
 * perimeter are a team, and making them ask an admin to tick off every node they finish would be
 * worse than not sharing it at all.
 *
 * <p>Plans live in the world folder, so a copy of the world carries the perimeters with it.
 */
public final class ServerPlan {
	private static final String DIR = "beaconator";

	private static final Map<String, PerimeterPlan> PLANS = new LinkedHashMap<>();
	private static final Map<String, String> AUTHORS = new LinkedHashMap<>();

	private static MinecraftServer server;
	private static boolean loaded;

	private ServerPlan() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> {
			server = minecraft;
			loadAll();

			// Only clients that registered the channel, so a vanilla client is never sent anything.
			if (ServerPlayNetworking.canSend(handler.player, PlanListPayload.TYPE)) {
				sender.sendPacket(list());
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(PlanRequestPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			server = player.getServer();
			loadAll();
			PerimeterPlan plan = PLANS.get(payload.name());

			if (plan != null && ServerPlayNetworking.canSend(player, PlanPayload.TYPE)) {
				ServerPlayNetworking.send(player, new PlanPayload(payload.name(), PlanStore.toJson(plan)));
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(PlanPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			server = player.getServer();
			loadAll();
			String name = sanitize(payload.name());

			if (name.isEmpty()) {
				return;
			}

			if (payload.isDelete()) {
				PLANS.remove(name);
				AUTHORS.remove(name);
				delete(name);
			} else {
				PerimeterPlan plan = PlanStore.fromJson(payload.json(), name);

				if (plan == null) {
					return;
				}

				PLANS.put(name, plan);
				AUTHORS.put(name, player.getGameProfile().getName());
				save(name);
			}

			broadcastList();
		});

		ServerPlayNetworking.registerGlobalReceiver(NodePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			server = player.getServer();
			PerimeterPlan plan = PLANS.get(payload.plan());
			NodeStatus status = payload.state();

			if (plan == null || status == null) {
				return;
			}

			NodeKey key = new NodeKey(payload.i(), payload.j());

			if (!plan.extents().contains(key.i(), key.j()) || plan.statusAt(key) == status) {
				return;
			}

			plan.setStatus(key, status);
			save(payload.plan());

			// Back to everyone but the sender, who already has it drawn.
			for (ServerPlayer other : server.getPlayerList().getPlayers()) {
				if (other != player && ServerPlayNetworking.canSend(other, NodePayload.TYPE)) {
					ServerPlayNetworking.send(other, payload);
				}
			}
		});
	}

	private static PlanListPayload list() {
		List<PlanListPayload.Entry> entries = new ArrayList<>();

		for (Map.Entry<String, PerimeterPlan> entry : PLANS.entrySet()) {
			entries.add(new PlanListPayload.Entry(entry.getKey(),
					entry.getValue().extents().nodeCount(),
					AUTHORS.getOrDefault(entry.getKey(), "")));
		}

		return new PlanListPayload(entries);
	}

	private static void broadcastList() {
		if (server == null) {
			return;
		}

		PlanListPayload payload = list();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, PlanListPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	// ----------------------------------------------------------------- storage

	/** Names come from clients, so they are not allowed anywhere near a path separator. */
	private static String sanitize(String raw) {
		String cleaned = raw.trim().replaceAll("[^A-Za-z0-9 ._-]", "_");
		return cleaned.length() > 48 ? cleaned.substring(0, 48).trim() : cleaned;
	}

	private static Path dir() {
		return server.getWorldPath(LevelResource.ROOT).resolve(DIR);
	}

	private static Path path(String name) {
		return dir().resolve(sanitize(name) + ".json");
	}

	private static void loadAll() {
		if (loaded || server == null) {
			return;
		}

		loaded = true;

		if (!Files.isDirectory(dir())) {
			return;
		}

		try (Stream<Path> files = Files.list(dir())) {
			files.filter(file -> file.getFileName().toString().endsWith(".json")).forEach(file -> {
				String file_name = file.getFileName().toString();
				String name = file_name.substring(0, file_name.length() - ".json".length());

				try {
					PerimeterPlan plan = PlanStore.fromJson(
							Files.readString(file, StandardCharsets.UTF_8), name);

					if (plan != null) {
						PLANS.put(name, plan);
					}
				} catch (IOException e) {
					BeaconatorMod.LOGGER.warn("Could not read the shared plan {}", file, e);
				}
			});
		} catch (IOException e) {
			BeaconatorMod.LOGGER.warn("Could not list shared plans in {}", dir(), e);
		}
	}

	private static void save(String name) {
		PerimeterPlan plan = PLANS.get(name);

		if (server == null || plan == null) {
			return;
		}

		try {
			Files.createDirectories(dir());
			Files.writeString(path(name), PlanStore.toJson(plan), StandardCharsets.UTF_8);
		} catch (IOException e) {
			BeaconatorMod.LOGGER.warn("Could not write the shared plan {}", name, e);
		}
	}

	private static void delete(String name) {
		if (server == null) {
			return;
		}

		try {
			Files.deleteIfExists(path(name));
		} catch (IOException e) {
			BeaconatorMod.LOGGER.warn("Could not delete the shared plan {}", name, e);
		}
	}
}
