package io.github.imaron85.presetswitcher.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Holds the engine of the running game. {@link #bootstrap} is called by the loader-specific early hook (Fabric language
 * adapter, NeoForge/Forge early services); it only uses the JDK and SLF4J because Minecraft and most libraries are not
 * loadable yet at that point.
 */
public final class Presets {
	public static final String MOD_ID = "presetswitcher";
	public static final Logger LOGGER = LoggerFactory.getLogger("PresetSwitcher");
	private static final Applier.Log LOG = new Applier.Log() {
		@Override
		public void info(String message) {
			LOGGER.info("[PresetSwitcher] {}", message);
		}

		@Override
		public void warn(String message) {
			LOGGER.warn("[PresetSwitcher] {}", message);
		}
	};

	private static PresetEngine engine;
	private static String hook;

	private Presets() {
	}

	/**
	 * Applies a pending / first-launch / updated preset. Runs once per class loader; later calls are ignored.
	 *
	 * @param hookName which early hook ran it, logged so the timing can be checked in latest.log
	 */
	public static synchronized void bootstrap(Path gameDir, int dataVersion, String hookName) {
		if (engine != null) return;
		hook = hookName;
		LOG.info("starting from " + hookName + " (game directory " + gameDir.toAbsolutePath().normalize() + ", data version " + dataVersion + ")");
		engine = new PresetEngine(gameDir, dataVersion, LOG);
		engine.startup();
	}

	public static synchronized boolean isBootstrapped() {
		return engine != null;
	}

	/** The engine; {@code null} before {@link #bootstrap}. */
	public static synchronized PresetEngine engine() {
		return engine;
	}

	public static synchronized String hook() {
		return hook;
	}

	public static Applier.Log log() {
		return LOG;
	}
}
