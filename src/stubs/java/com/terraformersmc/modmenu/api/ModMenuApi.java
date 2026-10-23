package com.terraformersmc.modmenu.api;

/** Compile-time stub of Mod Menu's API (compileOnly; the real interface is used at runtime). */
public interface ModMenuApi {
	default ConfigScreenFactory<?> getModConfigScreenFactory() {
		return null;
	}
}
