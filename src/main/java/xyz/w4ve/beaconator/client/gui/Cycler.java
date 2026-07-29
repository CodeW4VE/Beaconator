package xyz.w4ve.beaconator.client.gui;

import java.util.function.Function;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;

/**
 * One line around {@link CycleButton#builder}, so that the version specific part of building a
 * cycling button lives in one file rather than at five call sites.
 *
 * <p>Up to 1.21.8 a cycling button is told its starting value after it is built, with
 * {@code withInitialValue}; from 1.21.9 the builder takes it up front and that method is gone.
 * The arguments are the same either way, so the screens call this and stay one copy.
 */
public final class Cycler {
	private Cycler() {
	}

	public static <T> CycleButton.Builder<T> builder(Function<T, Component> naming, T initial) {
		return CycleButton.<T>builder(naming).withInitialValue(initial);
	}
}
