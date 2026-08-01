package xyz.w4ve.beaconator.client;

import java.io.IOException;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xyz.w4ve.beaconator.BeaconatorClient;
import xyz.w4ve.beaconator.config.BeaconatorConfig;
import xyz.w4ve.beaconator.io.PlanStore;
import xyz.w4ve.beaconator.client.net.ClientSync;
import xyz.w4ve.beaconator.client.scan.ScanCache;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;

/** The plan being edited right now, plus what the player is currently pointing at. */
public final class PlanManager {
	/** Nodes further away than this cannot be picked, no matter where you look. */
	private static final double MAX_PICK_DISTANCE = 2048.0;

	private static PerimeterPlan plan;
	private static boolean editMode;
	private static NodeKey hovered;
	private static boolean dirty;

	private PlanManager() {
	}

	public static PerimeterPlan plan() {
		return plan;
	}

	public static boolean hasPlan() {
		return plan != null;
	}

	public static void setPlan(PerimeterPlan newPlan) {
		plan = newPlan;
		hovered = null;
		dirty = true;
		ClientSync.detach();
		rememberOpenPlan();
	}

	/**
	 * The one way a node changes state. Applies it locally and, when the open plan is the server's
	 * shared one, tells the server so the rest of the team sees it.
	 *
	 * <p>Everything funnels through here on purpose: clicking on the map, the automatic scan
	 * noticing a beacon went in, undo, and {@code /bea fill}. A second path that skipped the
	 * network would show up as nodes that are green for you and not for anyone else.
	 */
	public static void changeStatus(NodeKey key, NodeStatus status) {
		if (plan == null || plan.statusAt(key) == status) {
			return;
		}

		plan.setStatus(key, status);
		dirty = true;
		ClientSync.sendNode(key, status);
	}

	/**
	 * The one way a node moves off the grid, for the same reason {@link #changeStatus} is the one
	 * way it changes state: a second path would leave the node in a different place for everyone
	 * else, which is worse than not moving it at all.
	 */
	public static void moveNode(NodeKey key, int dx, int dz) {
		if (plan == null) {
			return;
		}

		plan.setOffsetAt(key, dx, dz);
		int[] applied = plan.offsetAt(key);
		dirty = true;
		ScanCache.invalidate(key);
		ClientSync.sendMove(key, applied[0], applied[1]);
	}

	public static void closePlan() {
		plan = null;
		hovered = null;
		rememberOpenPlan();
	}

	public static boolean editMode() {
		return editMode && plan != null;
	}

	public static void setEditMode(boolean edit) {
		editMode = edit;
	}

	public static NodeKey hovered() {
		return editMode() ? hovered : null;
	}

	public static void markDirty() {
		dirty = true;
	}

	/** True when the plan is in the same dimension the player is standing in. */
	public static boolean inPlanDimension() {
		Minecraft mc = Minecraft.getInstance();

		if (plan == null || mc.level == null) {
			return false;
		}

		return plan.dimension().equals(mc.level.dimension().location().toString());
	}

	// --------------------------------------------------------------- lifecycle

	public static void onDisconnect() {
		autoSave();
		rememberOpenPlan();
		xyz.w4ve.beaconator.client.map.MapStore.closeAll();
		plan = null;
		hovered = null;
		editMode = false;
	}

	/**
	 * Reopens whatever you had open on this server. A perimeter is weeks of work, so being handed
	 * an empty screen on every join and having to go dig the plan out of a list is nonsense.
	 *
	 * <p>With no record of a previous plan it still opens the only one there is, which is the
	 * common case: one site, one plan.
	 */
	public static void onJoin() {
		BeaconatorConfig config = BeaconatorConfig.get();

		if (!config.reopenLastPlan || plan != null) {
			return;
		}

		String world = worldId();
		String name = config.lastPlan.get(world);

		if (name == null) {
			List<String> saved = PlanStore.list(world);

			if (saved.size() != 1) {
				return;
			}

			name = saved.get(0);
		}

		if (load(name)) {
			actionBar(Component.literal("Beaconator: " + plan.name()));
		}
	}

	/** Notes the open plan against this world, for {@link #onJoin()}. */
	private static void rememberOpenPlan() {
		BeaconatorConfig config = BeaconatorConfig.get();
		String world = worldId();

		if (plan == null) {
			config.lastPlan.remove(world);
		} else {
			config.lastPlan.put(world, plan.name());
		}

		config.save();
	}

	public static void tick(Minecraft mc) {
		if (plan == null || mc.level == null || mc.player == null) {
			hovered = null;
			return;
		}

		hovered = pick(mc.player);
	}

	// ------------------------------------------------------------------ picking

	/**
	 * The node the player is pointing at.
	 *
	 * <p>Three ways of working it out, in order of how well they match what you meant:
	 *
	 * <ol>
	 *   <li>The block you are actually looking at. This is the one that matters while digging:
	 *       down in a trench the beacon plane is above your head and useless, but the wall in
	 *       front of you is right there.
	 *   <li>Where the look ray crosses the plane the beacons sit on, for when you are flying
	 *       over the perimeter and aiming at open ground.
	 *   <li>A point straight ahead, when neither of those lands anywhere.
	 * </ol>
	 *
	 * <p>Intersecting the coverage boxes themselves would be the obvious approach and is the
	 * wrong one: they are a hundred blocks wide, they overlap, and you are almost always inside
	 * several at once.
	 */
	public static NodeKey pick(LocalPlayer player) {
		if (plan == null) {
			return null;
		}

		Vec3 hit = aimPoint(player);

		if (hit == null) {
			return null;
		}

		return plan.keyNear(hit.x, hit.z);
	}

	private static Vec3 aimPoint(LocalPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();

		if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
			return mc.hitResult.getLocation();
		}

		double planeY = plan.beaconY() + 0.5;

		if (Math.abs(look.y) > 1.0e-4) {
			double distance = (planeY - eye.y) / look.y;

			if (distance > 0 && distance <= MAX_PICK_DISTANCE) {
				return eye.add(look.scale(distance));
			}
		}

		// Looking level, or the plane is behind you: take a point straight ahead.
		return eye.add(look.scale(64.0));
	}

	// ------------------------------------------------------------------ storage

	public static String worldId() {
		Minecraft mc = Minecraft.getInstance();

		if (mc.getCurrentServer() != null) {
			return mc.getCurrentServer().ip;
		}

		if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
			return "singleplayer-" + mc.getSingleplayerServer().getWorldData().getLevelName();
		}

		return "unknown";
	}

	public static List<String> savedPlans() {
		return PlanStore.list(worldId());
	}

	public static boolean save() {
		if (plan == null) {
			return false;
		}

		try {
			PlanStore.save(plan, worldId());
			dirty = false;
			return true;
		} catch (IOException e) {
			BeaconatorClient.LOGGER.warn("Could not save plan {}", plan.name(), e);
			return false;
		}
	}

	/** Saves without complaining, used when leaving a world. */
	public static void autoSave() {
		if (plan != null && dirty) {
			save();
		}
	}

	public static boolean load(String name) {
		try {
			setPlan(PlanStore.load(worldId(), name));
			dirty = false;
			return true;
		} catch (IOException | RuntimeException e) {
			BeaconatorClient.LOGGER.warn("Could not load plan {}", name, e);
			return false;
		}
	}

	public static void feedback(Component message) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player != null) {
			mc.player.displayClientMessage(message, false);
		}
	}

	/** Short status line shown above the hotbar after a change. */
	public static void actionBar(Component message) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player != null) {
			mc.player.displayClientMessage(message, true);
		}
	}
}
