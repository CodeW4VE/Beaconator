package xyz.w4ve.beaconator.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import xyz.w4ve.beaconator.BeaconatorClient;
import xyz.w4ve.beaconator.client.Keys;
import xyz.w4ve.beaconator.client.Lang;
import xyz.w4ve.beaconator.client.LayerFilter;
import xyz.w4ve.beaconator.client.LitematicaBridge;
import xyz.w4ve.beaconator.client.PlanHistory;
import xyz.w4ve.beaconator.client.PlanManager;
import xyz.w4ve.beaconator.client.map.MapStore;
import xyz.w4ve.beaconator.client.map.MapView;
import xyz.w4ve.beaconator.client.net.ClientSync;
import xyz.w4ve.beaconator.client.scan.NodeScan;
import xyz.w4ve.beaconator.client.scan.ScanCache;
import xyz.w4ve.beaconator.client.scan.WorldGridDetector;
import xyz.w4ve.beaconator.client.scan.WorldScanner;
import xyz.w4ve.beaconator.config.BeaconatorConfig;
import xyz.w4ve.beaconator.config.CoverageStyle;
import xyz.w4ve.beaconator.io.LitematicIO;
import xyz.w4ve.beaconator.io.PlanStore;
import xyz.w4ve.beaconator.io.SchematicFiles;
import xyz.w4ve.beaconator.model.GridExtents;
import xyz.w4ve.beaconator.model.GridNode;
import xyz.w4ve.beaconator.model.GridSide;
import xyz.w4ve.beaconator.model.MaterialTally;
import xyz.w4ve.beaconator.model.NodeKey;
import xyz.w4ve.beaconator.model.NodeStatus;
import xyz.w4ve.beaconator.model.PerimeterPlan;
import xyz.w4ve.beaconator.model.PyramidCalculator;
import xyz.w4ve.beaconator.model.RowAxis;
import xyz.w4ve.beaconator.client.water.ChannelState;
import xyz.w4ve.beaconator.client.water.WaterCache;
import xyz.w4ve.beaconator.client.water.WaterScan;
import xyz.w4ve.beaconator.model.water.WaterBudget;
import xyz.w4ve.beaconator.model.water.WaterLayout;
import xyz.w4ve.beaconator.model.water.WaterNetwork;
import xyz.w4ve.beaconator.model.water.WaterPlan;
import xyz.w4ve.beaconator.model.water.WaterSegment;
import xyz.w4ve.beaconator.model.water.WaterSpec;
import xyz.w4ve.beaconator.net.PlanListPayload;

/**
 * The mod's screen. Everything worth seeing the current value of lives here, which is most
 * things: the commands stay around for scripting and for anyone who prefers typing.
 */
public class BeaconatorScreen extends Screen {
	/**
	 * The screen is two pages, not one row of nine tabs.
	 *
	 * <p>Nine did not fit: at any normal gui scale the row ran off both edges of the screen. And
	 * the water lines are not a ninth setting of the same thing anyway, they are the perimeter seen
	 * from underneath, with their own map, their own settings and their own bill. So they get a
	 * page of their own, reached by an arrow at the end of the row, and each page keeps its tabs
	 * down to a number that fits.
	 */
	private enum Section {
		BEACONS,
		WATER
	}

	private enum Tab {
		MAP("tab.map", Section.BEACONS),
		PLAN("tab.plan", Section.BEACONS),
		GRID("tab.grid", Section.BEACONS),
		BLOCKS("tab.blocks", Section.BEACONS),
		MATERIALS("tab.materials", Section.BEACONS),
		SHARED("tab.shared", Section.BEACONS),
		KEYS("tab.keys", Section.BEACONS),
		DISPLAY("tab.display", Section.BEACONS),

		WATER_MAP("tab.water_map", Section.WATER),
		WATER_SETUP("tab.water_setup", Section.WATER),
		WATER_COST("tab.water_cost", Section.WATER);

		private final String key;
		private final Section section;

		Tab(String key, Section section) {
			this.key = key;
			this.section = section;
		}
	}

	private static final int LABEL_COLOR = 0xFFFFFF;
	private static final int DIM_COLOR = 0xAAAAAA;
	private static final int WARN_COLOR = 0xFF6060;
	private static final int OK_COLOR = 0x57E36B;

	/** The blocks a beacon pyramid can be made of, in the order everyone lists them. */
	private static final String[] PYRAMID_BLOCKS = {
		"minecraft:iron_block",
		"minecraft:gold_block",
		"minecraft:emerald_block",
		"minecraft:diamond_block",
		"minecraft:netherite_block"
	};

	/** Handy marker blocks. Black first, which is what the perimeters here already use. */
	private static final String[] MARKER_BLOCKS = {
		"minecraft:black_stained_glass",
		"minecraft:red_stained_glass",
		"minecraft:white_stained_glass",
		"minecraft:tinted_glass"
	};

	private static Tab activeTab = Tab.MAP;
	private static final MapView MAP_VIEW = new MapView();
	/** Left click draws a channel instead of panning the map. Off by default: panning is what you
	 * do most, and a map you cannot drag without drawing on it is a trap. */
	private static boolean waterDraw;

	private final Screen parent;
	private final List<Label> labels = new ArrayList<>();
	private String status = "";
	private int statusColor = DIM_COLOR;
	private boolean dragging;
	private KeyMapping listeningFor;
	/** Both of these are "press it again and I mean it", for the two buttons that destroy work. */
	private boolean confirmGenerate;
	private boolean confirmClear;
	/** Water lines were touched while this screen was open, so the server wants to hear about it. */
	private boolean waterTouched;

	private EditBox nameBox;
	private EditBox pyramidBox;
	private EditBox markerBox;
	private EditBox centerX;
	private EditBox centerY;
	private EditBox centerZ;

	public BeaconatorScreen(Screen parent) {
		super(Component.literal("Beaconator"));
		this.parent = parent;
	}

	private record Label(int x, int y, Supplier<String> text, Supplier<Integer> color,
			boolean centered, boolean rightAligned) {
	}

	@Override
	protected void init() {
		labels.clear();

		// Note: listeningFor survives init on purpose. Clicking a binding sets it and rebuilds the
		// widgets to redraw the button, so clearing it here would cancel the listen in the same
		// frame and no key could ever be bound. It is cleared when the tab or the screen changes.
		// The Shared tab only exists where it can do something: a server without the mod never
		// registers the channel, and an empty tab that says "not available" is just clutter.
		List<Tab> tabs = new ArrayList<>();

		for (Tab tab : Tab.values()) {
			if (tab.section == activeTab.section && (tab != Tab.SHARED || ClientSync.connected())) {
				tabs.add(tab);
			}
		}

		if (!tabs.contains(activeTab)) {
			activeTab = Tab.MAP;
			tabs.removeIf(tab -> tab.section != Section.BEACONS);
		}

		// Both pages are on the bar, always, pinned to the two corners of the screen, and the one
		// you are on is greyed out like the tab you are on. Pinned, not laid out with the tabs: the
		// tab row is a different width on each page, so anything measured from it would shuffle
		// sideways every time you turn the page, and these two are the buttons you aim at without
		// looking. The tabs are centred between them.
		int sideWidth = Math.min(96, Math.max(58, width / 9));
		int room = width - (sideWidth + 8) * 2;
		int tabWidth = Math.min(76, Math.max(30, room / tabs.size()));
		int tabsX = Math.max(sideWidth + 8, width / 2 - tabs.size() * tabWidth / 2);

		addRenderableWidget(sectionButton(4, "section.to_beacons", "section.beacons_tip",
				sideWidth, Section.BEACONS, Tab.MAP));

		for (int index = 0; index < tabs.size(); index++) {
			Tab tab = tabs.get(index);
			Button button = Button.builder(
					Component.literal(fit(Lang.t(tab.key), tabWidth - 10)), b -> {
				activeTab = tab;
				listeningFor = null;
				// "Press it again" only means anything while you are looking at the button that
				// said it. Leaving the tab is as clear a no as pressing anything else.
				confirmGenerate = false;
				confirmClear = false;
				rebuildWidgets();
			}).bounds(tabsX + index * tabWidth, 6, tabWidth - 2, 20).build();
			button.active = tab != activeTab;
			addRenderableWidget(button);
		}

		addRenderableWidget(sectionButton(width - sideWidth - 4, "section.to_water",
				"section.water_tip", sideWidth, Section.WATER, Tab.WATER_MAP));

		addRenderableWidget(Button.builder(Lang.c("done"), button -> onClose())
				.bounds(width / 2 - 50, height - 28, 100, 20).build());

		// Next to Done and on every tab, because "get this off my screen" is not a display
		// preference to go hunting for, it is the thing you want at hand.
		BeaconatorConfig config = BeaconatorConfig.get();
		addRenderableWidget(Button.builder(
				Lang.c(config.enabled ? "master.button_on" : "master.button_off"), button -> {
					BeaconatorConfig current = BeaconatorConfig.get();
					current.enabled = !current.enabled;
					current.save();
					rebuildWidgets();
				})
				.bounds(8, height - 28, 110, 20)
				.tooltip(Tooltip.create(Lang.c("master.tooltip")))
				.build());

		boolean needsPlan = activeTab != Tab.PLAN && activeTab != Tab.DISPLAY
				&& activeTab != Tab.KEYS && activeTab != Tab.SHARED;

		// The water page keeps the beacons map's own view, so turning the page lands you looking at
		// the same piece of the world rather than back at the whole perimeter.
		MAP_VIEW.setWaterMode(activeTab == Tab.WATER_MAP);

		// Before anything else on this page, and before it complains about there being no plan: the
		// first thing somebody who turned the page needs is to be told whether this is for them.
		if (activeTab.section == Section.WATER && !BeaconatorConfig.get().waterIntroSeen) {
			initWaterIntro();
			return;
		}

		if (!needsPlan || PlanManager.hasPlan()) {
			switch (activeTab) {
				case MAP -> initMap();
				case WATER_MAP -> initWater();
				case WATER_SETUP -> initWaterSetup();
				case WATER_COST -> initWaterCost();
				case PLAN -> initPlan();
				case GRID -> initGrid();
				case BLOCKS -> initBlocks();
				case MATERIALS -> initMaterials();
				case SHARED -> initShared();
				case KEYS -> initKeys();
				case DISPLAY -> initDisplay();
			}
		} else {
			label(width / 2, 60, () -> Lang.t("no_plan"), () -> WARN_COLOR, true);
		}
	}

	/** One end of the tab bar: the page you are not on, or a greyed out reminder of the one you are. */
	private Button sectionButton(int x, String key, String tip, int width, Section section, Tab landing) {
		Button button = Button.builder(Component.literal(fit(Lang.t(key), width - 8)), b -> {
			activeTab = landing;
			listeningFor = null;
			confirmGenerate = false;
			confirmClear = false;
			rebuildWidgets();
		}).bounds(x, 6, width, 20).tooltip(Tooltip.create(Lang.c(tip))).build();

		button.active = activeTab.section != section;
		return button;
	}

	// --------------------------------------------------------------------- map

	private int mapX() {
		return 6;
	}

	private int mapY() {
		return 52;
	}

	private int mapWidth() {
		return width - 12;
	}

	private int mapHeight() {
		// Room for the two readout lines AND the Done button below them. At 46 the strip ran from
		// height-46 to height-18 and the button sits at height-28, straight through the text.
		return height - 52 - 64;
	}

