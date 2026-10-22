package io.github.imaron85.presetswitcher;

import io.github.imaron85.presetswitcher.core.Applier;
import io.github.imaron85.presetswitcher.core.Json;
import io.github.imaron85.presetswitcher.core.PresetEngine;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Holds the engine for the running game. {@link #bootstrap()} is called as early as Fabric allows
 * (see {@link EarlyEntry}); it must only use JDK, Fabric Loader API and SLF4J classes because Minecraft's
 * other libraries are not loadable yet.
 */
public final class PresetSwitcher {
	public static final String MOD_ID = "presetswitcher";
	public static final Logger LOGGER = LoggerFactory.getLogger("PresetSwitcher");

	private static PresetEngine engine;

	private PresetSwitcher() {
	}

	public static synchronized void bootstrap() {
		if (engine != null) return;
		FabricLoader loader = FabricLoader.getInstance();
		engine = new PresetEngine(loader.getGameDir(), dataVersion(loader), new Applier.Log() {
			@Override
			public void info(String message) {
				LOGGER.info("[PresetSwitcher] {}", message);
			}

			@Override
			public void warn(String message) {
				LOGGER.warn("[PresetSwitcher] {}", message);
			}
		});
		engine.startup();
	}

	public static PresetEngine engine() {
		bootstrap();
		return engine;
	}

	/** The game's data version from version.json, written into a newly created options.txt (-1 if unknown). */
	private static int dataVersion(FabricLoader loader) {
		try {
			Optional<ModContainer> mc = loader.getModContainer("minecraft");
			if (mc.isPresent()) {
				for (Path root : mc.get().getRootPaths()) {
					Path versionJson = root.resolve("version.json");
					if (Files.isRegularFile(versionJson) && Json.parse(Files.readString(versionJson)) instanceof Map<?, ?> m
							&& m.get("world_version") instanceof Json.Num n) {
						return Integer.parseInt(n.literal());
					}
				}
			}
		} catch (Exception e) {
			LOGGER.warn("could not read the game's data version: {}", e.toString());
		}
		return -1;
	}
}
