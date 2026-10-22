package io.github.imaron85.presetswitcher.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTest {
	private static final Applier.Log LOG = new Applier.Log() {
		@Override
		public void info(String message) {
			System.out.println("[info] " + message);
		}

		@Override
		public void warn(String message) {
			System.out.println("[warn] " + message);
		}
	};

	private static final String DEFS = """
			{
			  defaultPreset: "default",
			  base: [ { type: "copy", from: "base/options.txt", to: "options.txt", when: "missing" } ],
			  presets: [
			    { id: "low", name: "Low", operations: [
			      { type: "merge", file: "options.txt", values: { renderDistance: 4, particles: 2 } },
			      { type: "merge", file: "config/mod.json", values: { "quality.clouds": false } },
			      { type: "resourcePacks", disable: ["file/Fancy.zip"] },
			    ]},
			    { id: "default", name: "Default", operations: [
			      { type: "merge", file: "options.txt", values: { renderDistance: 12, particles: 0 } },
			      { type: "merge", file: "config/mod.json", values: { quality: { clouds: true } } },
			      { type: "resourcePacks", enable: ["file/Fancy.zip"] },
			    ]},
			  ],
			}
			""";

	private static PresetEngine engine(Path game) {
		PresetEngine e = new PresetEngine(game, 5023, LOG);
		e.startup();
		return e;
	}

	private static void setup(Path game, String defs) throws IOException {
		Path dir = game.resolve(PresetEngine.DEFINITIONS_DIR);
		Files.createDirectories(dir.resolve("base"));
		Files.writeString(dir.resolve("presets.json5"), defs);
		Files.writeString(dir.resolve("base/options.txt"), "version:5023\nrenderDistance:8\nkey_key.jump:key.keyboard.space\nresourcePacks:[\"vanilla\"]\n");
	}

	private static String options(Path game) throws IOException {
		return Files.readString(game.resolve("options.txt"));
	}

	@Test
	void fullLifecycle(@TempDir Path game) throws IOException {
		setup(game, DEFS);

		// 1. fresh install: base defaults copied, default preset applied
		PresetEngine e = engine(game);
		assertEquals("default", e.state().applied);
		assertTrue(e.shouldShowChooser());
		assertEquals("version:5023\nrenderDistance:12\nkey_key.jump:key.keyboard.space\nresourcePacks:[\"vanilla\",\"file/Fancy.zip\"]\nparticles:0\n", options(game));
		assertTrue(Files.readString(game.resolve("config/mod.json")).contains("\"clouds\": true"));

		// player rebinds jump and changes a non-preset option
		Files.writeString(game.resolve("options.txt"), options(game).replace("key.keyboard.space", "key.keyboard.j") + "fov:0.5\n");

		// 2. choose "low": applied at the next start, keybind and fov survive
		assertTrue(e.choose("low"));
		e = engine(game);
		assertEquals("low", e.state().applied);
		assertFalse(e.restartPending());
		assertFalse(e.shouldShowChooser());
		String opts = options(game);
		assertTrue(opts.contains("renderDistance:4\n"), opts);
		assertTrue(opts.contains("particles:2\n"), opts);
		assertTrue(opts.contains("key_key.jump:key.keyboard.j\n"), opts);
		assertTrue(opts.contains("fov:0.5\n"), opts);
		assertTrue(opts.contains("resourcePacks:[\"vanilla\"]\n"), opts);
		assertTrue(Files.readString(game.resolve("config/mod.json")).contains("\"clouds\": false"));

		// 3. unchanged definitions: nothing is touched (player tweak survives)
		Files.writeString(game.resolve("options.txt"), opts.replace("renderDistance:4", "renderDistance:6"));
		e = engine(game);
		assertTrue(options(game).contains("renderDistance:6\n"));

		// 4. pack update changes the definitions: the chosen preset's declared keys are re-applied, nothing else
		setup(game, DEFS.replace("renderDistance: 4, particles: 2", "renderDistance: 5, particles: 2, simulationDistance: 5"));
		e = engine(game);
		assertEquals("low", e.state().applied);
		opts = options(game);
		assertTrue(opts.contains("renderDistance:5\n"), opts);
		assertTrue(opts.contains("simulationDistance:5\n"), opts);
		assertTrue(opts.contains("key_key.jump:key.keyboard.j\n"), opts);
		assertTrue(opts.contains("fov:0.5\n"), opts);
	}

	@Test
	void deferredStepIsRetried(@TempDir Path game) throws IOException {
		setup(game, """
				{ presets: [ { id: "only", operations: [
				  { type: "merge", file: "config/late.toml", createIfMissing: false, values: { "general.x": 1 } },
				  { type: "merge", file: "shaderpacks/*.txt", format: "properties", values: { PROFILE: "HIGH" } },
				]}]}
				""");
		PresetEngine e = engine(game);
		assertEquals(2, e.state().deferred.size());
		assertFalse(Files.exists(game.resolve("config/late.toml")));

		Files.writeString(game.resolve("config/late.toml"), "[general]\nx = 0\ny = 2\n");
		Files.createDirectories(game.resolve("shaderpacks"));
		Files.writeString(game.resolve("shaderpacks/Pack.zip.txt"), "PROFILE=LOW\n");
		e = engine(game);
		assertTrue(e.state().deferred.isEmpty());
		assertEquals("[general]\nx = 1\ny = 2\n", Files.readString(game.resolve("config/late.toml")));
		assertEquals("PROFILE=HIGH\n", Files.readString(game.resolve("shaderpacks/Pack.zip.txt")));
	}

	@Test
	void invalidDefinitionsAreReportedNotThrown(@TempDir Path game) throws IOException {
		setup(game, "{ presets: [ { id: 'a', operations: [ { type: 'nope' } ] } ] }");
		PresetEngine e = engine(game);
		assertFalse(e.hasDefinitions());
		assertTrue(e.loadError().contains("unknown type"), e.loadError());
	}

	@Test
	void firstMatchDirective(@TempDir Path game) throws IOException {
		setup(game, """
				{ presets: [ { id: "only", operations: [
				  { type: "merge", file: "config/iris.properties", values: { shaderPack: { $firstMatch: "shaderpacks/Complementary*" }, enableShaders: true } },
				  { type: "resourcePacks", enable: [ { $firstMatch: "resourcepacks/Fresh*", prefix: "file/" } ] },
				]}]}
				""");
		Files.createDirectories(game.resolve("shaderpacks"));
		Files.writeString(game.resolve("shaderpacks/ComplementaryUnbound_r5.6 + EuphoriaPatches_1.7.zip"), "");
		Files.createDirectories(game.resolve("resourcepacks"));
		Files.writeString(game.resolve("resourcepacks/FreshAnimations_v1.10.zip"), "");
		engine(game);
		assertEquals("shaderPack=ComplementaryUnbound_r5.6 + EuphoriaPatches_1.7.zip\nenableShaders=true\n", Files.readString(game.resolve("config/iris.properties")));
		assertEquals("version:5023\nresourcePacks:[\"file/FreshAnimations_v1.10.zip\"]\n", options(game));
	}

	@Test
	void enableAfterRestoresOrder(@TempDir Path game) throws IOException {
		setup(game, """
				{ defaultPreset: "on", presets: [
				  { id: "off", operations: [ { type: "resourcePacks", disable: ["file/B.zip"] } ] },
				  { id: "on", operations: [ { type: "resourcePacks", enable: [ { pack: "file/B.zip", after: "file/A.zip" }, { pack: "file/D.zip", after: "file/Missing.zip" } ] } ] },
				]}
				""");
		Files.writeString(game.resolve("options.txt"), "version:5023\nresourcePacks:[\"vanilla\",\"file/A.zip\",\"file/C.zip\"]\n");
		engine(game);
		assertEquals("version:5023\nresourcePacks:[\"vanilla\",\"file/A.zip\",\"file/B.zip\",\"file/C.zip\",\"file/D.zip\"]\n", options(game));
	}
}
