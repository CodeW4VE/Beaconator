package xyz.w4ve.beaconator.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xyz.w4ve.beaconator.client.gui.BeaconatorScreen;
import xyz.w4ve.beaconator.client.map.MapStore;
import xyz.w4ve.beaconator.client.scan.ScanCache;
import xyz.w4ve.beaconator.config.BeaconatorConfig;
import xyz.w4ve.beaconator.model.GridExtents;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.model.PyramidCalculator;

/** Key bindings, the scroll wheel behaviour in edit mode, and the per tick housekeeping. */
public final class Keys {
	/**
	 * Shift, control and alt on top of a binding, which vanilla {@link KeyMapping} has no notion
	 * of. Stored per binding in the config as a bit mask, so `G`, `Shift + G` and
	 * `Ctrl + Shift + G` can be three different things.
	 */
	public static final int SHIFT = 1;
	public static final int CONTROL = 2;
	public static final int ALT = 4;

	private static final String CATEGORY = "key.categories.beaconator";
	private static final List<KeyMapping> ALL = new ArrayList<>();
	private static final Map<KeyMapping, Integer> DEFAULTS = new LinkedHashMap<>();
	private static final Map<String, Integer> DEFAULT_MODIFIERS = new LinkedHashMap<>();

	public static final KeyMapping TOGGLE_EDIT = register("toggle_edit", GLFW.GLFW_KEY_B);
	/** Shift + B, a real binding now rather than a special case inside the edit mode handler. */
	public static final KeyMapping OPEN_SCREEN = register("open_screen", GLFW.GLFW_KEY_B, SHIFT);
	public static final KeyMapping SET_CENTER = register("set_center", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping TOGGLE_RENDER = register("toggle_render", InputConstants.UNKNOWN.getValue());
	/**
	 * Bound out of the box. The beams are the loudest thing this mod draws, so getting them out of
	 * the way has to be one key, not a trip through the screen.
	 */
	public static final KeyMapping TOGGLE_BEAMS = register("toggle_beams", GLFW.GLFW_KEY_G);
	public static final KeyMapping TOGGLE_EASY_PLACE =
			register("toggle_easy_place", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping LAYER_UP = register("layer_up", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping LAYER_DOWN = register("layer_down", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping SCAN = register("scan", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping UNDO = register("undo", InputConstants.UNKNOWN.getValue());

	private Keys() {
	}

	private static KeyMapping register(String name, int key) {
		return register(name, key, 0);
	}

	private static KeyMapping register(String name, int key, int modifiers) {
		KeyMapping mapping = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.beaconator." + name, InputConstants.Type.KEYSYM, key, CATEGORY));
		ALL.add(mapping);
		DEFAULTS.put(mapping, key);
		DEFAULT_MODIFIERS.put(mapping.getName(), modifiers);
		return mapping;
	}

	// ------------------------------------------------------------------ modifiers

	/**
	 * True when the modifiers held right now are exactly the ones this binding wants.
	 *
	 * <p>Exact: a binding on plain `G` does not fire while shift is held, otherwise every
	 * combination would also trigger the plain binding sitting underneath it.
	 */
	public static boolean modifiersMatch(KeyMapping mapping) {
		return heldModifiers() == modifiersOf(mapping);
	}

	public static int modifiersOf(KeyMapping mapping) {
		return BeaconatorConfig.get().keyModifiers.getOrDefault(mapping.getName(),
				DEFAULT_MODIFIERS.getOrDefault(mapping.getName(), 0));
	}

	/** Always writes the value, zero included, so a default modifier can be taken back off. */
	public static void setModifiers(KeyMapping mapping, int modifiers) {
		BeaconatorConfig config = BeaconatorConfig.get();
		config.keyModifiers.put(mapping.getName(), modifiers);
		config.save();
	}

	public static int heldModifiers() {
		return (Screen.hasShiftDown() ? SHIFT : 0)
				| (Screen.hasControlDown() ? CONTROL : 0)
				| (Screen.hasAltDown() ? ALT : 0);
	}

	/** `Ctrl + Shift + G`, the way it should read on a button. */
	public static String describe(KeyMapping mapping) {
		String key = mapping.getTranslatedKeyMessage().getString();

		if (mapping.isUnbound()) {
			return key;
		}

		int modifiers = modifiersOf(mapping);
		StringBuilder text = new StringBuilder();

		if ((modifiers & CONTROL) != 0) {
			text.append("Ctrl + ");
		}

		if ((modifiers & SHIFT) != 0) {
			text.append("Shift + ");
		}

		if ((modifiers & ALT) != 0) {
			text.append("Alt + ");
		}

		return text.append(key).toString();
	}

	/** A click on this binding, once the modifiers agree. Replaces bare {@code consumeClick()}. */
	private static boolean triggered(KeyMapping mapping) {
		boolean matched = false;

		while (mapping.consumeClick()) {
			// Drain every queued click either way: a click held back by the wrong modifiers must
			// not fire later when they happen to line up.
			matched |= modifiersMatch(mapping);
		}

		return matched;
	}

	/** Every binding of this mod, for the Keys tab. */
	public static List<KeyMapping> all() {
		return ALL;
	}

	public static void resetDefaults() {
		Minecraft minecraft = Minecraft.getInstance();
		BeaconatorConfig.get().keyModifiers.clear();
		BeaconatorConfig.get().save();

		for (Map.Entry<KeyMapping, Integer> entry : DEFAULTS.entrySet()) {
			minecraft.options.setKey(entry.getKey(),
					InputConstants.Type.KEYSYM.getOrCreate(entry.getValue()));
		}

		minecraft.options.save();
		KeyMapping.resetMapping();
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(Keys::tick);
	}

	private static boolean migrated;

	/**
	 * Puts Shift + B back on the screen binding for anyone who ran an earlier build.
	 *
	 * <p>{@code options.txt} remembers every binding, including the ones left unbound, so changing
	 * a default never reaches a player who has already started the game once: opening the screen
	 * used to be a special case inside the edit mode key rather than a binding, and that saved
	 * "unknown" beats the new default. Runs on the first tick, when the options are loaded, and
	 * only touches a binding nobody has set to anything.
	 */
	private static void migrateBindings(Minecraft mc) {
		migrated = true;

		if (!OPEN_SCREEN.isUnbound()) {
			return;
		}

		mc.options.setKey(OPEN_SCREEN, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_B));
		mc.options.save();
		KeyMapping.resetMapping();
	}

	private static void tick(Minecraft mc) {
		if (!migrated) {
			migrateBindings(mc);
		}

		if (triggered(OPEN_SCREEN)) {
			BeaconatorScreen.open();
		}

		if (triggered(TOGGLE_EDIT)) {
			if (!PlanManager.hasPlan()) {
				PlanManager.actionBar(Component.literal(
						"No plan yet. " + describe(OPEN_SCREEN) + " opens the screen."));
			} else {
				PlanManager.setEditMode(!PlanManager.editMode());
				PlanManager.actionBar(Component.literal(
						PlanManager.editMode() ? "Beaconator edit mode on" : "Beaconator edit mode off"));
			}
		}

		if (triggered(SET_CENTER) && mc.player != null && PlanManager.hasPlan()) {
			PerimeterPlan plan = PlanManager.plan();
			plan.setCenter(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
			PlanManager.markDirty();
			PlanManager.actionBar(Component.literal("Centre moved to "
					+ mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ()));
		}

		if (triggered(TOGGLE_RENDER)) {
			BeaconatorConfig config = BeaconatorConfig.get();
			config.renderCoverage = !config.renderCoverage;
			config.renderWireframe = config.renderCoverage;
			// Beams included: "render off" that leaves a forest of beams standing is not off.
			config.showBeams = config.renderCoverage;
			config.save();
			PlanManager.actionBar(Component.literal(
					config.renderCoverage ? "Beaconator render on" : "Beaconator render off"));
		}

		if (triggered(TOGGLE_BEAMS)) {
			BeaconatorConfig config = BeaconatorConfig.get();
			config.showBeams = !config.showBeams;
			config.save();
			PlanManager.actionBar(Component.literal(
					config.showBeams ? "Beaconator beams on" : "Beaconator beams off"));
		}

		if (triggered(TOGGLE_EASY_PLACE)) {
			BeaconatorConfig config = BeaconatorConfig.get();
			config.easyPlace = !config.easyPlace;
			config.save();
			PlanManager.actionBar(Component.literal(
					config.easyPlace ? "Beaconator easy place on" : "Beaconator easy place off"));
		}

		if (triggered(LAYER_UP)) {
			shiftLayer(mc, 1);
		}

		if (triggered(LAYER_DOWN)) {
			shiftLayer(mc, -1);
		}

		if (triggered(UNDO)) {
			String what = PlanHistory.undo(PlanManager.plan());
			PlanManager.actionBar(Component.literal(what == null
					? Lang.t("map.nothing_to_undo")
					: Lang.t("map.undone", what)));
		}

		if (triggered(SCAN) && PlanManager.hasPlan()) {
			int[] result = ScanCache.scanAll(mc);
			PlanManager.actionBar(Component.literal(
					Lang.t("materials.scanned", result[0], result[1], result[2])));
		}

		PlanManager.tick(mc);
		ScanCache.tick(mc);
		EasyPlace.tick(mc);
		MapStore.tick(mc);
		LitematicaBridge.tick();
	}

	private static void shiftLayer(Minecraft mc, int amount) {
		if (!LayerFilter.active() && mc.player != null) {
			LayerFilter.single(mc.player.getBlockY());
		} else {
			LayerFilter.shift(amount);
		}

		PlanManager.actionBar(Component.literal(Lang.t("display.layers", LayerFilter.describe())));
	}

	/**
	 * Handles a scroll tick while edit mode is on.
	 *
	 * <p>Plain scroll grows and shrinks the grid, shift changes how many beacons sit on every
	 * node, control nudges the spacing. Returns true when the scroll was used, so the hotbar
	 * does not move as well.
	 */
	public static boolean handleScroll(double amount) {
		Minecraft mc = Minecraft.getInstance();

		if (!PlanManager.editMode() || mc.screen != null || mc.player == null) {
			return false;
		}

		if (!BeaconatorConfig.get().scrollChangesRing) {
			return false;
		}

		PerimeterPlan plan = PlanManager.plan();
		int step = amount > 0 ? 1 : -1;

		if (Screen.hasShiftDown()) {
			int beacons = Math.clamp(plan.beaconsPerNode() + step, 1, PyramidCalculator.MAX_BEACONS_PER_NODE);

			if (beacons != plan.beaconsPerNode()) {
				plan.setBeaconsPerNode(beacons);
				PlanManager.markDirty();
			}

			PlanManager.actionBar(Component.literal(plan.beaconsPerNode()
					+ " beacon" + (plan.beaconsPerNode() == 1 ? "" : "s") + " per node ("
					+ PyramidCalculator.totalBlocks(plan.beaconsPerNode(), plan.level())
					+ " blocks per pyramid)"));
			return true;
		}

		if (Screen.hasControlDown()) {
			int spacing = Math.max(1, plan.spacing() + step);
			plan.setSpacing(spacing);
			PlanManager.markDirty();
			int gap = Math.max(plan.gapAcrossRow(), plan.gapAlongRow());
			PlanManager.actionBar(Component.literal("Spacing " + spacing
					+ (gap > 0 ? " (" + gap + " uncovered)" : gap < 0 ? " (" + (-gap) + " overlap)" : " (exact)")));
			return true;
		}

		int ring = plan.ring();
		GridExtents extents = ring >= 0
				? GridExtents.ring(Math.max(0, ring + step))
				: plan.extents().expandAll(step);

		plan.setExtents(extents);
		plan.pruneOverrides();
		PlanManager.markDirty();
		PlanManager.actionBar(Component.literal(extents.columns() + " x " + extents.rows()
				+ " nodes (" + extents.nodeCount() + ")"));
		return true;
	}
}