	private void initMap() {
		int buttonY = 30;
		int left = 6;
		int gap = 4;

		// One row of buttons: share out whatever width the screen actually has instead of laying
		// them out at fixed positions that fall off the edge at small gui scales.
		record MapButton(Component label, Runnable action) {
		}

		List<MapButton> buttons = List.of(
				new MapButton(Lang.c("map.fit"),
						() -> MAP_VIEW.fit(PlanManager.plan(), mapWidth(), mapHeight())),
				new MapButton(Lang.c("map.centre"), MAP_VIEW::centreOnPlayer),
				new MapButton(Lang.c(MAP_VIEW.moveMode() ? "map.move_on" : "map.move_off"),
						MAP_VIEW::toggleMoveMode),
				new MapButton(Lang.c(MAP_VIEW.showCoverage() ? "map.coverage_on" : "map.coverage_off"),
						MAP_VIEW::toggleCoverage),
				new MapButton(Lang.c(MAP_VIEW.showGrid() ? "map.outlines_on" : "map.outlines_off"),
						MAP_VIEW::toggleGrid),
				new MapButton(Lang.c("map.undo"), this::undo),
				new MapButton(Lang.c("map.redraw"), () -> {
					int drawn = MapStore.redrawLoaded(Minecraft.getInstance());
					setStatus(Lang.t("map.redrawn", drawn), drawn > 0 ? OK_COLOR : DIM_COLOR);
				}));

		int statsWidth = font.width(Lang.t("map.stats", 99999, 99)) + 16;
		int available = width - left * 2 - statsWidth;
		int buttonWidth = Math.clamp((available - gap * (buttons.size() - 1)) / buttons.size(), 44, 110);

		for (int index = 0; index < buttons.size(); index++) {
			MapButton entry = buttons.get(index);
			// Cut to what the button actually has room for. Minecraft centres a button's label and
			// spills it out of both sides, and one more button on this row means less room each.
			Component label = Component.literal(fit(entry.label().getString(), buttonWidth - 8));
			addRenderableWidget(Button.builder(label, b -> {
				entry.action().run();
				rebuildWidgets();
			}).bounds(left + index * (buttonWidth + gap), buttonY, buttonWidth, 18).build());
		}

		// Right aligned by hand: drawString runs left to right from wherever it is put.
		label(width - 8, buttonY + 5,
				() -> Lang.t("map.stats", MapStore.chunksDrawn(), MapStore.tilesLoaded()),
				() -> DIM_COLOR, false, true);

		MAP_VIEW.ensureCentred(PlanManager.plan(), mapWidth(), mapHeight());
	}

	private void renderMap(GuiGraphics graphics, int mouseX, int mouseY) {
		PerimeterPlan plan = PlanManager.plan();
		// Tiles are fed from the client tick, so the map keeps filling in with this closed.
		MAP_VIEW.render(graphics, plan, mapX(), mapY(), mapWidth(), mapHeight(), mouseX, mouseY);

		// The game HUD is right behind this, so the readout gets its own backing.
		graphics.fill(0, mapY() + mapHeight(), width, mapY() + mapHeight() + 28, 0xC0101318);
		graphics.drawString(font, MAP_VIEW.describe(plan), mapX() + 4, mapY() + mapHeight() + 4,
				DIM_COLOR, false);
		// Two hints on one line, and on a narrow screen they used to overprint each other into
		// mush. The mouse one wins the space; the arrow keys one gives way.
		String mouseHint = MAP_VIEW.selecting() ? Lang.t("map.hint_area")
				: MAP_VIEW.moveMode() ? Lang.t("map.hint_moving")
				: Lang.t("map.hint");
		String moveHint = MAP_VIEW.moveMode() ? "" : Lang.t("map.hint_move");
		int hintY = mapY() + mapHeight() + 16;

		if (font.width(mouseHint) + font.width(moveHint) + 24 <= width) {
			graphics.drawString(font, moveHint, width - 8 - font.width(moveHint), hintY, DIM_COLOR, false);
		} else {
			moveHint = "";
		}

		int room = width - 16 - (moveHint.isEmpty() ? 0 : font.width(moveHint) + 12);
		graphics.drawString(font, fit(mouseHint, room), mapX() + 4, hintY, DIM_COLOR, false);

		NodeKey hovered = MAP_VIEW.hovered();

		if (hovered != null) {
			GridNode node = plan.nodeAt(hovered);
			int[] offset = plan.offsetAt(hovered);
			String text = Lang.t("map.node_at", hovered.toString(), node.x(), node.z())
					+ (plan.moved(hovered) ? String.format("  (%+d, %+d)", offset[0], offset[1]) : "")
					+ "  [" + Lang.state(plan.statusAt(hovered)) + "]";
			graphics.drawString(font, text, width - 8 - font.width(text), mapY() + mapHeight() + 4,
					LABEL_COLOR, false);

			// Why is this node not green? Without the numbers and the exact spot the plan expects
			// a beacon at, a node that reads as empty is impossible to tell from a plan that is
			// looking one block, or one Y level, off what was actually built.
			String detail = scanDetail(plan, hovered, node);
			graphics.drawString(font, detail, width - 8 - font.width(detail),
					mapY() + mapHeight() + 15, DIM_COLOR, false);
		}

		if (!PlanManager.inPlanDimension()) {
			graphics.drawCenteredString(font, Lang.t("wrong_dimension", plan.dimension()),
					width / 2, mapY() + 8, WARN_COLOR);
		}
	}

	/** One line of "here is what the scan saw", for working out why a node is not green. */
	private static String scanDetail(PerimeterPlan plan, NodeKey key, GridNode node) {
		NodeScan scan = ScanCache.get(key);
		List<int[]> beacons = plan.beaconPositionsOf(node);
		String expects = beacons.isEmpty() ? "-"
				: beacons.get(0)[0] + " " + beacons.get(0)[1] + " " + beacons.get(0)[2];

		if (scan == null || !scan.loaded()) {
			return "not read yet  ·  beacon expected at " + expects;
		}

		return scan.found() + "/" + scan.expected() + " blocks  ·  "
				+ scan.beaconsFound() + "/" + scan.beaconsExpected() + " beacons  ·  "
				+ "beacon expected at " + expects;
	}

	private boolean overMap(double mouseX, double mouseY) {
		return (activeTab == Tab.MAP
				|| (activeTab == Tab.WATER_MAP && BeaconatorConfig.get().waterIntroSeen))
				&& PlanManager.hasPlan()
				&& mouseX >= mapX() && mouseX <= mapX() + mapWidth()
				&& mouseY >= mapY() && mouseY <= mapY() + mapHeight();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}

		if (!overMap(mouseX, mouseY)) {
			return false;
		}

		if (activeTab == Tab.WATER_MAP) {
			return waterClicked(mouseX, mouseY, button);
		}

		PerimeterPlan plan = PlanManager.plan();

		// Move mode, or alt as the shortcut for it: grab the node instead of toggling it.
		if (button == 0 && (MAP_VIEW.moveMode() || hasAltDown())) {
			NodeKey grabbed = MAP_VIEW.nodeAt(plan, mouseX, mouseY, mapX(), mapY(), mapWidth(), mapHeight());

			if (grabbed != null) {
				PlanHistory.record(plan, grabbed, Lang.t("map.undo_move"));
				MAP_VIEW.beginMove(plan, mouseX, mouseY, mapX(), mapY(), mapWidth(), mapHeight());
				return true;
			}
		}

		// Shift or control starts a rectangle instead of touching a single node.
		if (button <= 1 && (hasShiftDown() || hasControlDown())) {
			MapView.SelectionMode mode = hasControlDown()
					? MapView.SelectionMode.RESET
					: button == 0 ? MapView.SelectionMode.REMOVE : MapView.SelectionMode.EXCLUDE;
			MAP_VIEW.beginSelection(mode, mouseX, mouseY, mapX(), mapY(), mapWidth(), mapHeight());
			return true;
		}

		NodeKey key = MAP_VIEW.nodeAt(plan, mouseX, mouseY, mapX(), mapY(), mapWidth(), mapHeight());

		if (key == null || button > 1) {
			dragging = true;
			return true;
		}

		PlanHistory.record(plan, key, Lang.t("map.undo_node"));
		NodeStatus next = button == 0 ? plan.toggleRemoved(key) : plan.toggleExcluded(key);
		setStatus("Node " + key + ": " + Lang.state(next),
				next == NodeStatus.REMOVED ? WARN_COLOR : next == NodeStatus.PENDING ? OK_COLOR : DIM_COLOR);
		PlanManager.markDirty();
		ScanCache.invalidate(key);
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (MAP_VIEW.drawingRun()) {
			int[] block = MAP_VIEW.blockAt(mouseX, mouseY, mapX(), mapY(), mapWidth(), mapHeight());
			MAP_VIEW.updateRun(block[0], block[1]);
			return true;
		}

		if (MAP_VIEW.movingNode()) {
			PerimeterPlan plan = PlanManager.plan();
			int[] offset = MAP_VIEW.updateMove(plan, mouseX, mouseY,
					mapX(), mapY(), mapWidth(), mapHeight(), hasControlDown());

			if (offset != null) {
				reportMove(MAP_VIEW.moveKey(), offset);
			}

			return true;
		}

		if (MAP_VIEW.selecting()) {
			MAP_VIEW.updateSelection(mouseX, mouseY, mapX(), mapY(), mapWidth(), mapHeight());
			return true;
		}

		if (overMap(mouseX, mouseY) && (dragging || button == 2)) {
			MAP_VIEW.drag(dragX, dragY);
			return true;
		}

		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;

		if (MAP_VIEW.drawingRun()) {
			WaterSegment run = MAP_VIEW.endRun();

			if (run != null) {
				PlanManager.plan().water().add(run);
				PlanManager.markDirty();
				waterTouched = true;
				setStatus(Lang.t("water.drew", run.length()), OK_COLOR);
			}

			return true;
		}

		if (MAP_VIEW.movingNode()) {
			MAP_VIEW.endMove();
			PlanManager.markDirty();
			return true;
		}

		if (MAP_VIEW.selecting()) {
			applySelection();
			return true;
		}

