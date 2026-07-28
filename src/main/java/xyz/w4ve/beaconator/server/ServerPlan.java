package xyz.w4ve.beaconator.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import xyz.w4ve.beaconator.net.PlanPayload;

/**
 * The shared plan, held by the server.
 *
 * <p>One plan for the whole server, published by an operator and edited by anyone who has the mod:
 * the people digging a perimeter are a team, and making them ask an admin to tick off every node
 * they finish would be worse than not sharing it at all.
 *
 * <p>Lives in the world folder rather than in config, so a copy of the world carries the perimeter
 * with it.
 */
public final class ServerPlan {
	private static final String FILE = "beaconator-plan.json";

	private static PerimeterPlan plan;
	private static MinecraftServer server;

	private ServerPlan() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> {
			server = minecraft;
			load();

			// Only clients that registered the channel, so a vanilla client is never sent anything.
			if (ServerPlayNetworking.canSend(handler.player, PlanPayload.TYPE)) {
				sender.sendPacket(plan == null ? PlanPayload.none() : new PlanPayload(PlanStore.toJson(plan)));
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(PlanPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			server = player.getServer();

			// Replacing the whole plan is an operator's call. Anything else and one person
			// wandering in with an old copy wipes what everyone else has been building.
			if (!player.hasPermissions(2)) {
				return;
			}

			plan = payload.isEmpty() ? null : PlanStore.fromJson(payload.json(), "shared");
			save();
			broadcast(payload.isEmpty() ? PlanPayload.none() : payload, null);
		});

		ServerPlayNetworking.registerGlobalReceiver(NodePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			server = player.getServer();
			NodeStatus status = payload.state();

			if (plan == null || status == null) {
				return;
			}

			NodeKey key = new NodeKey(payload.i(), payload.j());

			if (!plan.extents().contains(key.i(), key.j()) || plan.statusAt(key) == status) {
				return;
			}

			plan.setStatus(key, status);
			save();
			// Back to everyone but the sender, who already has it drawn.
			broadcast(payload, player);
		});
	}

	private static void broadcast(Object payload, ServerPlayer except) {
		if (server == null) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player == except) {
				continue;
			}

			if (payload instanceof PlanPayload plans && ServerPlayNetworking.canSend(player, PlanPayload.TYPE)) {
				ServerPlayNetworking.send(player, plans);
			} else if (payload instanceof NodePayload node && ServerPlayNetworking.canSend(player, NodePayload.TYPE)) {
				ServerPlayNetworking.send(player, node);
			}
		}
	}

	// ----------------------------------------------------------------- storage

	private static Path path() {
		return server.getWorldPath(LevelResource.ROOT).resolve(FILE);
	}

	private static void load() {
		if (plan != null || server == null || !Files.exists(path())) {
			return;
		}

		try {
			plan = PlanStore.fromJson(Files.readString(path(), StandardCharsets.UTF_8), "shared");
		} catch (IOException e) {
			BeaconatorMod.LOGGER.warn("Could not read the shared plan at {}", path(), e);
		}
	}

	private static void save() {
		if (server == null) {
			return;
		}

		try {
			if (plan == null) {
				Files.deleteIfExists(path());
			} else {
				Files.writeString(path(), PlanStore.toJson(plan), StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			BeaconatorMod.LOGGER.warn("Could not write the shared plan to {}", path(), e);
		}
	}
}
