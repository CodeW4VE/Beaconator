package xyz.w4ve.beaconator.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import xyz.w4ve.beaconator.BeaconatorClient;

/** Everything the player can tweak, stored in {@code config/beaconator.json}. */
public class BeaconatorConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static BeaconatorConfig instance;

	/**
	 * The master switch. Off means the mod draws nothing, reads nothing and touches no placement:
	 * as close to not having it installed as you can get without deleting the jar.
	 *
	 * <p>Deliberately does not touch any other setting, so turning it back on restores exactly the
	 * setup you had. The screen and {@code /bea} stay available, or there would be no way back.
	 */
	public boolean enabled = true;

	// -- render ---------------------------------------------------------------
	/** Translucent faces of the coverage volume. */
	public boolean renderCoverage = true;
	/** Edges of the coverage volume. */
	public boolean renderWireframe = true;
	/** How much of the coverage volume to draw. */
	public CoverageStyle coverageStyle = CoverageStyle.SLAB;
	/** Height of the slab drawn in SLAB and FLOOR styles. */
	public int slabThickness = 1;
	public float coverageOpacity = 0.10f;
	public float wireframeOpacity = 0.85f;
	public float lineWidth = 2.0f;
	/** Draw the coverage through terrain. Off means it hides behind hills like a normal box. */
	public boolean seeThrough = true;
	/** A small box on each beacon position, so you can see exactly where they go. */
	public boolean renderBeaconMarkers = true;
	/** Outline of the pyramid base on the ground. */
	public boolean renderPyramidFootprint = true;
	/** Nodes further away than this are skipped. Coverage boxes are 101 blocks wide. */
	public int maxRenderDistance = 1024;
	/** Hard cap on nodes drawn in one frame, after the frustum has had its say. */
	public int maxNodesDrawn = 512;

	// -- beams ----------------------------------------------------------------
	/** A beam on every beacon position, so you can see where to dig from a distance. */
	public boolean showBeams = true;
	/**
	 * How tall the beams stand, in blocks. Vanilla draws a beacon beam 1024 blocks up, which is
	 * what makes it read as "goes to the sky"; anything shorter stops in mid air and stops being
	 * the landmark you are looking for from the far side of a perimeter.
	 */
	public int beamHeight = 1024;
	/** How far the beams also run below the beacon, for when you are digging under the site. */
	public int beamDepth = 0;
	/** Half width of a beam up close. A real beacon beam is about this thick. */
	public float beamWidth = 0.22f;
	/**
	 * Beams thinner than this on screen are widened until they are not. A 0.44 wide beam is barely
	 * one pixel at 250 blocks, and a perimeter is over a thousand blocks across.
	 */
	public float beamMinPixels = 3.0f;
	public float beamOpacity = 0.65f;

	// -- hud ------------------------------------------------------------------
	public boolean showHud = true;
	/** 0 top left, 1 top right, 2 bottom left, 3 bottom right. */
	public int hudCorner = 0;
	public int hudOffsetX = 4;
	public int hudOffsetY = 4;

	// -- colours (ARGB) -------------------------------------------------------
	public int colorPending = 0xFFFFFFFF;
	public int colorPlaced = 0xFF57E36B;
	/**
	 * Started but not finished, which includes a node whose pyramid is up but is one beacon short.
	 * Green used to creep in as the blocks went in, so a node missing its second beacon looked
	 * done from any distance.
	 */
	public int colorPartial = 0xFFFFD23F;
	/** Dark grey rather than near black: the old value was invisible against stone. */
	public int colorExcluded = 0xFF6C7078;
	/**
	 * An excluded node that is finished. Its own colour rather than a blend of the other two,
	 * because half the nodes of a big perimeter are excluded and you want to choose how they read.
	 */
	public int colorExcludedDone = 0xFF35D6D6;
	public int colorHover = 0xFFFFC24A;
	/**
	 * Dropped nodes, kept apart from excluded ones. They used to share a colour, which left no
	 * way to make "outside the perimeter" visible without also lighting up the two hundred nodes
	 * you deleted.
	 */
	public int colorRemoved = 0xFF4A4E55;
	/** Beam colours: red for what is missing, green for what is done, grey for excluded. */
	public int colorBeamPending = 0xFFFF3B30;
	public int colorBeamPlaced = 0xFF57E36B;
	public int colorBeamPartial = 0xFFFFD23F;
	public int colorBeamExcluded = 0xFF9AA0A8;
	/** Used for the strip between nodes when the spacing leaves the perimeter uncovered. */
	public int colorGap = 0xFFFF4040;

	// -- editing --------------------------------------------------------------
	/** Scrolling in edit mode grows and shrinks the grid instead of changing hotbar slot. */
	public boolean scrollChangesRing = true;
	/** Picks the right block out of your hotbar for whatever spot you are aiming at. */
	public boolean easyPlace = false;
	/** With easy place on, refuse to place a plan block where the plan wants nothing. */
	public boolean strictPlacement = true;
	/** Show what is still missing in the HUD. */
	public boolean showMaterials = true;

	// -- language -------------------------------------------------------------
	/** English by default, whatever the game is set to. Toggle it on the Display tab. */
	public xyz.w4ve.beaconator.client.Lang.Mode language = xyz.w4ve.beaconator.client.Lang.Mode.ENGLISH;

	// -- litematica -----------------------------------------------------------
	/** Mirror Litematica's easy place toggle when it is installed. */
	public boolean followLitematicaEasyPlace = true;

	// -- scanning -------------------------------------------------------------
	/** Keep checking nearby nodes against the world, so finished ones turn green on their own. */
	public boolean autoScan = true;
	public int autoScanIntervalMs = 1000;
	/** Only nodes this close get rescanned automatically. Use /bea scan for a full sweep. */
	public int autoScanDistance = 256;
	/** Shade pending nodes towards green as their blocks go in. */
	public boolean showProgressColor = true;
	/**
	 * Excluded nodes get the same untouched, half built, done reading as the rest. Off means they
	 * stay flat grey whatever their state, which is what they did before and what you want if you
	 * care more about seeing the shape of the perimeter than its progress.
	 */
	public boolean shadeExcluded = true;

	// -- layers ---------------------------------------------------------------
	/** Which layer filter is on: 0 all, 1 a single layer, 2 a range. Kept between sessions. */
	public int layerMode;
	public int layerMin;
	public int layerMax;

	// -- keys -----------------------------------------------------------------
	/**
	 * Extra modifiers per binding, keyed by its translation key: 1 shift, 2 control, 4 alt.
	 * Vanilla key bindings are a single key, so this is what makes `Shift + G` and
	 * `Ctrl + Shift + G` different bindings.
	 */
	public Map<String, Integer> keyModifiers = new LinkedHashMap<>();

	// -- session --------------------------------------------------------------
	/**
	 * Last plan opened on each server or world, so rejoining picks up where you left off instead
	 * of dropping you into an empty screen every single time.
	 */
	public Map<String, String> lastPlan = new LinkedHashMap<>();
	/** Reopen that plan on join. Off means you always start with nothing loaded. */
	public boolean reopenLastPlan = true;

	public static BeaconatorConfig get() {
		if (instance == null) {
			instance = load();
		}

		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("beaconator.json");
	}

	private static BeaconatorConfig load() {
		Path path = path();

		if (Files.exists(path)) {
			try {
				String json = Files.readString(path, StandardCharsets.UTF_8);
				BeaconatorConfig loaded = GSON.fromJson(json, BeaconatorConfig.class);

				if (loaded != null) {
					loaded.migrate();
					return loaded;
				}
			} catch (IOException | RuntimeException e) {
				BeaconatorClient.LOGGER.warn("Could not read {}, falling back to defaults", path, e);
			}
		}

		BeaconatorConfig fresh = new BeaconatorConfig();
		fresh.save();
		return fresh;
	}

	/**
	 * Pulls a saved config forward when a default turns out to have been a bad one. Only values
	 * left untouched at the old default are moved, so anything the player picked on purpose stays.
	 */
	private void migrate() {
		if (lastPlan == null) {
			lastPlan = new LinkedHashMap<>();
		}

		if (keyModifiers == null) {
			keyModifiers = new LinkedHashMap<>();
		}

		if (colorExcluded == 0xFF303030) {
			colorExcluded = 0xFF6C7078;
		}

		if (beamHeight == 96) {
			beamHeight = 1024;
		}
	}

	public void save() {
		try {
			Path path = path();
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			BeaconatorClient.LOGGER.warn("Could not save the config", e);
		}
	}
}
