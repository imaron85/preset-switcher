package io.github.imaron85.presetswitcher.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Version differences of screen handling. */
public final class ScreenCompat {
	private ScreenCompat() {
	}

	public static void setScreen(Screen screen) {
		//? if >=26.2 {
		Minecraft.getInstance().gui.setScreen(screen);
		//?} else {
		/*Minecraft.getInstance().setScreen(screen);
		*///?}
	}
}
