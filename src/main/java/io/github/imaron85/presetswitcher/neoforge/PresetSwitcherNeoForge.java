package io.github.imaron85.presetswitcher.neoforge;

import io.github.imaron85.presetswitcher.client.ClientEngine;
import io.github.imaron85.presetswitcher.client.PresetScreen;
import io.github.imaron85.presetswitcher.core.Presets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** NeoForge entry point (client only). Presets were already applied by the early hook; this adds the config screen. */
@Mod(value = Presets.MOD_ID, dist = Dist.CLIENT)
public final class PresetSwitcherNeoForge {
	public PresetSwitcherNeoForge(ModContainer container) {
		ClientEngine.bootstrapFallback(FMLPaths.GAMEDIR.get(), "NeoForge mod constructor");
		container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> PresetScreen.open(parent));
	}
}
