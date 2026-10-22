package io.github.imaron85.presetswitcher;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Applies presets before any other mod reads its configuration.
 *
 * <p>Fabric Loader instantiates every declared language adapter right after mod discovery, before mixin
 * configuration plugins run and before any {@code preLaunch} entrypoint. This adapter is never used to create
 * entrypoints; constructing it is the hook. The {@code preLaunch} entrypoint is a fallback for loaders that
 * create adapters lazily; {@link PresetSwitcher#bootstrap()} runs only once.
 */
public final class EarlyEntry implements LanguageAdapter, PreLaunchEntrypoint {
	public EarlyEntry() {
		try {
			PresetSwitcher.bootstrap();
		} catch (Throwable t) {
			PresetSwitcher.LOGGER.error("Preset Switcher failed to start; presets are not applied", t);
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
}
