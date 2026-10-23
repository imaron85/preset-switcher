package io.github.imaron85.presetswitcher.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.imaron85.presetswitcher.client.PresetScreen;

/** Mod Menu's config button opens the preset list. Only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return PresetScreen::open;
	}
}
