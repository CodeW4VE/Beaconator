package xyz.w4ve.beaconator.client.gui;

import java.util.function.Function;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;

/**
 * One line around {@link CycleButton#builder}, so that the version specific part of building a
 * cycling button lives in one file rather than at five call sites.
 *
 * <p>This is the 1.21.11 and newer version: the starting value is an argument to the builder,
 * and {@code withInitialValue} no longer exists.
 */
public final class Cycler {
	private Cycler() {
	}

	public static <T> CycleButton.Builder<T> builder(Function<T, Component> naming, T initial) {
		return CycleButton.builder(naming, initial);
	}
}
