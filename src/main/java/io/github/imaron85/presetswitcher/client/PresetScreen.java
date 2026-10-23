package io.github.imaron85.presetswitcher.client;

import io.github.imaron85.presetswitcher.core.Definitions;
import io.github.imaron85.presetswitcher.core.PresetEngine;
import io.github.imaron85.presetswitcher.core.Presets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?} else {
/*import net.minecraft.Util;
import net.minecraft.client.gui.navigation.CommonInputs;
*///?}

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists the pack's presets with their descriptions; used as first-launch chooser, from Options and from the loader's
 * mod list (Mod Menu / NeoForge / Forge config button). Widgets are positioned by hand (no layouts) so the same code
 * runs from Minecraft 1.20 to 26.x; it is rebuilt on every resize.
 */
public class PresetScreen extends Screen {
	private static final int HEADER_HEIGHT = 52;
	private static final int FOOTER_HEIGHT = 33;
	private static final int DESCRIPTION_COLOR = 0xFFA0A0A0;
	private static final int NAME_COLOR = 0xFFFFFFFF;

	private final Screen parent;
	private final Runnable firstLaunchNext;
	private final PresetEngine engine = ClientEngine.get();
	private PresetList list;
	private Button useButton;
	/** Selected preset id, kept across resizes (the widgets are rebuilt). */
	private String selectedId;

	private PresetScreen(Screen parent, Runnable firstLaunchNext) {
		super(title(ClientEngine.get()));
		this.parent = parent;
		this.firstLaunchNext = firstLaunchNext;
		this.selectedId = engine.effectivePreset();
	}

	/** Opened from the Options screen or the mod list; returns to {@code parent}. */
	public static PresetScreen open(Screen parent) {
		return new PresetScreen(parent, null);
	}

	/** Shown once on first launch, before the title screen; {@code next} continues vanilla's start-up (title screen or quick play). */
	public static PresetScreen firstLaunch(Runnable next) {
		Presets.log().info("showing the first-launch preset chooser");
		return new PresetScreen(null, next);
	}

	private static Component title(PresetEngine engine) {
		Definitions defs = engine.definitions();
		String custom = defs == null ? null : Texts.of(defs.title());
		return custom != null ? Component.literal(custom) : Texts.tr("title", "Settings Presets");
	}

	@Override
	protected void init() {
		Definitions defs = engine.definitions();
		int textWidth = Math.min(this.width - 40, 400);

		StringWidget titleWidget = new StringWidget(this.title, this.font);
		titleWidget.setX((this.width - titleWidget.getWidth()) / 2);
		titleWidget.setY(8);
		addRenderableWidget(titleWidget);
		MultiLineTextWidget subtitle = new MultiLineTextWidget(subtitle(defs), this.font).setMaxWidth(textWidth).setMaxRows(2).setCentered(true);
		subtitle.setX((this.width - subtitle.getWidth()) / 2);
		subtitle.setY(24);
		addRenderableWidget(subtitle);

		int footerY = this.height - FOOTER_HEIGHT + 6;
		if (defs != null) {
			list = new PresetList(this.minecraft, defs);
			addRenderableWidget(list);
			useButton = addRenderableWidget(Button.builder(Texts.tr("use", "Use Preset"), b -> useSelected())
					.bounds(this.width / 2 - 154, footerY, 150, 20).build());
			Component back = firstLaunchNext != null
					? Texts.tr("keep", "Keep %s", Texts.presetName(defs, engine.state().applied))
					: CommonComponents.GUI_CANCEL;
			addRenderableWidget(Button.builder(back, b -> onClose()).bounds(this.width / 2 + 4, footerY, 150, 20).build());
			// Keyboard: arrow keys pick a preset, Enter uses it, Escape keeps the current one
			setInitialFocus(list);
		} else {
			Component message = engine.loadError() != null
					? Texts.tr("error", "The preset definitions could not be loaded:").copy().append("\n").append(Component.literal(engine.loadError()).withStyle(ChatFormatting.RED))
					: Texts.tr("none", "This modpack does not define any presets (config/presetswitcher/presets.json5).");
			MultiLineTextWidget text = new MultiLineTextWidget(message, this.font).setMaxWidth(textWidth).setCentered(true);
			text.setX((this.width - text.getWidth()) / 2);
			text.setY(HEADER_HEIGHT + 10);
			addRenderableWidget(text);
			addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(this.width / 2 - 100, footerY, 200, 20).build());
		}
		updateButtons();
	}

	//? if <1.20.2 {
	/*@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
	}
	*///?}

	private Component subtitle(Definitions defs) {
		String custom = defs == null ? null : Texts.of(defs.subtitle());
		if (custom != null) return Component.literal(custom);
		if (firstLaunchNext != null) {
			return Texts.tr("subtitle.first", "Choose how the game should look and perform. You can change this later in Options > Presets...");
		}
		return Texts.tr("subtitle", "The chosen preset is applied the next time the game starts. Keybinds and other personal settings are kept.");
	}

	private void updateButtons() {
		if (useButton != null) useButton.active = list != null && list.getSelected() != null;
	}

	@Override
	public void onClose() {
		if (firstLaunchNext != null) {
			try {
				engine.dismissChooser();
			} catch (IOException e) {
				Presets.LOGGER.warn("could not save the preset state", e);
			}
		}
		leave();
	}

	private void leave() {
		if (firstLaunchNext != null) firstLaunchNext.run();
		else ScreenCompat.setScreen(parent);
	}

	private void useSelected() {
		PresetList.Entry selected = list == null ? null : list.getSelected();
		if (selected == null) return;
		boolean restart;
		try {
			restart = engine.choose(selected.preset.id());
		} catch (IOException e) {
			Presets.LOGGER.error("could not save the preset choice", e);
			return;
		}
		if (!restart) {
			leave();
			return;
		}
		Component name = Component.literal(Texts.of(selected.preset.name()));
		ScreenCompat.setScreen(new ConfirmScreen(
				quit -> {
					if (quit) Minecraft.getInstance().stop();
					else leave();
				},
				Texts.tr("restart.title", "Restart Required"),
				Texts.tr("restart.message", "The preset \"%s\" is applied the next time you start the game. Your keybinds and other personal settings are kept.", name),
				Texts.tr("restart.quit", "Quit Game"),
				Texts.tr("restart.later", "Later")));
	}

	private static int rowWidth(int screenWidth) {
		return Math.min(340, screenWidth - 40);
	}

	/** Same height for every row (variable row heights only exist since 1.21.9): name + the longest description. */
	private static int rowHeight(Definitions defs, Font font, int rowWidth) {
		int lines = 0;
		for (Definitions.Preset preset : defs.presets().values()) {
			String desc = Texts.of(preset.description());
			if (desc != null && !desc.isEmpty()) lines = Math.max(lines, font.split(Component.literal(desc), rowWidth - 12).size());
		}
		return 8 + 11 + lines * 10 + 4;
	}

	final class PresetList extends ObjectSelectionList<PresetList.Entry> {
		PresetList(Minecraft minecraft, Definitions defs) {
			//? if >=1.20.3 {
			super(minecraft, PresetScreen.this.width, PresetScreen.this.height - HEADER_HEIGHT - FOOTER_HEIGHT, HEADER_HEIGHT,
					rowHeight(defs, PresetScreen.this.font, rowWidth(PresetScreen.this.width)));
			//?} else {
			/*super(minecraft, PresetScreen.this.width, PresetScreen.this.height, HEADER_HEIGHT, PresetScreen.this.height - FOOTER_HEIGHT,
					rowHeight(defs, PresetScreen.this.font, rowWidth(PresetScreen.this.width)));
			*///?}
			int wrap = rowWidth(PresetScreen.this.width) - 12;
			for (Definitions.Preset preset : defs.presets().values()) {
				Entry entry = new Entry(preset, wrap);
				addEntry(entry);
				if (preset.id().equals(selectedId)) super.setSelected(entry);
			}
			if (getSelected() != null) centerScrollOn(getSelected());
		}

		@Override
		public int getRowWidth() {
			return rowWidth(PresetScreen.this.width);
		}

		@Override
		public void setSelected(Entry entry) {
			super.setSelected(entry);
			if (entry != null) selectedId = entry.preset.id();
			updateButtons();
		}

		final class Entry extends ObjectSelectionList.Entry<Entry> {
			final Definitions.Preset preset;
			private final Component name;
			private final List<FormattedCharSequence> description;
			//? if <1.21.9 {
			/*private long lastClick;
			*///?}

			Entry(Definitions.Preset preset, int wrap) {
				this.preset = preset;
				MutableComponent n = Component.literal(Texts.of(preset.name())).withStyle(ChatFormatting.BOLD);
				if (preset.id().equals(engine.state().applied)) n.append(Component.literal(" ").append(Texts.tr("current", "(active)")).withStyle(s -> s.withBold(false).withColor(ChatFormatting.GREEN)));
				if (preset.id().equals(engine.state().pending)) n.append(Component.literal(" ").append(Texts.tr("pending", "(after restart)")).withStyle(s -> s.withBold(false).withColor(ChatFormatting.YELLOW)));
				this.name = n;
				String desc = Texts.of(preset.description());
				this.description = desc == null || desc.isEmpty() ? new ArrayList<>() : PresetScreen.this.font.split(Component.literal(desc), wrap);
			}

			//? if >=26.1 {
			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				int x = getContentX() + 4;
				int y = getContentY() + 2;
				graphics.text(PresetScreen.this.font, name, x, y, NAME_COLOR);
				y += 12;
				for (FormattedCharSequence line : description) {
					graphics.text(PresetScreen.this.font, line, x, y, DESCRIPTION_COLOR);
					y += 10;
				}
			}
			//?} else if >=1.21.9 {
			/*@Override
			public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float a) {
				int x = getContentX() + 4;
				int y = getContentY() + 2;
				graphics.drawString(PresetScreen.this.font, name, x, y, NAME_COLOR);
				y += 12;
				for (FormattedCharSequence line : description) {
					graphics.drawString(PresetScreen.this.font, line, x, y, DESCRIPTION_COLOR);
					y += 10;
				}
			}
			*///?} else {
			/*@Override
			public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float a) {
				int x = left + 4;
				int y = top + 2;
				graphics.drawString(PresetScreen.this.font, name, x, y, NAME_COLOR);
				y += 12;
				for (FormattedCharSequence line : description) {
					graphics.drawString(PresetScreen.this.font, line, x, y, DESCRIPTION_COLOR);
					y += 10;
				}
			}
			*///?}

			//? if >=1.21.9 {
			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				PresetList.this.setSelected(this);
				if (doubleClick) useSelected();
				return super.mouseClicked(event, doubleClick);
			}

			@Override
			public boolean keyPressed(KeyEvent event) {
				if (event.isSelection()) {
					PresetList.this.setSelected(this);
					useSelected();
					return true;
				}
				return super.keyPressed(event);
			}
			//?} else {
			/*@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				if (button != 0) return false;
				PresetList.this.setSelected(this);
				long now = Util.getMillis();
				if (now - lastClick < 250L) useSelected();
				lastClick = now;
				return true;
			}

			@Override
			public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
				if (CommonInputs.selected(keyCode)) {
					PresetList.this.setSelected(this);
					useSelected();
					return true;
				}
				return super.keyPressed(keyCode, scanCode, modifiers);
			}
			*///?}

			@Override
			public Component getNarration() {
				return Component.translatable("narrator.select", Component.literal(Texts.of(preset.name()) + ". " + Texts.of(preset.description())));
			}
		}
	}
}
