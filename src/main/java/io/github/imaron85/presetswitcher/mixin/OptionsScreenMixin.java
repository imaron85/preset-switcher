package io.github.imaron85.presetswitcher.mixin;

import io.github.imaron85.presetswitcher.client.OptionsButton;
import io.github.imaron85.presetswitcher.core.Presets;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21 {
import net.minecraft.client.gui.screens.options.OptionsScreen;
//?} else {
/*import net.minecraft.client.gui.screens.OptionsScreen;
*///?}
//? if <1.20.5 {
/*import org.objectweb.asm.Opcodes;
*///?}

/** Adds a full-row "Presets..." button below vanilla's option buttons (above "Done"). */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
	@Unique
	private GridLayout.RowHelper presetswitcher$rows;

	protected OptionsScreenMixin(Component title) {
		super(title);
	}

	@ModifyVariable(method = "init", at = @At("STORE"))
	private GridLayout.RowHelper presetswitcher$captureRows(GridLayout.RowHelper rows) {
		presetswitcher$rows = rows;
		return rows;
	}

	//? if >=1.20.5 {
	@Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addToContents(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
	//?} else {
	/*@Inject(method = "init", at = @At(value = "FIELD", target = "Lnet/minecraft/network/chat/CommonComponents;GUI_DONE:Lnet/minecraft/network/chat/Component;", opcode = Opcodes.GETSTATIC))
	*///?}
	private void presetswitcher$addButton(CallbackInfo ci) {
		if (presetswitcher$rows != null && OptionsButton.visible()) {
			presetswitcher$rows.addChild(OptionsButton.create(this), 2);
			Presets.log().info("added the Presets... button to the Options screen");
		}
	}
}
