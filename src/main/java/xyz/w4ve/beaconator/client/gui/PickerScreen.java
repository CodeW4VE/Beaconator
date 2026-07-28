package xyz.w4ve.beaconator.client.gui;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A scrollable list of names with a confirm button. Used to pick a saved plan or a schematic. */
public class PickerScreen extends Screen {
	private final Screen parent;
	private final List<String> options;
	private final Consumer<String> onPick;
	private final String confirmLabel;
	private NameList list;
	private Button confirm;

	public PickerScreen(Screen parent, String title, String confirmLabel, List<String> options,
			Consumer<String> onPick) {
		super(Component.literal(title));
		this.parent = parent;
		this.options = options;
		this.onPick = onPick;
		this.confirmLabel = confirmLabel;
	}

	@Override
	protected void init() {
		list = new NameList(minecraft, width, height - 96, 32, 20);

		for (String option : options) {
			list.add(option);
		}

		addRenderableWidget(list);

		confirm = Button.builder(Component.literal(confirmLabel), button -> pick())
				.bounds(width / 2 - 154, height - 52, 150, 20)
				.build();
		confirm.active = false;
		addRenderableWidget(confirm);

		addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
				.bounds(width / 2 + 4, height - 52, 150, 20)
				.build());
	}

	private void pick() {
		NameEntry selected = list.getSelected();

		if (selected != null) {
			onPick.accept(selected.name);
		}

		onClose();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);

		if (options.isEmpty()) {
			graphics.drawCenteredString(font, "Nothing here yet", width / 2, height / 2, 0xAAAAAA);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	private class NameList extends ObjectSelectionList<NameEntry> {
		NameList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
			super(minecraft, width, height, y, itemHeight);
		}

		/** addEntry is protected on the vanilla list, so entries go in from here. */
		void add(String name) {
			addEntry(new NameEntry(name));
		}

		@Override
		public void setSelected(NameEntry entry) {
			super.setSelected(entry);
			confirm.active = entry != null;
		}

		@Override
		public int getRowWidth() {
			return 260;
		}
	}

	private class NameEntry extends ObjectSelectionList.Entry<NameEntry> {
		private final String name;

		NameEntry(String name) {
			this.name = name;
		}

		@Override
		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
				int mouseX, int mouseY, boolean hovered, float partialTick) {
			graphics.drawString(font, name, left + 4, top + 5, 0xFFFFFF, false);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			list.setSelected(this);

			if (button == 0) {
				return true;
			}

			return false;
		}

		@Override
		public Component getNarration() {
			return Component.literal(name);
		}
	}
}
