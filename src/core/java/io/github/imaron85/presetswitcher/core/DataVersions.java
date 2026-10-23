package io.github.imaron85.presetswitcher.core;

import java.util.Map;

/**
 * Data versions of the Minecraft releases this mod supports. Used where the early hook cannot read the game's
 * {@code version.json} (NeoForge, Forge); the value is written as {@code version:} into an options.txt the mod creates,
 * so the game does not run its options data fixers from version 0.
 */
public final class DataVersions {
	private static final Map<String, Integer> RELEASES = Map.ofEntries(
			Map.entry("1.20", 3463), Map.entry("1.20.1", 3465), Map.entry("1.20.2", 3578), Map.entry("1.20.3", 3698),
			Map.entry("1.20.4", 3700), Map.entry("1.20.5", 3837), Map.entry("1.20.6", 3839),
			Map.entry("1.21", 3953), Map.entry("1.21.1", 3955), Map.entry("1.21.2", 4080), Map.entry("1.21.3", 4082),
			Map.entry("1.21.4", 4189), Map.entry("1.21.5", 4325), Map.entry("1.21.6", 4435), Map.entry("1.21.7", 4438),
			Map.entry("1.21.8", 4440), Map.entry("1.21.9", 4554), Map.entry("1.21.10", 4556), Map.entry("1.21.11", 4671),
			Map.entry("26.1", 4786), Map.entry("26.1.1", 4788), Map.entry("26.1.2", 4790), Map.entry("26.2", 4903),
			Map.entry("26.3", 5023));

	private DataVersions() {
	}

	/** @return the data version of a release, or -1 if unknown */
	public static int of(String minecraftVersion) {
		if (minecraftVersion == null) return -1;
		return RELEASES.getOrDefault(minecraftVersion.trim(), -1);
	}
}
