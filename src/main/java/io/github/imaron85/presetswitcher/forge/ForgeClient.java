package io.github.imaron85.presetswitcher.forge;

import io.github.imaron85.presetswitcher.client.ClientEngine;
import io.github.imaron85.presetswitcher.client.PresetScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

import java.nio.file.Path;

/** Client-only part of the Forge entry point (kept separate so the server never loads client classes). */
final class ForgeClient {
	private ForgeClient() {
	}

	static void init(Path gameDir) {
		ClientEngine.bootstrapFallback(gameDir, "Forge mod constructor");
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> PresetScreen.open(parent)));
	}
}
