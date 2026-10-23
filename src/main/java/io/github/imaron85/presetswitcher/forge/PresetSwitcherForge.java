package io.github.imaron85.presetswitcher.forge;

import io.github.imaron85.presetswitcher.core.Presets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

/** Forge entry point. Client only in effect: on a dedicated server it does nothing and is not required by clients. */
@Mod(Presets.MOD_ID)
public final class PresetSwitcherForge {
	public PresetSwitcherForge() {
		ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
				() -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (remote, server) -> true));
		if (FMLEnvironment.dist == Dist.CLIENT) ForgeClient.init(FMLPaths.GAMEDIR.get());
	}
}
