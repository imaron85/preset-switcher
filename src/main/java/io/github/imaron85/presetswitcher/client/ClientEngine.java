package io.github.imaron85.presetswitcher.client;

import io.github.imaron85.presetswitcher.core.PresetEngine;
import io.github.imaron85.presetswitcher.core.Presets;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

/** The engine for client code. Normally started by the loader's early hook; started late here only if that did not run. */
public final class ClientEngine {
	private ClientEngine() {
	}

	public static PresetEngine get() {
		if (!Presets.isBootstrapped()) {
			Presets.log().warn("the early hook did not run; applying presets now (mods may already have read their configs)");
			Presets.bootstrap(Minecraft.getInstance().gameDirectory.toPath(), dataVersion(), "client fallback");
		}
		return Presets.engine();
	}

	/** Starts the engine from a mod constructor when no early hook ran (degraded: mods may already have read configs). */
	public static void bootstrapFallback(java.nio.file.Path gameDir, String hook) {
		if (Presets.isBootstrapped()) return;
		Presets.log().warn("the early hook did not run; applying presets from the " + hook);
		Presets.bootstrap(gameDir, dataVersion(), hook);
	}

	static int dataVersion() {
		//? if >=1.21.6 {
		return SharedConstants.getCurrentVersion().dataVersion().version();
		//?} else {
		/*return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
		*///?}
	}
}
