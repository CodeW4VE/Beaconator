package xyz.w4ve.beaconator.client.gui;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Picks a colour out of a palette: a normal button that steps through it, with the colour itself
 * shown on the right.
 *
 * <p>Deliberately not the slider widget the numbers use. A palette has no more and no less, so a
 * bar filling up as you go through it means nothing, and it drew every colour half full besides.
 * Left click goes forward, right click back, the wheel does the same, and it wraps: there is no
 * end of a palette to get stuck at.
 */
public class ColourButton extends Button {
	private final int[] palette;
	private final String[] names;
	private final IntSupplier get;
	private final IntConsumer set;
	private final String label;
	private final Runnable onChange;

	public ColourButton(int x, int y, int width, String label, int[] palette, String[] names,
			IntSupplier get, IntConsumer set, Runnable onChange) {
		super(x, y, width, 20, Component.empty(), button -> {
		}, DEFAULT_NARRATION);
		this.palette = palette;
		this.names = names;
		this.get = get;
		this.set = set;
		this.label = label;
		this.onChange = onChange;
	}

	private int index() {
		int current = get.getAsInt();

		for (int i = 0; i < palette.length; i++) {
			if (palette[i] == current) {
				return i;
			}
		}

		return 0;
	}

	private void step(int direction) {
		set.accept(palette[Math.floorMod(index() + direction, palette.length)]);

		if (onChange != null) {
			onChange.run();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!active || !visible || !isMouseOver(mouseX, mouseY) || button > 1) {
			return false;
		}

		step(button == 1 ? -1 : 1);
		playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!active || !visible || !isMouseOver(mouseX, mouseY) || scrollY == 0) {
			return false;
		}

		step(scrollY > 0 ? 1 : -1);
		return true;
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		var font = net.minecraft.client.Minecraft.getInstance().font;
		// The swatch sits on the right, so the label keeps the width it would have anyway.
		setMessage(Component.literal(fit(font, label + ": " + names[index()], width - 40)));
		super.renderWidget(graphics, mouseX, mouseY, delta);

		int right = getX() + width - 6;
		graphics.fill(right - 14, getY() + 4, right, getY() + 16, 0xFF000000);
		graphics.fill(right - 13, getY() + 5, right - 1, getY() + 15, get.getAsInt());
	}

	private static String fit(net.minecraft.client.gui.Font font, String text, int room) {
		if (font.width(text) <= room) {
			return text;
		}

		return font.plainSubstrByWidth(text, Math.max(0, room - font.width("..."))) + "...";
	}
}
