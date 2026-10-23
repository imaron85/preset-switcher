package io.github.imaron85.presetswitcher.early.neoforge;

import io.github.imaron85.presetswitcher.core.DataVersions;
import io.github.imaron85.presetswitcher.core.Presets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageLoader;
import net.neoforged.neoforgespi.language.ModFileScanData;

/**
 * NeoForge early hook. FancyModLoader does not load jars that declare early services (locators, graphics
 * bootstrappers) as mods, so this class and the core live in a library jar nested in the mod jar (jar-in-jar).
 * FML creates the language loaders of library jars after mod discovery, before mixin configs are applied and before any
 * mod is constructed or any mod config is loaded. It never loads a mod.
 */
public final class LanguageLoaderHook implements IModLanguageLoader {
	public LanguageLoaderHook() {
		try {
			//? if >=1.21.9 {
			FMLLoader loader = FMLLoader.getCurrent();
			if (loader.getDist() != Dist.CLIENT) return;
			String mc = loader.getVersionInfo().mcVersion();
			//?} else {
			/*if (FMLLoader.getDist() != Dist.CLIENT) return;
			String mc = FMLLoader.versionInfo().mcVersion();
			*///?}
			Presets.bootstrap(FMLPaths.GAMEDIR.get(), DataVersions.of(mc), "NeoForge language loader");
		} catch (Throwable t) {
			Presets.LOGGER.error("Preset Switcher failed to start; presets are not applied", t);
		}
	}

	@Override
	public String name() {
		return "presetswitcher_early";
	}

	@Override
	public String version() {
		return "1";
	}

	@Override
	public ModContainer loadMod(IModInfo info, ModFileScanData modFileScanResults, ModuleLayer layer) {
		throw new IllegalStateException("the presetswitcher_early language loader does not load mods");
	}
}
