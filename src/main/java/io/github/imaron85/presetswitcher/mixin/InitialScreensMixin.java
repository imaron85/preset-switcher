package io.github.imaron85.presetswitcher.mixin;

import io.github.imaron85.presetswitcher.client.ClientEngine;
import io.github.imaron85.presetswitcher.client.PresetScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

//? if >=26.2 {
import net.minecraft.client.gui.Gui;
//?} else {
/*import net.minecraft.client.Minecraft;
*///?}
//? if >=1.21.6 {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}
//? if <1.20.2 {
/*import com.mojang.realmsclient.client.RealmsClient;
import net.minecraft.client.main.GameConfig;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
*///?}

import java.util.List;
import java.util.function.Function;

/**
 * Shows the first-launch chooser before the title screen. Since 1.20.2 vanilla builds a chain of start-up screens
 * (accessibility onboarding, ban notices) that ends with the title screen or the quick-play connection; the chooser is
 * appended to that chain, so its buttons continue to wherever vanilla would have gone. On 1.20.1 the start-up step is
 * deferred until the chooser closes.
 */
//? if >=26.2 {
@Mixin(Gui.class)
//?} else {
/*@Mixin(Minecraft.class)
*///?}
public abstract class InitialScreensMixin {
	//? if >=1.21.6 {
	@Inject(method = "addInitialScreens", at = @At("RETURN"))
	private void presetswitcher$addChooser(List<Function<Runnable, Screen>> screens, CallbackInfoReturnable<Boolean> cir) {
		if (ClientEngine.get().shouldShowChooser()) screens.add(PresetScreen::firstLaunch);
	}
	//?} else if >=1.20.2 {
	/*@Inject(method = "addInitialScreens", at = @At("RETURN"))
	private void presetswitcher$addChooser(List<Function<Runnable, Screen>> screens, CallbackInfo ci) {
		if (ClientEngine.get().shouldShowChooser()) screens.add(PresetScreen::firstLaunch);
	}
	*///?} else {
	/*@Unique
	private boolean presetswitcher$chooserDone;

	@Shadow
	private void setInitialScreen(RealmsClient realmsClient, ReloadInstance reloadInstance, GameConfig.QuickPlayData quickPlayData) {
		throw new AssertionError();
	}

	@Inject(method = "setInitialScreen", at = @At("HEAD"), cancellable = true)
	private void presetswitcher$chooser(RealmsClient realmsClient, ReloadInstance reloadInstance, GameConfig.QuickPlayData quickPlayData, CallbackInfo ci) {
		if (presetswitcher$chooserDone || !ClientEngine.get().shouldShowChooser()) return;
		presetswitcher$chooserDone = true;
		ci.cancel();
		((Minecraft) (Object) this).setScreen(PresetScreen.firstLaunch(() -> setInitialScreen(realmsClient, reloadInstance, quickPlayData)));
	}
	*///?}
}
