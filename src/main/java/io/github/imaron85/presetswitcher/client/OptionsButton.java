package io.github.imaron85.presetswitcher.client;

import io.github.imaron85.presetswitcher.PresetSwitcher;
import io.github.imaron85.presetswitcher.core.PresetEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The "Presets..." button added to vanilla's Options screen. */
public final class OptionsButton {
	private OptionsButton() {
	}

	/** Shown when the pack ships definitions (or broken ones, so the error is visible). */
	public static boolean visible() {
		PresetEngine engine = PresetSwitcher.engine();
		return engine.hasDefinitions() || engine.loadError() != null;
	}

	public static Button create(Screen parent) {
		PresetEngine engine = PresetSwitcher.engine();
		Button button = Button.builder(Texts.tr("button", "Presets..."), b -> Minecraft.getInstance().gui.setScreen(PresetScreen.open(parent))).build();
		if (engine.hasDefinitions()) {
			String name = Texts.presetName(engine.definitions(), engine.effectivePreset());
			Component tip = engine.restartPending()
					? Texts.tr("tooltip.pending", "Preset after restart: %s", name)
					: Texts.tr("tooltip", "Current preset: %s", name);
			button.setTooltip(Tooltip.create(tip));
		}
		return button;
	}
}