		return super.mouseReleased(mouseX, mouseY, button);
	}

	/** Says where a node ended up, in blocks off the grid, which is what gets built. */
	private void reportMove(NodeKey key, int[] offset) {
		if (key == null || offset == null) {
			return;
		}

		if (offset[0] == 0 && offset[1] == 0) {
			setStatus(Lang.t("map.node_home", key.toString()), OK_COLOR);
		} else {
			setStatus(Lang.t("map.node_moved", key.toString(), offset[0], offset[1]), OK_COLOR);
		}
	}

	/** Applies the drag rectangle to every node it caught, in one undoable step. */
	private void applySelection() {
		PerimeterPlan plan = PlanManager.plan();
		MapView.SelectionMode mode = MAP_VIEW.selectionMode();
		List<NodeKey> keys = MAP_VIEW.selectedNodes(plan);
		MAP_VIEW.cancelSelection();

		if (plan == null || mode == null || keys.isEmpty()) {
			return;
		}

		PlanHistory.record(plan, keys, Lang.t("map.undo_area"));

		for (NodeKey key : keys) {
			plan.setStatus(key, mode.status());
		}

		PlanManager.markDirty();
		ScanCache.invalidate(keys);
		setStatus(Lang.t("map.selected", keys.size(), Lang.state(mode.status())),
				mode == MapView.SelectionMode.REMOVE ? WARN_COLOR : OK_COLOR);
	}

	private void undo() {
		String what = PlanHistory.undo(PlanManager.plan());

		if (what == null) {
			setStatus(Lang.t("map.nothing_to_undo"), DIM_COLOR);
			return;
		}

		ScanCache.clear();
		setStatus(Lang.t("map.undone", what), OK_COLOR);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (overMap(mouseX, mouseY)) {
			MAP_VIEW.zoomBy(scrollY, mouseX, mouseY, mapX(), mapY(), mapWidth(), mapHeight());
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ------------------------------------------------------------------- water

	/**
	 * The one time explanation, shown the first time this tab is opened and never again.
	 *
	 * <p>Every other tab of this mod is about beacons, and anybody who installed it wants beacons.
	 * This one is about a thing only technical players build, so it says so plainly instead of
	 * dropping somebody into a screen full of digsorts and shulkers and letting them work out that
	 * none of it is aimed at them.
	 */
	private void initWaterIntro() {
		// The button sits below however many lines the text wraps to, and Spanish is a third longer
		// than English, so the height is measured rather than guessed.
		addRenderableWidget(Button.builder(Lang.c("water.intro_ok"), button -> {
			BeaconatorConfig.get().waterIntroSeen = true;
			BeaconatorConfig.get().save();
			rebuildWidgets();
		}).bounds(width / 2 - 60, introBottom() + 20, 120, 20).build());
	}

	/** How wide the intro paragraphs are allowed to be, and where they end. */
	private int introWidth() {
		return Math.min(width - 40, 360);
	}

	private int introBottom() {
		int lines = font.split(Component.literal(Lang.t("water.intro_what")), introWidth()).size()
				+ font.split(Component.literal(Lang.t("water.intro_who")), introWidth()).size();
		return 66 + lines * 11 + 10;
	}

	/**
	 * The one time explanation, wrapped to whatever width the screen has.
	 *
	 * <p>Every other tab of this mod is about beacons, and anybody who installed it wants beacons.
	 * This one is about a thing only technical players build, so it says so plainly instead of
	 * dropping somebody into a screen full of digsorts and shulkers and letting them work out that
	 * none of it is aimed at them.
	 */
	private void renderWaterIntro(GuiGraphics graphics) {
		int room = introWidth();
		int left = width / 2 - room / 2;
		graphics.drawCenteredString(font, Lang.t("water.intro_title"), width / 2, 44, LABEL_COLOR);

		int y = 66;

		for (var line : font.split(Component.literal(Lang.t("water.intro_what")), room)) {
			graphics.drawString(font, line, left, y, LABEL_COLOR, false);
			y += 11;
		}

		y += 10;

		for (var line : font.split(Component.literal(Lang.t("water.intro_who")), room)) {
			graphics.drawString(font, line, left, y, DIM_COLOR, false);
			y += 11;
		}
	}

	/**
	 * The currents map: drawing, and the four buttons that go with drawing.
	 *
	 * <p>Everything you set once and forget lives on Setup, and everything you read lives on Cost.
	 * What is left here is what you press with the map in front of you.
	 */
	private void initWater() {
		PerimeterPlan plan = PlanManager.plan();
		WaterPlan water = plan.water();
		int buttonY = 30;
		int left = 6;
		int gap = 4;

		record WaterButton(Component label, Runnable action) {
		}

		List<WaterButton> buttons = List.of(
				new WaterButton(Lang.c(water.isEmpty() ? "water.generate" : "water.regenerate"),
						this::generateWater),
				new WaterButton(Lang.c(waterDraw ? "water.draw_on" : "water.draw_off"), () -> {
					waterDraw = !waterDraw;
					MAP_VIEW.cancelRun();
				}),
				new WaterButton(Lang.c("map.undo"), this::undoRun),
				new WaterButton(Lang.c("water.clear"), this::clearWater));

		int buttonWidth = Math.clamp((width - left * 2 - gap * (buttons.size() - 1)) / buttons.size(),
				44, 130);

		for (int index = 0; index < buttons.size(); index++) {
			WaterButton entry = buttons.get(index);
			Component label = Component.literal(fit(entry.label().getString(), buttonWidth - 8));
			addRenderableWidget(Button.builder(label, b -> {
				entry.action().run();
				rebuildWidgets();
			}).bounds(left + index * (buttonWidth + gap), buttonY, buttonWidth, 18).build());
		}

		MAP_VIEW.ensureCentred(plan, mapWidth(), mapHeight());
	}

	/** Everything about the network you decide once: where it drains, what shape, what it is made of. */
	private void initWaterSetup() {
		PerimeterPlan plan = PlanManager.plan();
		WaterPlan water = plan.water();
		BeaconatorConfig config = BeaconatorConfig.get();
		int left = width / 2 - 154;
		int right = width / 2 + 4;
		int y = 40;
		int step = Math.clamp((height - 90 - y) / 7, 22, 30);

		addRenderableWidget(Button.builder(Lang.c("water.drain_here"), button -> {
			pickDrain();
			rebuildWidgets();
		}).bounds(left, y, 150, 20).tooltip(Tooltip.create(Lang.c("water.drain_tip"))).build());

		// The layer the ice floor sits on. It defaults to the deepest one there is, because that is
		// where the pyramid bases already are and the channel is free real estate down there, but a
		// perimeter built at another depth needs to say so.
		stepper(left, y + step, Lang.t("water.channel_y"), () -> water.spec().y(), value -> {
			WaterSpec spec = water.spec();
			water.setSpec(new WaterSpec(spec.layout(), value, spec.rowStep(), spec.iceBlock(),
					spec.sourceEvery(), spec.sink()));
			PlanManager.markDirty();
			waterTouched = true;
		}, -64, 320);

		addRenderableWidget(Button.builder(Lang.c(water.spec().layout() == WaterLayout.TREE
				? "water.layout_tree" : "water.layout_fishbone"), button -> {
					water.setSpec(water.spec().with(water.spec().layout() == WaterLayout.TREE
							? WaterLayout.FISHBONE : WaterLayout.TREE));
					PlanManager.markDirty();
					waterTouched = true;
					rebuildWidgets();
				}).bounds(left, y + step * 2, 150, 20)
				.tooltip(Tooltip.create(Lang.c("water.layout_tip"))).build());

		addRenderableWidget(Button.builder(Lang.c("water.ice", shortName(water.spec().iceBlock())),
				button -> {
					water.setSpec(water.spec().withIce(nextIce(water.spec().iceBlock())));
					PlanManager.markDirty();
					waterTouched = true;
					rebuildWidgets();
				}).bounds(left, y + step * 3, 150, 20)
				.tooltip(Tooltip.create(Lang.c("water.ice_tip"))).build());

		// How far a source carries. Only worth touching once you have dug a stretch and found out
		// what the water actually does, which is why it is a number and not a guess in the code.
		stepper(left, y + step * 4, Lang.t("water.source_every"),
				() -> water.spec().sourceEvery(), value -> {
			WaterSpec spec = water.spec();
			water.setSpec(new WaterSpec(spec.layout(), spec.y(), spec.rowStep(), spec.iceBlock(),
					Math.max(1, value), spec.sink()));
			PlanManager.markDirty();
			waterTouched = true;
		}, 1, 64);

		stepper(left, y + step * 5, Lang.t("water.row_step"),
				() -> water.spec().rowStep(), value -> {
			water.setSpec(water.spec().withRowStep(Math.max(1, value)));
			PlanManager.markDirty();
			waterTouched = true;
		}, 1, 8);

		onOff(right, y, "water.show", config.renderWater, value -> config.renderWater = value);
		colourButton(right, y + step, 150, "water.colour", () -> config.colorWater,
				value -> config.colorWater = value);
		colourButton(right, y + step * 2, 150, "water.colour_bad", () -> config.colorWaterBad,
				value -> config.colorWaterBad = value);
		colourButton(right, y + step * 3, 150, "water.colour_drain", () -> config.colorWaterDrain,
				value -> config.colorWaterDrain = value);

		onOff(right, y + step * 4, "water.fittings", config.showFittings,
				value -> config.showFittings = value);

		addRenderableWidget(Button.builder(Lang.c("water.intro_again"), button -> {
			BeaconatorConfig.get().waterIntroSeen = false;
			BeaconatorConfig.get().save();
			rebuildWidgets();
		}).bounds(right, y + step * 5, 150, 20).build());
	}

	/** What is set right now, in words, under the buttons that set it. */
	private void renderWaterSetup(GuiGraphics graphics) {
		PerimeterPlan plan = PlanManager.plan();
		WaterPlan water = plan.water();
		int left = width / 2 - 154;
		int y = height - 92;

		String drain = water.spec().sink() == null
				? Lang.t("water.drain_none")
				: Lang.t("water.drain_set", water.spec().sink().x(), water.spec().sink().z());
		graphics.drawString(font, fit(drain, width - 16), left, y,
				water.spec().sink() == null ? WARN_COLOR : LABEL_COLOR, false);
		graphics.drawString(font, fit(Lang.t("water.layer",
				water.spec().y(), water.spec().waterY()), width - 16), left, y + 12, DIM_COLOR, false);

		// Say what that height means rather than only what it is. The default is the one layer that
		// costs nothing to dig; anywhere else and the channel is crossing the pyramids, not passing
		// between them, which the red runs will tell you about but late.
		int baseY = plan.beaconY() - plan.level();
		String note = water.spec().y() == WaterSpec.BOTTOM_LAYER && baseY == WaterSpec.BOTTOM_LAYER
				? Lang.t("water.layer_bottom")
				: Lang.t("water.layer_high", baseY);
		graphics.drawString(font, fit(note, width - 16), left, y + 24, DIM_COLOR, false);
		graphics.drawString(font, fit(Lang.t("water.setup_hint"), width - 16), left, y + 36,
				DIM_COLOR, false);
	}

	/** The bill, in full, on a page with room for it. */
	private void initWaterCost() {
		PerimeterPlan plan = PlanManager.plan();

		addRenderableWidget(Button.builder(Lang.c(plan.water().isEmpty()
				? "water.generate" : "water.regenerate"), button -> {
					generateWater();
					rebuildWidgets();
				}).bounds(width / 2 - 75, 32, 150, 20).build());
	}

	private void renderWaterCost(GuiGraphics graphics) {
		PerimeterPlan plan = PlanManager.plan();
		WaterPlan water = plan.water();
		int left = width / 2 - 154;
		int y = 62;

		if (water.isEmpty()) {
			graphics.drawCenteredString(font, Lang.t("water.empty"), width / 2, y, DIM_COLOR);
			return;
		}

		WaterNetwork network = WaterCache.network(plan);
		WaterBudget budget = network.budget();

		for (Map.Entry<String, Integer> entry : budget.tally().counts().entrySet()) {
			Block block = WorldScanner.block(entry.getKey());

			if (block != null) {
				graphics.renderItem(new ItemStack(block.asItem()), left, y - 4);
			}

			graphics.drawString(font, shortName(entry.getKey()), left + 20, y, LABEL_COLOR, false);
			graphics.drawString(font, String.format("%,d", entry.getValue()), left + 190, y,
					LABEL_COLOR, false);
			y += 18;
		}

		y += 6;
		graphics.drawString(font, Lang.t("water.cost_mine",
				String.format("%,d", budget.iceToMine()), shortName(water.spec().iceBlock()),
				WaterBudget.shulkers(budget.iceToMine())), left, y, LABEL_COLOR, false);
		y += 14;
		graphics.drawString(font, Lang.t("water.cost_channel",
				String.format("%,d", budget.channelBlocks()),
				String.format("%,d", budget.blocksToDig())), left, y, DIM_COLOR, false);
		y += 14;
		graphics.drawString(font, Lang.t("water.cost_trip",
				String.format("%,d", budget.trip().longest()),
				String.format("%,d", budget.trip().average()),
				budget.trip().maxTurns()), left, y, DIM_COLOR, false);
		y += 12;
		graphics.drawString(font, budget.trip().overhead() == 0
				? Lang.t("water.ideal_perfect")
				: Lang.t("water.ideal", budget.trip().overhead()), left, y,
				budget.trip().overhead() == 0 ? OK_COLOR : DIM_COLOR, false);
		y += 18;
		graphics.drawString(font, Lang.t("water.cost_nodes", budget.nodesServed(),
				budget.nodesOrphaned(), plan.countByStatus(NodeStatus.EXCLUDED)),
				left, y, DIM_COLOR, false);
		y += 14;
		graphics.drawString(font, Lang.t("water.cost_flow", budget.waterSources(),
				budget.flowStops(), budget.junctions()), left, y, DIM_COLOR, false);

		int[] scanned = WaterScan.tally(plan);
		int read = scanned[ChannelState.SOLID.ordinal()] + scanned[ChannelState.OPEN.ordinal()]
				+ scanned[ChannelState.FLOORED.ordinal()] + scanned[ChannelState.FLOWING.ordinal()];

		if (read > 0) {
			y += 18;
			graphics.drawString(font, Lang.t("water.progress",
					String.format("%,d", scanned[ChannelState.FLOWING.ordinal()]),
					String.format("%,d", scanned[ChannelState.FLOORED.ordinal()]),
					String.format("%,d", scanned[ChannelState.OPEN.ordinal()]),
					String.format("%,d", scanned[ChannelState.SOLID.ordinal()])),
					left, y, LABEL_COLOR, false);
			y += 12;
			graphics.drawString(font, Lang.t("water.progress_note",
					String.format("%,d", scanned[ChannelState.UNKNOWN.ordinal()])),
					left, y, DIM_COLOR, false);
		}

		y += 14;
		graphics.drawString(font, Lang.t("water.fittings_note"), left, y, DIM_COLOR, false);

		int blocked = budget.blockedRuns();
		int lost = network.disconnected().size();

		if (blocked > 0 || lost > 0) {
			y += 18;
			graphics.drawString(font, blocked > 0 ? Lang.t("water.blocked", blocked)
					: Lang.t("water.disconnected", lost), left, y, WARN_COLOR, false);
		}
	}

	/** Packed ice, blue ice, plain ice: the three anybody actually floors a channel with. */
	private static String nextIce(String current) {
		return switch (current) {
			case WaterSpec.PACKED_ICE -> WaterSpec.BLUE_ICE;
			case WaterSpec.BLUE_ICE -> "minecraft:ice";
			default -> WaterSpec.PACKED_ICE;
		};
	}

	/**
	 * Lays a fresh network over the plan, throwing away anything drawn by hand.
	 *
	 * <p>Asks first when there is hand drawn work to lose. Not a dialog: the button says what it is
	 * about to destroy and you press it again, which is the same answer with one less screen.
	 */
	private void generateWater() {
		PerimeterPlan plan = PlanManager.plan();
		WaterPlan water = plan.water();

		if (water.edited() && !confirmGenerate) {
			confirmGenerate = true;
			setStatus(Lang.t("water.confirm_generate", water.runs().size()), WARN_COLOR);
			return;
		}

		confirmGenerate = false;
		water.generate(plan);
		PlanManager.markDirty();
		WaterCache.invalidate();
		waterTouched = true;
		setStatus(Lang.t("water.generated", water.runs().size(),
				String.format("%,d", water.network(plan).channelBlocks())), OK_COLOR);
	}

	private void clearWater() {
		PerimeterPlan plan = PlanManager.plan();
		WaterPlan water = plan.water();

		if (water.isEmpty()) {
			return;
		}

		if (!confirmClear) {
			confirmClear = true;
			setStatus(Lang.t("water.confirm_clear", water.runs().size()), WARN_COLOR);
			return;
		}

		confirmClear = false;
		water.clear();
		PlanManager.markDirty();
		WaterCache.invalidate();
		waterTouched = true;
		setStatus(Lang.t("water.cleared"), OK_COLOR);
	}

	/**
	 * Takes the drain from whatever you were aiming at.
	 *
	 * <p>Aimed at rather than typed because the middle of a grid is not a place: it is usually
	 * inside the centre node's own pyramid, and the sorter is wherever it actually got built. The
	 * game keeps aiming while this screen is open, so the block is the one you were looking at when
	 * you opened it.
	 */
	private void pickDrain() {
		Minecraft mc = Minecraft.getInstance();
		PerimeterPlan plan = PlanManager.plan();

		if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
			setStatus(Lang.t("water.drain_look"), WARN_COLOR);
			return;
		}

		BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
		WaterPlan water = plan.water();
		water.setSpec(water.spec().drainingAt(pos.getX(), pos.getZ()));
		PlanManager.markDirty();
		WaterCache.invalidate();
		waterTouched = true;
		setStatus(Lang.t("water.drain_set", pos.getX(), pos.getZ()), OK_COLOR);
	}

	private void undoRun() {
		WaterSegment run = PlanManager.plan().water().removeLast();

		if (run == null) {
			setStatus(Lang.t("map.nothing_to_undo"), DIM_COLOR);
			return;
		}

		PlanManager.markDirty();
		waterTouched = true;
		setStatus(Lang.t("water.erased", run.length()), OK_COLOR);
	}

	/** Click, drag and erase on the currents map. */
	private boolean waterClicked(double mouseX, double mouseY, int button) {
		PerimeterPlan plan = PlanManager.plan();
		int[] block = MAP_VIEW.blockAt(mouseX, mouseY, mapX(), mapY(), mapWidth(), mapHeight());

		// Right click erases whatever run is under it, whether Draw is on or not: getting rid of a
		// wrong line is not a mode you should have to be in.
		if (button == 1) {
			int before = channelLength(plan);
			boolean erased = plan.water().removeAt(block[0], block[1]);

			if (erased) {
				PlanManager.markDirty();
				waterTouched = true;
				setStatus(Lang.t("water.erased", before - channelLength(plan)), OK_COLOR);
			} else {
				setStatus(Lang.t("water.nothing_there"), DIM_COLOR);
			}

			return true;
		}

		if (button == 0 && waterDraw) {
			MAP_VIEW.beginRun(block[0], block[1]);
			return true;
		}

		dragging = true;
		return true;
	}

	/** Blocks of channel there are right now, counting a crossing twice. Only for saying what changed. */
	private static int channelLength(PerimeterPlan plan) {
		int total = 0;

		for (WaterSegment run : plan.water().runs()) {
			total += run.length();
		}

		return total;
	}

	private void renderWater(GuiGraphics graphics, int mouseX, int mouseY) {
		PerimeterPlan plan = PlanManager.plan();
		MAP_VIEW.render(graphics, plan, mapX(), mapY(), mapWidth(), mapHeight(), mouseX, mouseY);

		graphics.fill(0, mapY() + mapHeight(), width, mapY() + mapHeight() + 28, 0xC0101318);
		WaterPlan water = plan.water();
		int textY = mapY() + mapHeight() + 4;

		if (water.isEmpty()) {
			graphics.drawString(font, Lang.t("water.empty"), mapX() + 4, textY, DIM_COLOR, false);
		} else {
			WaterNetwork network = WaterCache.network(plan);
			WaterBudget budget = network.budget();
			String line = Lang.t("water.stats",
					String.format("%,d", budget.channelBlocks()),
					String.format("%,d", budget.iceToMine()),
					shortName(water.spec().iceBlock()),
					WaterBudget.shulkers(budget.iceToMine()),
					String.format("%,d", budget.trip().longest()));
			graphics.drawString(font, fit(line, width - 16), mapX() + 4, textY, LABEL_COLOR, false);

			// Second line: what is wrong if anything is, otherwise how the digging is going.
			int blocked = budget.blockedRuns();
			int lost = network.disconnected().size();
			int[] scanned = WaterScan.tally(plan);
			int read = scanned[ChannelState.SOLID.ordinal()] + scanned[ChannelState.OPEN.ordinal()]
					+ scanned[ChannelState.FLOORED.ordinal()] + scanned[ChannelState.FLOWING.ordinal()];
			String second = blocked > 0 ? Lang.t("water.blocked", blocked)
					: lost > 0 ? Lang.t("water.disconnected", lost)
					: budget.nodesOrphaned() > 0 ? Lang.t("water.orphans", budget.nodesOrphaned())
					: read == 0 ? Lang.t("water.all_served", budget.nodesServed())
					: Lang.t("water.progress",
							String.format("%,d", scanned[ChannelState.FLOWING.ordinal()]),
							String.format("%,d", scanned[ChannelState.FLOORED.ordinal()]),
							String.format("%,d", scanned[ChannelState.OPEN.ordinal()]),
							String.format("%,d", scanned[ChannelState.SOLID.ordinal()]));
			graphics.drawString(font, fit(second, width - 16), mapX() + 4, textY + 11,
					blocked > 0 || lost > 0 || budget.nodesOrphaned() > 0 ? WARN_COLOR : OK_COLOR,
					false);
		}

		// The hint goes on the right of the first line, where the beacons map puts its own.
		String hint = waterDraw ? Lang.t("water.hint_draw") : Lang.t("water.hint_look");
		graphics.drawString(font, hint, width - 8 - font.width(hint), mapY() + mapHeight() + 17,
				DIM_COLOR, false);

		String where = water.spec().sink() == null
				? Lang.t("water.drain_none")
				: Lang.t("water.drain_set", water.spec().sink().x(), water.spec().sink().z());
		graphics.drawString(font, where, width - 8 - font.width(where), mapY() + 6, DIM_COLOR, false);

		if (!PlanManager.inPlanDimension()) {
			graphics.drawCenteredString(font, Lang.t("wrong_dimension", plan.dimension()),
					width / 2, mapY() + 8, WARN_COLOR);
		}
	}

	// -------------------------------------------------------------------- plan

	private void initPlan() {
		int left = width / 2 - 154;
		int right = width / 2 + 4;
		int y = 36;

		label(left, y, () -> Lang.t("plan.name"), () -> DIM_COLOR, false);
		nameBox = new EditBox(font, left, y + 10, 150, 20, Component.literal("name"));
		nameBox.setMaxLength(48);
		nameBox.setValue(PlanManager.hasPlan() ? PlanManager.plan().name() : "perimeter");
		// Set after the value, so filling the box in does not count as typing. Renaming as you
		// type is the point: the box used to do nothing at all until you found the Save button,
		// which is why nobody could tell what Save was for.
		nameBox.setResponder(value -> {
			if (PlanManager.hasPlan() && !value.isBlank()) {
				PlanManager.plan().setName(value.trim());
				touch();
			}
		});
		addRenderableWidget(nameBox);

		addRenderableWidget(Button.builder(Lang.c("plan.new"), button -> newPlan())
				.bounds(right, y + 10, 150, 20).build());

		int rowY = y + 40;
		label(left, rowY, () -> Lang.t("plan.centre"), () -> DIM_COLOR, false);
		centerX = coordBox(left, rowY + 10, PlanManager.hasPlan() ? PlanManager.plan().centerX() : 0);
		centerY = coordBox(left + 52, rowY + 10, PlanManager.hasPlan() ? PlanManager.plan().beaconY() : 64);
		centerZ = coordBox(left + 104, rowY + 10, PlanManager.hasPlan() ? PlanManager.plan().centerZ() : 0);

		addRenderableWidget(Button.builder(Lang.c("plan.apply_centre"), button -> applyCentre())
				.bounds(right, rowY + 10, 74, 20).build());
		addRenderableWidget(Button.builder(Lang.c("plan.here"), button -> centreHere())
				.bounds(right + 76, rowY + 10, 74, 20).build());

		int buttonsY = rowY + 44;
		addRenderableWidget(Button.builder(Lang.c("plan.detect"), button -> detect())
				.bounds(left, buttonsY, 150, 20).build());
		addRenderableWidget(Button.builder(Lang.c("plan.save"), button -> save())
				.bounds(right, buttonsY, 150, 20).build());

		// Says out loud that nothing is lost by closing. Saving by hand next to a button called
		// "Export schematic" reads like the two do the same kind of thing, and they do not.
		label(width / 2, buttonsY + 22, () -> Lang.t("plan.autosaves"), () -> DIM_COLOR, true);

		addRenderableWidget(Button.builder(Lang.c("plan.open"), button -> openPlan())
				.bounds(left, buttonsY + 36, 150, 20).build());
		addRenderableWidget(Button.builder(Lang.c("plan.delete"), button -> deletePlan())
				.bounds(right, buttonsY + 36, 150, 20).build());

		addRenderableWidget(Button.builder(Lang.c("plan.import"), button -> importSchematic())
				.bounds(left, buttonsY + 60, 150, 20).build());
		addRenderableWidget(Button.builder(Lang.c("plan.export"), button -> exportSchematic())
				.bounds(right, buttonsY + 60, 150, 20).build());

		// Sharing lives in its own tab. One button here that half did it was how Save and Export
		// ended up looking like the same kind of thing.
		if (ClientSync.connected()) {
			addRenderableWidget(Button.builder(Lang.c("share.push"), button -> {
				if (ClientSync.share()) {
					setStatus(Lang.t("share.pushed", PlanManager.plan().name()), OK_COLOR);
				}
			}).bounds(left, buttonsY + 84, 150, 20).build());

			addRenderableWidget(Button.builder(Lang.c("share.go"), button -> {
				activeTab = Tab.SHARED;
				rebuildWidgets();
			}).bounds(right, buttonsY + 84, 150, 20).build());
		}

		label(width / 2, buttonsY + 112, () -> PlanManager.hasPlan()
				? PlanManager.plan().dimension() + "  ·  " + PlanManager.plan().extents().nodeCount()
						+ " " + Lang.t("nodes")
				: Lang.t("no_plan"), () -> DIM_COLOR, true);
	}

	private EditBox coordBox(int x, int y, int value) {
		EditBox box = new EditBox(font, x, y, 48, 20, Component.literal("coord"));
		box.setValue(Integer.toString(value));
		addRenderableWidget(box);
		return box;
	}

	// -------------------------------------------------------------------- grid

	private void initGrid() {
		PerimeterPlan plan = PlanManager.plan();
		int left = width / 2 - 154;
		int right = width / 2 + 4;
		int y = 40;

		stepper(left, y, Lang.t("grid.ring"), () -> Math.max(0, plan.ring()), value -> {
			plan.setRing(value);
			plan.pruneOverrides();
			touch();
		}, 0, 64);

		stepper(right, y, Lang.t("grid.spacing"), plan::spacing, value -> {
			plan.setSpacing(Math.max(1, value));
			touch();
		}, 1, 4096);

		int sidesY = y + 28;
		label(width / 2, sidesY - 2, () -> Lang.t("grid.sides"), () -> DIM_COLOR, true);
		sideStepper(left, sidesY + 10, GridSide.WEST, plan);
		sideStepper(right, sidesY + 10, GridSide.EAST, plan);
		sideStepper(left, sidesY + 34, GridSide.NORTH, plan);
		sideStepper(right, sidesY + 34, GridSide.SOUTH, plan);

		int cyclesY = sidesY + 62;

		addRenderableWidget(Cycler.<Integer>builder(value -> Component.literal(
						Lang.t(value == 1 ? "grid.beacons_one" : "grid.beacons_many", value)),
						plan.beaconsPerNode())
				.withValues(1, 2, 3, 4, 5, 6)
				.displayOnlyValue()
				.create(left, cyclesY, 150, 20, Component.empty(), (button, value) -> {
					plan.setBeaconsPerNode(value);
					touch();
				}));

		addRenderableWidget(Cycler.<Integer>builder(value -> Component.literal(
						Lang.t("grid.level", value, 10 * value + 10)), plan.level())
				.withValues(1, 2, 3, 4)
				.displayOnlyValue()
				.create(right, cyclesY, 150, 20, Component.empty(), (button, value) -> {
					plan.setLevel(value);
					touch();
				}));

		// All four quarter turns, so the beacons of a node can hang off whichever side of it
		// suits the perimeter, not only east and south.
		addRenderableWidget(Cycler.<RowAxis>builder(
						value -> Component.literal(fit(Lang.t("grid.axis",
								value.degrees() + "\u00b0 " + directionName(value)), 142)),
						plan.rowAxis())
				.withValues(RowAxis.values())
				.displayOnlyValue()
				.create(left, cyclesY + 24, 150, 20, Component.empty(), (button, value) -> {
					plan.setRowAxis(value);
					ScanCache.clear();
					touch();
				}));

		addRenderableWidget(CycleButton.onOffBuilder(plan.autoSpacing())
				.create(right, cyclesY + 24, 150, 20, Lang.c("grid.auto_spacing"), (button, value) -> {
					plan.setAutoSpacing(value);
					touch();
				}));

		label(width / 2, cyclesY + 54, () -> Lang.t("grid.summary",
				plan.extents().columns(), plan.extents().rows(), plan.extents().nodeCount(),
				String.format("%,d", plan.tally().total())), () -> LABEL_COLOR, true);

		label(width / 2, cyclesY + 66, this::coverageNote, this::coverageColor, true);

		label(width / 2, cyclesY + 78, () -> Lang.t("grid.per_pyramid",
				PyramidCalculator.totalBlocks(plan.beaconsPerNode(), plan.level()),
				2 * plan.level() + 1, 2 * plan.level() + plan.beaconsPerNode()), () -> DIM_COLOR, true);
	}

	private void sideStepper(int x, int y, GridSide side, PerimeterPlan plan) {
		String label = switch (side) {
			case WEST -> Lang.t("grid.west");
			case EAST -> Lang.t("grid.east");
			case NORTH -> Lang.t("grid.north");
			case SOUTH -> Lang.t("grid.south");
		};

		stepper(x, y, label,
				() -> switch (side) {
					case WEST -> -plan.extents().minI();
					case EAST -> plan.extents().maxI();
					case NORTH -> -plan.extents().minJ();
					case SOUTH -> plan.extents().maxJ();
				},
				value -> {
					GridExtents extents = plan.extents();
					plan.setExtents(switch (side) {
						case WEST -> new GridExtents(-value, extents.maxI(), extents.minJ(), extents.maxJ());
						case EAST -> new GridExtents(extents.minI(), value, extents.minJ(), extents.maxJ());
						case NORTH -> new GridExtents(extents.minI(), extents.maxI(), -value, extents.maxJ());
						case SOUTH -> new GridExtents(extents.minI(), extents.maxI(), extents.minJ(), value);
					});
					plan.pruneOverrides();
					touch();
				}, 0, 64);
	}

	// ------------------------------------------------------------------ blocks

	private void initBlocks() {
		PerimeterPlan plan = PlanManager.plan();
		int left = width / 2 - 154;
		int right = width / 2 + 4;
		int y = 44;

		label(left, y - 12, () -> Lang.t("blocks.pyramid"), () -> DIM_COLOR, false);
		pyramidBox = new EditBox(font, left, y, 150, 20, Component.literal("pyramid"));
		pyramidBox.setMaxLength(64);
		pyramidBox.setValue(plan.pyramidBlock());
		pyramidBox.setResponder(value -> applyBlock(value, true));
		addRenderableWidget(pyramidBox);

		addRenderableWidget(Button.builder(Lang.c("blocks.from_hand"), button -> {
			String id = heldBlockId();

			if (id != null) {
				pyramidBox.setValue(id);
			}
		}).bounds(right, y, 150, 20).build());

		// One button per beacon material, which beats typing ids for five options.
		blockRow(left, y + 24, PYRAMID_BLOCKS, true, plan);

		int markerY = y + 74;
		label(left, markerY - 12, () -> Lang.t("blocks.marker"), () -> DIM_COLOR, false);
		markerBox = new EditBox(font, left, markerY, 150, 20, Component.literal("marker"));
		markerBox.setMaxLength(64);
		markerBox.setValue(plan.markerBlock());
		markerBox.setResponder(value -> applyBlock(value, false));
		addRenderableWidget(markerBox);

		addRenderableWidget(Button.builder(Lang.c("blocks.from_hand"), button -> {
			String id = heldBlockId();

			if (id != null) {
				markerBox.setValue(id);
			}
		}).bounds(right, markerY, 150, 20).build());

		blockRow(left, markerY + 24, MARKER_BLOCKS, false, plan);

		addRenderableWidget(CycleButton.onOffBuilder(plan.placeMarker())
				.create(left, markerY + 52, 150, 20, Lang.c("blocks.place_marker"), (button, value) -> {
					plan.setPlaceMarker(value);
					touch();
				}));

		// The same idea for the nodes that stay in: a colour on top so a finished one reads from
		// the air. Off by default, because it is a block per beacon across the whole perimeter.
		addRenderableWidget(CycleButton.onOffBuilder(!plan.innerCapBlock().isEmpty())
				.create(right, markerY + 52, 150, 20, Lang.c("blocks.inner_cap"), (button, value) -> {
					plan.setInnerCapBlock(value
							? (markerBox != null && !markerBox.getValue().isBlank()
									? markerBox.getValue().trim() : "minecraft:white_stained_glass")
							: "");
					touch();
					rebuildWidgets();
				}));

		if (!plan.innerCapBlock().isEmpty()) {
			label(width / 2, markerY + 78, () -> Lang.t("blocks.inner_cap_is",
					shortName(plan.innerCapBlock())), () -> DIM_COLOR, true);
			blockRow(left, markerY + 88, MARKER_BLOCKS, false, plan, true);
			label(width / 2, markerY + 116, () -> Lang.t("blocks.explain1"), () -> DIM_COLOR, true);
			label(width / 2, markerY + 128, () -> Lang.t("blocks.explain2"), () -> DIM_COLOR, true);
		} else {
			label(width / 2, markerY + 82, () -> Lang.t("blocks.explain1"), () -> DIM_COLOR, true);
			label(width / 2, markerY + 94, () -> Lang.t("blocks.explain2"), () -> DIM_COLOR, true);
		}
	}

	/** A row of one click block choices. Beats typing ids when there are five of them. */
	private void blockRow(int x, int y, String[] blocks, boolean pyramid, PerimeterPlan plan) {
		blockRow(x, y, blocks, pyramid, plan, false);
	}

	private void blockRow(int x, int y, String[] blocks, boolean pyramid, PerimeterPlan plan,
			boolean innerCap) {
		int buttonWidth = 60;

		for (int index = 0; index < blocks.length; index++) {
			String id = blocks[index];
			Block block = WorldScanner.block(id);

			if (block == null) {
				continue;
			}

			boolean selected = id.equals(innerCap ? plan.innerCapBlock()
					: pyramid ? plan.pyramidBlock() : plan.markerBlock());
			Component label = Component.literal((selected ? "> " : "") + shortLabel(id));

			addRenderableWidget(Button.builder(label, button -> {
				if (innerCap) {
					plan.setInnerCapBlock(id);
				} else if (pyramid) {
					plan.setPyramidBlock(id);
					pyramidBox.setValue(id);
				} else {
					plan.setMarkerBlock(id);
					markerBox.setValue(id);
				}

				touch();
				setStatus(Lang.t("blocks.using", shortName(id)), OK_COLOR);
				rebuildWidgets();
			}).bounds(x + index * (buttonWidth + 2), y, buttonWidth, 20).build());
		}
	}

	private static String shortLabel(String id) {
		String name = shortName(id);
		int underscore = name.indexOf('_');
		String head = underscore < 0 ? name : name.substring(0, underscore);
		return Character.toUpperCase(head.charAt(0)) + head.substring(1);
	}

	private String heldBlockId() {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null) {
			return null;
		}

		ItemStack stack = mc.player.getMainHandItem();
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		Block block = WorldScanner.block(id.toString());

		if (block == null || stack.isEmpty()) {
			setStatus(Lang.t("blocks.hold_first"), WARN_COLOR);
			return null;
		}

		return id.toString();
	}

	private void applyBlock(String raw, boolean pyramid) {
		String id = raw.contains(":") ? raw : "minecraft:" + raw;

		if (WorldScanner.block(id) == null) {
			setStatus(Lang.t("blocks.unknown", raw), WARN_COLOR);
			return;
		}

		PerimeterPlan plan = PlanManager.plan();

		if (pyramid) {
			plan.setPyramidBlock(id);
		} else {
			plan.setMarkerBlock(id);
		}

		setStatus(Lang.t("blocks.using", shortName(id)), OK_COLOR);
		PlanManager.markDirty();
	}

	// --------------------------------------------------------------- materials

	private void initMaterials() {
		int left = width / 2 - 154;
		int right = width / 2 + 4;

		addRenderableWidget(Button.builder(Lang.c("materials.scan"), button -> {
			int[] result = ScanCache.scanAll(Minecraft.getInstance());
			setStatus(Lang.t("materials.scanned", result[0], result[1], result[2]), OK_COLOR);
			rebuildWidgets();
		}).bounds(left, 36, 150, 20).build());

		addRenderableWidget(CycleButton.onOffBuilder(BeaconatorConfig.get().autoScan)
				.create(right, 36, 150, 20, Lang.c("materials.keep_scanning"), (button, value) -> {
					BeaconatorConfig.get().autoScan = value;
					BeaconatorConfig.get().save();
				}));
	}

	private void renderMaterials(GuiGraphics graphics) {
		PerimeterPlan plan = PlanManager.plan();

		if (plan == null) {
			return;
		}

		MaterialTally[] totals = ScanCache.missingTotals();
		int left = width / 2 - 154;
		int y = 70;

		graphics.drawString(font, Lang.t("materials.block"), left + 20, y, DIM_COLOR, false);
		graphics.drawString(font, Lang.t("materials.needed"), left + 170, y, DIM_COLOR, false);
		graphics.drawString(font, Lang.t("materials.missing"), left + 240, y, DIM_COLOR, false);
		y += 14;

		for (Map.Entry<String, Integer> entry : plan.tally().counts().entrySet()) {
			Block block = WorldScanner.block(entry.getKey());
			int missing = totals[0].get(entry.getKey()) + totals[1].get(entry.getKey());

			if (block != null) {
				graphics.renderItem(new ItemStack(block.asItem()), left, y - 4);
			}

			graphics.drawString(font, shortName(entry.getKey()), left + 20, y, LABEL_COLOR, false);
			graphics.drawString(font, String.format("%,d", entry.getValue()), left + 170, y, LABEL_COLOR, false);
			graphics.drawString(font, String.format("%,d", missing), left + 240, y,
					missing == 0 ? OK_COLOR : LABEL_COLOR, false);
			y += 18;
		}

		y += 8;
		graphics.drawString(font, Lang.t("materials.total", String.format("%,d", plan.tally().total()),
				String.format("%,d", totals[0].total() + totals[1].total())), left, y, LABEL_COLOR, false);

		if (!totals[1].isEmpty()) {
			y += 12;
			graphics.drawString(font, Lang.t("materials.unknown", String.format("%,d", totals[1].total())),
					left, y, DIM_COLOR, false);
		}

		y += 20;
		graphics.drawString(font, Lang.t("materials.states",
				plan.countByStatus(NodeStatus.PLACED), plan.countByStatus(NodeStatus.PENDING),
				plan.countByStatus(NodeStatus.EXCLUDED), plan.countByStatus(NodeStatus.REMOVED)),
				left, y, DIM_COLOR, false);

		// One line about the water, and the rest of it on its own page. The ice alone is nine times
		// the length of the network, so a full breakdown here would bury the pyramids it sits under.
		if (!plan.water().isEmpty()) {
			WaterBudget budget = WaterCache.network(plan).budget();
			y += 20;
			graphics.drawString(font, fit(Lang.t("water.see_cost",
					String.format("%,d", budget.channelBlocks()),
					WaterBudget.shulkers(budget.iceToMine())), width - 16),
					left, y, DIM_COLOR, false);
		}
	}

	// -------------------------------------------------------------------- keys

	private void initKeys() {
		int left = width / 2 - 154;
		int right = width / 2 + 4;
		int y = 40;
		int index = 0;

		for (KeyMapping mapping : Keys.all()) {
			int column = index % 2 == 0 ? left : right;
			int row = index / 2;
			KeyMapping target = mapping;

			addRenderableWidget(Button.builder(keyLabel(mapping, 150), button -> {
				listeningFor = target;
				rebuildWidgets();
			}).bounds(column, y + row * 24, 150, 20).build());

			index++;
		}

		int bottom = y + ((Keys.all().size() + 1) / 2) * 24 + 8;

		addRenderableWidget(Button.builder(Lang.c("keys.reset"), button -> {
			Keys.resetDefaults();
			rebuildWidgets();
		}).bounds(left, bottom, 150, 20).build());

		label(width / 2, bottom + 30, () -> Lang.t("keys.hint"), () -> DIM_COLOR, true);
		label(width / 2, bottom + 42, () -> Lang.t("keys.hint2"), () -> DIM_COLOR, true);
	}

	/**
	 * The binding and the key it is on, trimmed to fit. Minecraft centres button text and lets it
	 * spill out both sides, which turns a long name into an unreadable stub.
	 */
	private Component keyLabel(KeyMapping mapping, int buttonWidth) {
		if (mapping == listeningFor) {
			return Component.literal("> " + Lang.t("keys.press") + " <");
		}

		String name = Component.translatable(mapping.getName()).getString();
		String tail = ": " + Keys.describe(mapping);
		return Component.literal(fit(name, buttonWidth - 8 - font.width(tail)) + tail);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Ctrl+Z anywhere in the screen, as long as a text field does not want the key. On the
		// water tab it takes back the last run instead: undoing a node you marked ten minutes ago
		// is not what anybody means by undo while they are drawing channels.
		if (keyCode == InputConstants.KEY_Z && hasControlDown() && listeningFor == null
				&& getFocused() instanceof EditBox == false) {
			if (activeTab == Tab.WATER_MAP && PlanManager.hasPlan()) {
				undoRun();
			} else {
				undo();
			}

			return true;
		}

		// Arrow keys nudge the whole grid while you watch the map. Three blocks off is the
		// difference between a beacon that fits and one that does not, and going back to the
		// Plan tab to type coordinates for that is no way to live.
		if (activeTab == Tab.MAP && listeningFor == null && PlanManager.hasPlan()
				&& !(getFocused() instanceof EditBox)) {
			int step = hasControlDown() ? 16 : hasShiftDown() ? 5 : 1;
			int dx = keyCode == InputConstants.KEY_LEFT ? -step
					: keyCode == InputConstants.KEY_RIGHT ? step : 0;
			int dz = keyCode == InputConstants.KEY_UP ? -step
					: keyCode == InputConstants.KEY_DOWN ? step : 0;

			// In move mode the arrows belong to the node that was picked, not to the whole grid:
			// nudging one node is the entire point of the mode, and moving all two hundred of
			// them because the mode was left on would be a nasty surprise.
			if ((dx != 0 || dz != 0) && MAP_VIEW.moveMode()) {
				PerimeterPlan plan = PlanManager.plan();
				NodeKey key = MAP_VIEW.moveKey();

				if (key == null) {
					setStatus(Lang.t("map.no_move_target"), DIM_COLOR);
					return true;
				}

				PlanHistory.record(plan, key, Lang.t("map.undo_move"));
				reportMove(key, MAP_VIEW.nudge(plan, dx, dz));
				return true;
			}

			if (dx != 0 || dz != 0) {
				PerimeterPlan plan = PlanManager.plan();
				plan.setCenter(plan.centerX() + dx, plan.beaconY(), plan.centerZ() + dz);
				PlanManager.markDirty();
				ScanCache.clear();
				setStatus(Lang.t("map.moved", plan.centerX(), plan.beaconY(), plan.centerZ()), OK_COLOR);
				return true;
			}

			if (keyCode == InputConstants.KEY_PAGEUP || keyCode == InputConstants.KEY_PAGEDOWN) {
				PerimeterPlan plan = PlanManager.plan();
				int dy = keyCode == InputConstants.KEY_PAGEUP ? step : -step;
				plan.setCenter(plan.centerX(), plan.beaconY() + dy, plan.centerZ());
				PlanManager.markDirty();
				ScanCache.clear();
				setStatus(Lang.t("map.moved", plan.centerX(), plan.beaconY(), plan.centerZ()), OK_COLOR);
				return true;
			}
		}

		if (listeningFor != null) {
			// Shift, control and alt are the modifiers of the combination, never the key itself.
			// Without this, reaching for Ctrl + Shift + G binds the control key and stops there.
			if (isModifierKey(keyCode)) {
				return true;
			}

			InputConstants.Key key = keyCode == InputConstants.KEY_ESCAPE
					? InputConstants.UNKNOWN
					: InputConstants.getKey(keyCode, scanCode);
			bind(listeningFor, key, keyCode == InputConstants.KEY_ESCAPE ? 0 : Keys.heldModifiers());
			listeningFor = null;
			rebuildWidgets();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** GLFW 340..347: the left and right shift, control, alt and super keys. */
	private static boolean isModifierKey(int keyCode) {
		return keyCode >= 340 && keyCode <= 347;
	}

	private void bind(KeyMapping mapping, InputConstants.Key key, int modifiers) {
		Minecraft minecraft = Minecraft.getInstance();
		mapping.setKey(key);
		minecraft.options.save();
		Keys.setModifiers(mapping, modifiers);
		KeyMapping.resetMapping();

		if (key.equals(InputConstants.UNKNOWN)) {
			setStatus(Lang.t("keys.cleared"), DIM_COLOR);
			return;
		}

		// Worth saying out loud: a modded profile has a hundred bindings and a silent clash just
		// means two things fire at once and you never find out why. Our own bindings only clash
		// when the modifiers match too, which is the whole point of having them.
		for (KeyMapping other : minecraft.options.keyMappings) {
			boolean sameCombination = other != mapping && other.same(mapping)
					&& (!Keys.all().contains(other) || Keys.modifiersOf(other) == modifiers);

			if (sameCombination) {
				setStatus(Lang.t("keys.conflict", Keys.describe(mapping),
						Component.translatable(other.getName()).getString()), WARN_COLOR);
				return;
			}
		}

		setStatus(Lang.t("keys.bound", Keys.describe(mapping)), OK_COLOR);
	}

	// ------------------------------------------------------------------ shared

	/**
	 * The plans the server is holding: open one, put yours up, take one down.
	 *
	 * <p>The whole point is that this needs no explaining. You see the list, you press the one you
	 * want, and you are on the same perimeter as everyone else.
	 */
	private void initShared() {
		int left = width / 2 - 154;
		int right = width / 2 + 4;
		int y = 34;

		label(width / 2, y, () -> Lang.t("share.title"), () -> LABEL_COLOR, true);

		List<PlanListPayload.Entry> entries = ClientSync.available();
		int rowY = y + 16;

		if (entries.isEmpty()) {
			label(width / 2, rowY + 14, () -> Lang.t("share.empty"), () -> DIM_COLOR, true);
		}

		// Room for six before it would run into the buttons at the bottom. More than that on one
		// server means something has gone wrong socially, not technically.
		for (int index = 0; index < Math.min(entries.size(), 6); index++) {
			PlanListPayload.Entry entry = entries.get(index);
			int entryY = rowY + index * 24;
			boolean open = entry.name().equals(ClientSync.openShared());

			Button pick = Button.builder(
					Component.literal(fit((open ? "> " : "") + entry.name(), 220)), button -> {
						ClientSync.open(entry.name());
						setStatus(Lang.t("share.opening", entry.name()), OK_COLOR);
					}).bounds(left, entryY, 232, 20).build();
			pick.active = !open;
			addRenderableWidget(pick);

			addRenderableWidget(Button.builder(Lang.c("share.remove"), button -> {
				ClientSync.remove(entry.name());
				setStatus(Lang.t("share.removed", entry.name()), OK_COLOR);
				rebuildWidgets();
			}).bounds(left + 236, entryY, 72, 20).build());

			label(left + 4, entryY + 24, () -> "", () -> DIM_COLOR, false);
		}

		int bottom = rowY + Math.max(1, Math.min(entries.size(), 6)) * 24 + 12;

		Button share = Button.builder(Lang.c("share.push"), button -> {
			if (ClientSync.share()) {
				setStatus(Lang.t("share.pushed", PlanManager.plan().name()), OK_COLOR);
				rebuildWidgets();
			}
		}).bounds(left, bottom, 150, 20).build();
		share.active = PlanManager.hasPlan();
		addRenderableWidget(share);

		addRenderableWidget(Button.builder(Lang.c("share.refresh"), button -> {
			ClientSync.open(ClientSync.openShared());
			rebuildWidgets();
		}).bounds(right, bottom, 150, 20).build());

		label(width / 2, bottom + 26, () -> ClientSync.shared()
				? Lang.t("share.on", ClientSync.openShared())
				: Lang.t("share.off"), () -> DIM_COLOR, true);
		label(width / 2, bottom + 38, () -> Lang.t("share.hint"), () -> DIM_COLOR, true);
	}

	// ----------------------------------------------------------------- display

	private void initDisplay() {
		BeaconatorConfig config = BeaconatorConfig.get();
		int left = width / 2 - 154;
		int right = width / 2 + 4;
		int y = 32;
		// Fourteen rows have to fit above the Done button, and at a big gui scale the screen is
		// only 240 tall. Derive the spacing from the room there actually is rather than assuming
		// 22 and running off the bottom of small screens.
		int step = Math.clamp((height - 34 - y) / 14, 17, 22);

		// displayOnlyValue, or the widget prefixes the value with an empty message and a stray
		// ": " shows up at the front of the button.
		addRenderableWidget(Cycler.<Lang.Mode>builder(value -> Component.literal(fit(switch (value) {
					case AUTO -> Lang.t("display.language") + ": auto";
					case ENGLISH -> Lang.t("display.language") + ": English";
					case SPANISH -> Lang.t("display.language") + ": Espanol";
				}, 142)), config.language)
				.withValues(Lang.Mode.values())
				.displayOnlyValue()
				.create(left, y, 150, 20, Component.empty(), (button, value) -> {
					config.language = value;
					config.save();
					rebuildWidgets();
				}));

		onOff(right, y, "display.hud", config.showHud, value -> config.showHud = value);
		onOff(left, y + step, "display.coverage", config.renderCoverage,
				value -> config.renderCoverage = value);
		onOff(right, y + step, "display.outlines", config.renderWireframe,
				value -> config.renderWireframe = value);

		addRenderableWidget(Cycler.<CoverageStyle>builder(
						value -> Component.literal(fit(switch (value) {
							case SLAB -> Lang.t("display.style_slab");
							case FLOOR -> Lang.t("display.style_floor");
							case FULL -> Lang.t("display.style_full");
						}, 142)), config.coverageStyle)
				.withValues(CoverageStyle.values())
				.displayOnlyValue()
				.create(left, y + step * 2, 150, 20, Component.empty(), (button, value) -> {
					config.coverageStyle = value;
					config.save();
				}));

		onOff(right, y + step * 2, "display.see_through", config.seeThrough,
				value -> config.seeThrough = value);

		stepper(left, y + step * 3, Lang.t("display.opacity"), () -> Math.round(config.coverageOpacity * 100),
				value -> {
					config.coverageOpacity = Math.clamp(value / 100.0f, 0.0f, 1.0f);
					config.save();
				}, 0, 100, 5);

		stepper(right, y + step * 3, Lang.t("display.distance"), () -> config.maxRenderDistance, value -> {
			config.maxRenderDistance = Math.max(64, value);
			config.save();
		}, 64, 8192, 128);

		onOff(left, y + step * 4, "display.beacon_boxes", config.renderBeaconMarkers,
				value -> config.renderBeaconMarkers = value);
		onOff(right, y + step * 4, "display.pyramid_outline", config.renderPyramidFootprint,
				value -> config.renderPyramidFootprint = value);
		onOff(left, y + step * 5, "display.progress", config.showProgressColor,
				value -> config.showProgressColor = value);
		onOff(right, y + step * 5, "display.easy_place", config.easyPlace,
				value -> config.easyPlace = value);
		onOff(left, y + step * 6, "display.strict", config.strictPlacement,
				value -> config.strictPlacement = value);

		CycleButton<Boolean> follow = onOff(right, y + step * 6, "display.follow_litematica",
				config.followLitematicaEasyPlace, value -> config.followLitematicaEasyPlace = value);
		follow.active = LitematicaBridge.installed();

		onOff(left, y + step * 7, "display.beams", config.showBeams,
				value -> config.showBeams = value);

		// In pixels, not blocks: a beam is only useful if it stays visible from across the site.
		stepper(right, y + step * 7, Lang.t("display.beam_pixels"),
				() -> Math.round(config.beamMinPixels), value -> {
					config.beamMinPixels = Math.clamp(value, 0, 16);
					config.save();
				}, 0, 16, 1);

		// Colours, on the tab where you are already looking at the thing they colour. Seven of
		// them fill four rows of two, and the toggle for shading excluded nodes takes the eighth
		// slot: it belongs with the colours and it saves a row, which this tab has run out of.
		int colourY = y + step * 8;
		colourButton(left, colourY, 150, "colour.pending", () -> config.colorPending,
				value -> config.colorPending = value);
		colourButton(right, colourY, 150, "colour.partial", () -> config.colorPartial,
				value -> config.colorPartial = value);
		colourButton(left, colourY + step, 150, "colour.placed", () -> config.colorPlaced,
				value -> config.colorPlaced = value);
		colourButton(right, colourY + step, 150, "colour.excluded", () -> config.colorExcluded,
				value -> config.colorExcluded = value);
		colourButton(left, colourY + step * 2, 150, "colour.removed", () -> config.colorRemoved,
				value -> config.colorRemoved = value);
		colourButton(right, colourY + step * 2, 150, "colour.beam", () -> config.colorBeamPending,
				value -> config.colorBeamPending = value);
		colourButton(left, colourY + step * 3, 150, "colour.excluded_done", () -> config.colorExcludedDone,
				value -> config.colorExcludedDone = value);
		onOff(right, colourY + step * 3, "display.shade_excluded", config.shadeExcluded,
				value -> config.shadeExcluded = value);

		int layerY = colourY + step * 4;
		addRenderableWidget(Button.builder(
				Component.literal(Lang.t("display.layers", LayerFilter.describe())), button -> {
					if (LayerFilter.active()) {
						LayerFilter.all();
					} else if (Minecraft.getInstance().player != null) {
						LayerFilter.single(Minecraft.getInstance().player.getBlockY());
					}

					rebuildWidgets();
				}).bounds(left, layerY, 150, 20).build());

		// Greyed out on "all layers": they do nothing there, and a button that does nothing when
		// you press it is worse than one you can see is unavailable.
		Button layerUp = Button.builder(Lang.c("display.layer_up"), button -> {
			LayerFilter.shift(1);
			rebuildWidgets();
		}).bounds(right, layerY, 74, 20).build();
		layerUp.active = LayerFilter.active();
		addRenderableWidget(layerUp);

		Button layerDown = Button.builder(Lang.c("display.layer_down"), button -> {
			LayerFilter.shift(-1);
			rebuildWidgets();
		}).bounds(right + 76, layerY, 74, 20).build();
		layerDown.active = LayerFilter.active();
		addRenderableWidget(layerDown);

		// The water lines get one row here rather than a tab of their own settings: seeing them or
		// not is the same kind of choice as seeing the coverage or not.
		onOff(left, layerY + step, "water.show", config.renderWater,
				value -> config.renderWater = value);
		colourButton(right, layerY + step, 150, "water.materials", () -> config.colorWater,
				value -> config.colorWater = value);
	}

	// ----------------------------------------------------------------- actions

	private void newPlan() {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || mc.level == null) {
			return;
		}

		PlanManager.autoSave();
		String name = nameBox == null || nameBox.getValue().isBlank() ? "perimeter" : nameBox.getValue().trim();
		PerimeterPlan plan = new PerimeterPlan(name, mc.level.dimension().location().toString(),
				mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
		PlanManager.setPlan(plan);
		PlanManager.setEditMode(true);
		PlanManager.save();
		ScanCache.clear();
		setStatus(Lang.t("plan.created", name), OK_COLOR);
		activeTab = Tab.GRID;
		rebuildWidgets();
	}

	private void applyCentre() {
		if (!PlanManager.hasPlan()) {
			return;
		}

		try {
			PlanManager.plan().setCenter(
					Integer.parseInt(centerX.getValue().trim()),
					Integer.parseInt(centerY.getValue().trim()),
					Integer.parseInt(centerZ.getValue().trim()));
			ScanCache.clear();
			touch();
			setStatus(Lang.t("plan.centre_moved"), OK_COLOR);
		} catch (NumberFormatException e) {
			setStatus(Lang.t("plan.bad_numbers"), WARN_COLOR);
		}
	}

	private void centreHere() {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || !PlanManager.hasPlan()) {
			return;
		}

		PlanManager.plan().setCenter(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
		ScanCache.clear();
		touch();
		rebuildWidgets();
	}

	private void detect() {
		try {
			WorldGridDetector.Result result = WorldGridDetector.detect(Minecraft.getInstance(),
					nameBox == null || nameBox.getValue().isBlank() ? "detected" : nameBox.getValue().trim(), 256);
			PlanManager.autoSave();
			PlanManager.setPlan(result.plan());
			ScanCache.clear();
			PlanManager.save();
			setStatus(Lang.t("plan.detected", result.beaconsFound(),
					result.plan().extents().columns(), result.plan().extents().rows(),
					result.plan().spacing()), OK_COLOR);
			activeTab = Tab.GRID;
			rebuildWidgets();
		} catch (IllegalStateException | IllegalArgumentException e) {
			setStatus(e.getMessage(), WARN_COLOR);
		}
	}

	private void save() {
		if (!PlanManager.hasPlan()) {
			return;
		}

		if (nameBox != null && !nameBox.getValue().isBlank()) {
			PlanManager.plan().setName(nameBox.getValue().trim());
		}

		boolean saved = PlanManager.save();
		setStatus(saved ? Lang.t("plan.saved") : Lang.t("plan.save_failed"), saved ? OK_COLOR : WARN_COLOR);
	}

	private void openPlan() {
		minecraft.setScreen(new PickerScreen(this, Lang.t("plan.pick_open"), Lang.t("plan.open"),
				PlanManager.savedPlans(), name -> {
					PlanManager.autoSave();

					if (PlanManager.load(name)) {
						ScanCache.clear();
						setStatus(Lang.t("plan.opened", name), OK_COLOR);
					} else {
						setStatus(Lang.t("plan.open_failed", name), WARN_COLOR);
					}
				}));
	}

	private void deletePlan() {
		minecraft.setScreen(new PickerScreen(this, Lang.t("plan.pick_delete"), Lang.t("plan.delete"),
				PlanManager.savedPlans(), name -> {
					try {
						PlanStore.delete(PlanManager.worldId(), name);

						if (PlanManager.hasPlan() && PlanManager.plan().name().equals(name)) {
							PlanManager.closePlan();
						}

						setStatus(Lang.t("plan.deleted", name), OK_COLOR);
					} catch (IOException e) {
						setStatus(Lang.t("plan.delete_failed"), WARN_COLOR);
					}
				}));
	}

	private void importSchematic() {
		minecraft.setScreen(new PickerScreen(this, Lang.t("plan.pick_import"), Lang.t("plan.import"),
				SchematicFiles.list(), file -> {
					Minecraft mc = Minecraft.getInstance();

					if (mc.level == null) {
						return;
					}

					Path path = SchematicFiles.resolve(file);

					try {
						int[] fallback = mc.player == null ? null
								: new int[] {mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()};
						boolean placedByHand = !LitematicIO.hasOrigin(path);
						PerimeterPlan plan = LitematicIO.read(path, SchematicFiles.stripExtension(file),
								mc.level.dimension().location().toString(), fallback);
						PlanManager.autoSave();
						PlanManager.setPlan(plan);
						ScanCache.clear();
						PlanManager.save();
						setStatus(placedByHand
								? Lang.t("plan.imported_loose")
								: Lang.t("plan.imported", plan.extents().nodeCount()), OK_COLOR);
					} catch (IOException | RuntimeException e) {
						BeaconatorClient.LOGGER.warn("Import failed", e);
						setStatus(Lang.t("plan.import_failed", String.valueOf(e.getMessage())), WARN_COLOR);
					}
				}));
	}

	/**
	 * Writes the schematic on a worker thread.
	 *
	 * <p>A schematic covers the plan's whole bounding box, and a perimeter is enormous: 17x17
	 * nodes at 101 spacing is 1616 x 5 x 1616, thirteen million block slots. Doing that on the
	 * render thread froze the game long enough for the server to drop the connection, which is a
	 * memorable way to find out that a button is doing too much work.
	 */
	private void exportSchematic() {
		if (!PlanManager.hasPlan()) {
			return;
		}

		PerimeterPlan plan = PlanManager.plan();
		Minecraft mc = Minecraft.getInstance();
		String author = mc.player == null ? "" : mc.player.getGameProfile().getName();
		String name = plan.name();
		setStatus(Lang.t("plan.exporting", name), DIM_COLOR);

		CompletableFuture.runAsync(() -> {
			try {
				LitematicIO.write(SchematicFiles.resolve(name), plan, author);
				mc.execute(() -> setStatus(Lang.t("plan.exported", name), OK_COLOR));
			} catch (IOException | RuntimeException e) {
				BeaconatorClient.LOGGER.warn("Export failed", e);
				mc.execute(() -> setStatus(
						Lang.t("plan.export_failed", String.valueOf(e.getMessage())), WARN_COLOR));
			}
		});
	}

	// ----------------------------------------------------------------- helpers

	private void stepper(int x, int y, String label, IntSupplier get, IntConsumer set, int min, int max) {
		stepper(x, y, label, get, set, min, max, 1);
	}

	/**
	 * Trims text to a pixel width, with an ellipsis when it does not fit.
	 *
	 * <p>Every label here goes through this. Minecraft centres button text and lets it spill out
	 * of both ends, so a Spanish string that is a third longer than the English one turns into an
	 * unreadable stub rather than something obviously too long.
	 */
	private String fit(String text, int room) {
		if (font.width(text) <= room) {
			return text;
		}

		return font.plainSubstrByWidth(text, Math.max(0, room - font.width("..."))) + "...";
	}

	/** An on/off toggle that leaves room for the ": ON" the widget appends. */
	private CycleButton<Boolean> onOff(int x, int y, String key, boolean value, Consumer<Boolean> set) {
		String label = fit(Lang.t(key), 142 - font.width(": OFF"));
		CycleButton<Boolean> button = CycleButton.onOffBuilder(value)
				.create(x, y, 150, 20, Component.literal(label), (widget, picked) -> {
					set.accept(picked);
					BeaconatorConfig.get().save();
				});

		addRenderableWidget(button);
		return button;
	}

	/** A number with a bar: ends, left and right click, and the scroll wheel. */
	private void stepper(int x, int y, String label, IntSupplier get, IntConsumer set,
			int min, int max, int step) {
		addRenderableWidget(new StepperWidget(x, y, 150, 20, label, get, set, min, max, step,
				this::rebuildWidgets));
	}

	private void label(int x, int y, Supplier<String> text, Supplier<Integer> color, boolean centered) {
		label(x, y, text, color, centered, false);
	}

	private void label(int x, int y, Supplier<String> text, Supplier<Integer> color,
			boolean centered, boolean rightAligned) {
		labels.add(new Label(x, y, text, color, centered, rightAligned));
	}

	/** "+X", "-Z" and friends, for the rotation button. */
	private static String directionName(RowAxis axis) {
		return switch (axis) {
			case X -> "+X";
			case Z -> "+Z";
			case X_NEGATIVE -> "-X";
			case Z_NEGATIVE -> "-Z";
		};
	}

	/**
	 * Colours you can actually tell apart on a map made of greens, greys and blues. Cycled with a
	 * button rather than typed as hex: this is the sort of thing you fix while looking at it.
	 */
	private static final String[] PALETTE_NAMES = {
		"colour.white", "colour.yellow", "colour.orange", "colour.red", "colour.pink",
		"colour.purple", "colour.blue", "colour.cyan", "colour.green", "colour.lime",
		"colour.grey", "colour.dark_grey"
	};

	private static final int[] PALETTE = {
		0xFFFFFFFF, 0xFFFFD23F, 0xFFFF9838, 0xFFFF3B30, 0xFFFF5BD0, 0xFFB06BFF,
		0xFF4C8DFF, 0xFF35D6D6, 0xFF57E36B, 0xFFB6FF5B, 0xFF9AA0A8, 0xFF4A4E55
	};

	/** One colour setting. A button, not a bar: a palette has no more and no less. */
	private void colourButton(int x, int y, int width, String key, IntSupplier get, IntConsumer set) {
		String[] names = new String[PALETTE.length];

		for (int i = 0; i < PALETTE.length; i++) {
			names[i] = Lang.t(PALETTE_NAMES[i]);
		}

		addRenderableWidget(new ColourButton(x, y, width, Lang.t(key), PALETTE, names, get, value -> {
			set.accept(value);
			BeaconatorConfig.get().save();
		}, this::rebuildWidgets));
	}

	private String coverageNote() {
		PerimeterPlan plan = PlanManager.plan();
		int gap = Math.max(plan.gapAcrossRow(), plan.gapAlongRow());

		if (gap > 0) {
			return Lang.t("grid.gap", gap);
		}

		if (gap < 0) {
			return Lang.t("grid.overlap", -gap);
		}

		return Lang.t("grid.fits");
	}

	private int coverageColor() {
		return Math.max(PlanManager.plan().gapAcrossRow(), PlanManager.plan().gapAlongRow()) > 0
				? WARN_COLOR : OK_COLOR;
	}

	private void touch() {
		PlanManager.markDirty();
	}

	private void setStatus(String text, int color) {
		status = text == null ? "" : text;
		statusColor = color;
	}

	private static String shortName(String blockId) {
		int colon = blockId.indexOf(':');
		return colon < 0 ? blockId : blockId.substring(colon + 1);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);

		for (Label label : labels) {
			String text = label.text().get();

			if (label.centered()) {
				graphics.drawCenteredString(font, text, label.x(), label.y(), label.color().get());
			} else {
				int x = label.rightAligned() ? label.x() - font.width(text) : label.x();
				graphics.drawString(font, text, x, label.y(), label.color().get(), false);
			}
		}

		if (activeTab == Tab.MATERIALS && PlanManager.hasPlan()) {
			renderMaterials(graphics);
		}

		if (activeTab == Tab.MAP && PlanManager.hasPlan()) {
			renderMap(graphics, mouseX, mouseY);
		}

		if (activeTab.section == Section.WATER && !BeaconatorConfig.get().waterIntroSeen) {
			renderWaterIntro(graphics);
		} else if (activeTab == Tab.WATER_MAP && PlanManager.hasPlan()) {
			renderWater(graphics, mouseX, mouseY);
		} else if (activeTab == Tab.WATER_COST && PlanManager.hasPlan()) {
			renderWaterCost(graphics);
		} else if (activeTab == Tab.WATER_SETUP && PlanManager.hasPlan()) {
			renderWaterSetup(graphics);
		}

		if (!status.isEmpty()) {
			graphics.drawCenteredString(font, status, width / 2, height - 44, statusColor);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		PlanManager.autoSave();
		MapStore.saveAll();

		// Channels drawn on a shared plan go up when the screen closes, in one piece. Sending the
		// whole plan on every stroke would be hundreds of kilobytes for a line you might undo.
		if (waterTouched) {
			ClientSync.resend();
			waterTouched = false;
		}
		minecraft.setScreen(parent);
	}

	/** Opened from a key binding, Mod Menu or {@code /bea gui}. */
	public static void open() {
		Minecraft.getInstance().setScreen(new BeaconatorScreen(null));
	}
}
