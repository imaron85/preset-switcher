package io.github.imaron85.presetswitcher.mixin;

import io.github.imaron85.presetswitcher.PresetSwitcher;
import io.github.imaron85.presetswitcher.client.PresetScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Function;

/** Adds the first-launch chooser to vanilla's chain of startup screens (after accessibility onboarding, before the title screen). */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@Inject(method = "addInitialScreens", at = @At("RETURN"))
	private void presetswitcher$addChooser(List<Function<Runnable, Screen>> screens, CallbackInfoReturnable<Boolean> cir) {
		if (PresetSwitcher.engine().shouldShowChooser()) {
			screens.add(PresetScreen::firstLaunch);
		}
	}
}
