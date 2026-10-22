package io.github.imaron85.presetswitcher.client;

import io.github.imaron85.presetswitcher.PresetSwitcher;
import io.github.imaron85.presetswitcher.core.Definitions;
import io.github.imaron85.presetswitcher.core.PresetEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/** Lists the pack's presets with their descriptions; used as first-launch chooser, from Options and from Mod Menu. */
public class PresetScreen extends Screen {
	private static final int HEADER_HEIGHT = 52;
	private static final int FOOTER_HEIGHT = 33;
	private static final int DESCRIPTION_COLOR = 0xFFA0A0A0;

	private final @Nullable Screen parent;
	private final @Nullable Runnable firstLaunchNext;
	private final PresetEngine engine = PresetSwitcher.engine();
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);
	private @Nullable PresetList list;
	private @Nullable Button useButton;

	private PresetScreen(@Nullable Screen parent, @Nullable Runnable firstLaunchNext) {
		super(title(PresetSwitcher.engine()));
		this.parent = parent;
		this.firstLaunchNext = firstLaunchNext;
	}

	/** Opened from the Options screen or Mod Menu; returns to {@code parent}. */
	public static PresetScreen open(@Nullable Screen parent) {
		return new PresetScreen(parent, null);
	}

	/** Shown once on first launch, between vanilla's onboarding screens and the title screen. */
	public static PresetScreen firstLaunch(Runnable next) {
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
		LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(6));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		header.addChild(new MultiLineTextWidget(subtitle(defs), this.font).setMaxWidth(Math.min(this.width - 40, 400)).setMaxRows(2).setCentered(true));

		if (defs != null) {
			list = layout.addToContents(new PresetList(this.minecraft, defs));
		} else {
			Component message = engine.loadError() != null
					? Texts.tr("error", "The preset definitions could not be loaded:").copy().append("\n").append(Component.literal(engine.loadError()).withStyle(ChatFormatting.RED))
					: Texts.tr("none", "This modpack does not define any presets (config/presetswitcher/presets.json5).");
			layout.addToContents(new MultiLineTextWidget(message, this.font).setMaxWidth(Math.min(this.width - 40, 400)).setCentered(true));
		}

		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		if (defs != null) {
			useButton = footer.addChild(Button.builder(Texts.tr("use", "Use Preset"), b -> useSelected()).build());
			Component back = firstLaunchNext != null
					? Texts.tr("keep", "Keep %s", Texts.presetName(defs, engine.state().applied))
					: CommonComponents.GUI_CANCEL;
			footer.addChild(Button.builder(back, b -> onClose()).build());
		} else {
			footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).build());
		}
		layout.visitWidgets(this::addRenderableWidget);
		updateButtons();
		repositionElements();
	}

	private Component subtitle(@Nullable Definitions defs) {
		String custom = defs == null ? null : Texts.of(defs.subtitle());
		if (custom != null) return Component.literal(custom);
		if (firstLaunchNext != null) {
			return Texts.tr("subtitle.first", "Choose how the game should look and perform. You can change this later in Options > Presets...");
		}
		return Texts.tr("subtitle", "The chosen preset is applied the next time the game starts. Keybinds and other personal settings are kept.");
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
		if (list != null) list.updateSize(this.width, layout);
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
				PresetSwitcher.LOGGER.warn("could not save the preset state", e);
			}
		}
		leave();
	}

	private void leave() {
		if (firstLaunchNext != null) firstLaunchNext.run();
		else this.minecraft.gui.setScreen(parent);
	}

	private void useSelected() {
		PresetList.Entry selected = list == null ? null : list.getSelected();
		if (selected == null) return;
		boolean restart;
		try {
			restart = engine.choose(selected.preset.id());
		} catch (IOException e) {
			PresetSwitcher.LOGGER.error("could not save the preset choice", e);
			return;
		}
		if (!restart) {
			leave();
			return;
		}
		Component name = Component.literal(Texts.of(selected.preset.name()));
		this.minecraft.gui.setScreen(new ConfirmScreen(
				quit -> {
					if (quit) this.minecraft.stop();
					else leave();
				},
				Texts.tr("restart.title", "Restart Required"),
				Texts.tr("restart.message", "The preset \"%s\" is applied the next time you start the game. Your keybinds and other personal settings are kept.", name),
				Texts.tr("restart.quit", "Quit Game"),
				Texts.tr("restart.later", "Later")));
	}

	final class PresetList extends ObjectSelectionList<PresetList.Entry> {
		PresetList(Minecraft minecraft, Definitions defs) {
			super(minecraft, PresetScreen.this.width, PresetScreen.this.layout.getContentHeight(), PresetScreen.this.layout.getHeaderHeight(), 36);
			String current = engine.effectivePreset();
			int wrap = getRowWidth() - 12;
			for (Definitions.Preset preset : defs.presets().values()) {
				Entry entry = new Entry(preset, wrap);
				addEntry(entry, entry.height());
				if (preset.id().equals(current)) setSelected(entry);
			}
			if (getSelected() != null) centerScrollOn(getSelected());
		}

		@Override
		public int getRowWidth() {
			return Math.min(340, PresetScreen.this.width - 40);
		}

		@Override
		public void setSelected(@Nullable Entry entry) {
			super.setSelected(entry);
			updateButtons();
		}

		final class Entry extends ObjectSelectionList.Entry<Entry> {
			final Definitions.Preset preset;
			private final Component name;
			private final List<FormattedCharSequence> description;

			Entry(Definitions.Preset preset, int wrap) {
				this.preset = preset;
				MutableComponent n = Component.literal(Texts.of(preset.name())).withStyle(ChatFormatting.BOLD);
				if (preset.id().equals(engine.state().applied)) n.append(Component.literal(" ").append(Texts.tr("current", "(active)")).withStyle(s -> s.withBold(false).withColor(ChatFormatting.GREEN)));
				if (preset.id().equals(engine.state().pending)) n.append(Component.literal(" ").append(Texts.tr("pending", "(after restart)")).withStyle(s -> s.withBold(false).withColor(ChatFormatting.YELLOW)));
				this.name = n;
				String desc = Texts.of(preset.description());
				this.description = desc == null || desc.isEmpty() ? List.of() : PresetScreen.this.font.split(Component.literal(desc), wrap);
			}

			int height() {
				return 8 + 11 + description.size() * 10 + 4;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				int x = getContentX() + 4;
				int y = getContentY() + 2;
				graphics.text(PresetScreen.this.font, name, x, y, 0xFFFFFFFF);
				y += 12;
				for (FormattedCharSequence line : description) {
					graphics.text(PresetScreen.this.font, line, x, y, DESCRIPTION_COLOR);
					y += 10;
				}
			}

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

			@Override
			public Component getNarration() {
				return Component.translatable("narrator.select", Component.literal(Texts.of(preset.name()) + ". " + Texts.of(preset.description())));
			}
		}
	}
}
