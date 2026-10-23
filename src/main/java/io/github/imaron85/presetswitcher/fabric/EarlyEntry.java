package io.github.imaron85.presetswitcher.fabric;

import io.github.imaron85.presetswitcher.core.Json;
import io.github.imaron85.presetswitcher.core.Presets;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Applies presets before any other mod reads its configuration.
 *
 * <p>Fabric Loader instantiates every declared language adapter right after mod discovery, before mixin
 * configuration plugins run and before any {@code preLaunch} entrypoint. This adapter is never used to create
 * entrypoints; constructing it is the hook. The {@code preLaunch} entrypoint is a fallback for loaders that create
 * adapters lazily (Quilt); {@link Presets#bootstrap} runs only once.
 */
public final class EarlyEntry implements LanguageAdapter, PreLaunchEntrypoint {
	public EarlyEntry() {
		try {
			if (FabricLoader.getInstance().getEnvironmentType() != net.fabricmc.api.EnvType.CLIENT) return;
			FabricLoader loader = FabricLoader.getInstance();
			Presets.bootstrap(loader.getGameDir(), dataVersion(loader), "Fabric language adapter");
		} catch (Throwable t) {
			Presets.LOGGER.error("Preset Switcher failed to start; presets are not applied", t);
		}
	}

	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		throw new LanguageAdapterException("the presetswitcher language adapter does not create entrypoints");
	}

	@Override
	public void onPreLaunch() {
		// Construction already ran the bootstrap.
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
				return io.github.imaron85.presetswitcher.core.DataVersions.of(mc.get().getMetadata().getVersion().getFriendlyString());
			}
		} catch (Exception e) {
			Presets.LOGGER.warn("could not read the game's data version: {}", e.toString());
		}
		return -1;
	}
}
