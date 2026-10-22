package io.github.imaron85.presetswitcher.client;

import io.github.imaron85.presetswitcher.core.Definitions;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * UI strings. English fallbacks are built in because mod language files are only loaded when Fabric API
 * (its resource loader) is installed, and this mod does not require it.
 */
final class Texts {
	private Texts() {
	}

	static Component tr(String key, String english, Object... args) {
		return Component.translatableWithFallback("presetswitcher." + key, english, args);
	}

	static String language() {
		Minecraft mc = Minecraft.getInstance();
		return mc.options == null ? "en_us" : mc.options.languageCode;
	}

	static String of(Definitions.Text text) {
		return text == null ? null : text.get(language());
	}

	static String presetName(Definitions defs, String id) {
		if (defs == null || id == null) return String.valueOf(id);
		Definitions.Preset p = defs.presets().get(id);
		return p == null ? id : of(p.name());
	}
}
