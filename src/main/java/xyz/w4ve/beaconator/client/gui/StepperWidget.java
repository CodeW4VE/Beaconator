package xyz.w4ve.beaconator.client.gui;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A number you can change every way you might try to: the minus and plus ends, left click to go
 * up and right click to go down anywhere on it, and the scroll wheel over it.
 *
 * <p>It also fills like a bar, so where the value sits between its limits is visible without
 * reading the number. Two 20 pixel buttons with a label wedged between them told you nothing and
 * only answered to a precise click on the right end.
 */
public class StepperWidget extends AbstractWidget {
	private static final int BORDER = 0xFFA0A0A0;
	private static final int BACKGROUND = 0xFF101318;
	private static final int FILL = 0xFF3A5A78;
	private static final int FILL_HOVER = 0xFF4E7BA1;
	private static final int END = 0x40FFFFFF;

	private final IntSupplier get;
	private final IntConsumer set;
	private final int min;
	private final int max;
	private final int step;
	private final Runnable onChange;
	private final String label;

	public StepperWidget(int x, int y, int width, int height, String label, IntSupplier get,
			IntConsumer set, int min, int max, int step, Runnable onChange) {
		super(x, y, width, height, Component.literal(label));
		this.label = label;
		this.get = get;
		this.set = set;
		this.min = min;
		this.max = max;
		this.step = step;
		this.onChange = onChange;
	}

	private void nudge(int direction) {
		int wanted = (int) Math.clamp((long) get.getAsInt() + (long) direction * step, min, max);

		if (wanted != get.getAsInt()) {
			set.accept(wanted);

			if (onChange != null) {
				onChange.run();
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!active || !visible || !isMouseOver(mouseX, mouseY)) {
			return false;
		}

		// The ends always mean what they say. Everywhere else, left goes up and right goes down,
		// so you never have to aim.
		if (mouseX < getX() + 20) {
			nudge(-1);
		} else if (mouseX > getX() + width - 20) {
			nudge(1);
		} else if (button == 1) {
			nudge(-1);
		} else if (button == 0) {
			nudge(1);
		} else {
			return false;
		}

		playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!active || !visible || !isMouseOver(mouseX, mouseY) || scrollY == 0) {
			return false;
		}

		nudge(scrollY > 0 ? 1 : -1);
		return true;
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		int value = get.getAsInt();
		int span = Math.max(1, max - min);
		int filled = (int) ((width - 2) * (double) Math.clamp(value - min, 0, span) / span);

		graphics.fill(getX(), getY(), getX() + width, getY() + height, BORDER);
		graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, BACKGROUND);
		graphics.fill(getX() + 1, getY() + 1, getX() + 1 + filled, getY() + height - 1,
				isHovered() ? FILL_HOVER : FILL);

		// The two ends, marked so it still reads as something you can press.
		graphics.fill(getX() + 20, getY() + 1, getX() + 21, getY() + height - 1, END);
		graphics.fill(getX() + width - 21, getY() + 1, getX() + width - 20, getY() + height - 1, END);

		var font = net.minecraft.client.Minecraft.getInstance().font;
		int textY = getY() + (height - 8) / 2;
		graphics.drawString(font, "-", getX() + 9, textY, 0xFFFFFFFF, false);
		graphics.drawString(font, "+", getX() + width - 12, textY, 0xFFFFFFFF, false);

		String text = label + ": " + value;
		int room = width - 46;

		if (font.width(text) > room) {
			text = font.plainSubstrByWidth(label, Math.max(0, room - font.width("...: " + value)))
					+ "...: " + value;
		}

		graphics.drawCenteredString(font, text, getX() + width / 2, textY, 0xFFFFFFFF);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {
		defaultButtonNarrationText(narration);
	}
}
