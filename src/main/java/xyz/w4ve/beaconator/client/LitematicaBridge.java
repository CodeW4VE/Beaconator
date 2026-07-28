package xyz.w4ve.beaconator.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import xyz.w4ve.beaconator.BeaconatorClient;
import xyz.w4ve.beaconator.config.BeaconatorConfig;

/**
 * Reads Litematica's easy place toggle, when Litematica is installed, so the two do not fight
 * over the same click.
 *
 * <p>Everything here is reflection wrapped in try/catch on purpose. There is no compile time
 * dependency on Litematica and no code taken from it, so an update on their side can at worst
 * make this stop syncing, never crash.
 */
public final class LitematicaBridge {
	private static final String CONFIG_CLASS = "fi.dy.masa.litematica.config.Configs$Generic";
	private static final String FIELD = "EASY_PLACE_MODE";

	private static boolean resolved;
	private static Object configOption;
	private static Method getter;
	private static Boolean lastSeen;
	private static boolean warned;

	private LitematicaBridge() {
	}

	public static boolean installed() {
		return FabricLoader.getInstance().isModLoaded("litematica");
	}

	/** Litematica's easy place state, or null when it cannot be read. */
	public static Boolean easyPlaceEnabled() {
		if (!installed()) {
			return null;
		}

		if (!resolved) {
			resolve();
		}

		if (configOption == null || getter == null) {
			return null;
		}

		try {
			return (Boolean) getter.invoke(configOption);
		} catch (ReflectiveOperationException | RuntimeException e) {
			warnOnce(e);
			return null;
		}
	}

	private static void resolve() {
		resolved = true;

		try {
			Class<?> configs = Class.forName(CONFIG_CLASS);
			Field field = configs.getField(FIELD);
			configOption = field.get(null);
			getter = configOption.getClass().getMethod("getBooleanValue");
			BeaconatorClient.LOGGER.info("Litematica found, easy place will follow its toggle");
		} catch (ReflectiveOperationException | RuntimeException e) {
			warnOnce(e);
		}
	}

	private static void warnOnce(Exception e) {
		if (!warned) {
			warned = true;
			BeaconatorClient.LOGGER.info("Litematica is installed but its easy place toggle could not "
					+ "be read, so the two stay independent ({})", e.toString());
		}
	}

	/** Mirrors their toggle onto ours, telling the player the first time it happens. */
	public static void tick() {
		BeaconatorConfig config = BeaconatorConfig.get();

		if (!config.followLitematicaEasyPlace) {
			return;
		}

		Boolean theirs = easyPlaceEnabled();

		if (theirs == null || theirs.equals(lastSeen)) {
			return;
		}

		lastSeen = theirs;

		if (config.easyPlace == theirs) {
			return;
		}

		config.easyPlace = theirs;
		config.save();
		PlanManager.actionBar(Component.literal("Beaconator easy place "
				+ (theirs ? "on" : "off") + " (following Litematica)")
				.withStyle(ChatFormatting.GRAY));
	}
}
