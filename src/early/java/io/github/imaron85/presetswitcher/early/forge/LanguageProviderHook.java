package io.github.imaron85.presetswitcher.early.forge;

import io.github.imaron85.presetswitcher.core.DataVersions;
import io.github.imaron85.presetswitcher.core.Presets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.ILifecycleEvent;
import net.minecraftforge.forgespi.language.IModLanguageProvider;
import net.minecraftforge.forgespi.language.ModFileScanData;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Forge 1.20.1 early hook. Jars with early services are excluded from mod loading, so this class lives in a language
 * provider jar nested in the mod jar (jar-in-jar). Forge instantiates language providers right after mod discovery,
 * before the game layer and mixins are set up and before any mod is constructed. It never loads a mod.
 */
public final class LanguageProviderHook implements IModLanguageProvider {
	public LanguageProviderHook() {
		try {
			if (FMLLoader.getDist() != Dist.CLIENT) return;
			Presets.bootstrap(FMLPaths.GAMEDIR.get(), DataVersions.of(FMLLoader.versionInfo().mcVersion()), "Forge language provider");
		} catch (Throwable t) {
			Presets.LOGGER.error("Preset Switcher failed to start; presets are not applied", t);
		}
	}

	@Override
	public String name() {
		return "presetswitcher_early";
	}

	@Override
	public Consumer<ModFileScanData> getFileVisitor() {
		return scanData -> {
		};
	}

	@Override
	public <R extends ILifecycleEvent<R>> void consumeLifecycleEvent(Supplier<R> consumeEvent) {
	}
}
