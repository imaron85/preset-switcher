package io.github.imaron85.presetswitcher.core;

import io.github.imaron85.presetswitcher.core.format.FileFormat.Assignment;
import io.github.imaron85.presetswitcher.core.format.JsonFormat;
import io.github.imaron85.presetswitcher.core.format.OptionsFormat;
import io.github.imaron85.presetswitcher.core.format.PropertiesFormat;
import io.github.imaron85.presetswitcher.core.format.TomlFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatsTest {
	private static Assignment a(Object value, String... path) {
		return new Assignment(List.of(path), value);
	}

	@Test
	void optionsMergeKeepsOtherKeys() {
		String in = "version:5023\nrenderDistance:12\nkey_key.attack:key.mouse.left\nresourcePacks:[\"vanilla\"]\n";
		String out = new OptionsFormat(5023).apply(in, List.of(a(new Json.Num("4"), "renderDistance"), a("0.5", "entityDistanceScaling")));
		assertEquals("version:5023\nrenderDistance:4\nkey_key.attack:key.mouse.left\nresourcePacks:[\"vanilla\"]\nentityDistanceScaling:0.5\n", out);
	}

	@Test
	void optionsKeepsVanillaQuoting() {
		String out = new OptionsFormat(5023).apply("graphicsPreset:\"fancy\"\nlang:en_us\n", List.of(a("custom", "graphicsPreset"), a("de_de", "lang")));
		assertEquals("graphicsPreset:\"custom\"\nlang:de_de\n", out);
	}

	@Test
	void unknownKeysAreRejected() {
		IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> Definitions.parse("{ presets: [ { id: 'a', operations: [ { type: 'merge', file: 'options.txt', valeus: { a: 1 } } ] } ] }"));
		assertTrue(e.getMessage().contains("unknown key 'valeus'"), e.getMessage());
	}

	@Test
	void optionsCreatesVersionLine() {
		assertEquals("version:5023\nrenderDistance:4\n", new OptionsFormat(5023).apply(null, List.of(a(new Json.Num("4"), "renderDistance"))));
	}

	@Test
	void optionsKeepsCrLf() {
		assertEquals("a:1\r\nb:3\r\n", new OptionsFormat(1).apply("a:1\r\nb:2\r\n", List.of(a("3", "b"))));
	}

	@Test
	void propertiesPreservesComments() {
		String in = "# Iris\nenableShaders=false\nshaderPack = old.zip\nmaxShadowRenderDistance\\\n  =32\n";
		String out = new PropertiesFormat().apply(in, List.of(a(Boolean.TRUE, "enableShaders"), a("New Pack.zip", "shaderPack"), a(new Json.Num("8"), "maxShadowRenderDistance"), a("x", "added")));
		assertEquals("# Iris\nenableShaders=true\nshaderPack = New Pack.zip\nmaxShadowRenderDistance=8\nadded=x\n", out);
	}

	@Test
	void jsonNestedAndArrays() {
		String in = """
				{
				    // comment
				    "quality": {"weather_quality": "DEFAULT", "leaves": 'FANCY',},
				    list: [1, {"a": 1}],
				}
				""";
		String out = new JsonFormat().apply(in, List.of(a("FAST", "quality", "weather_quality"), a(new Json.Num("2"), "list", "1", "a"), a(Boolean.TRUE, "new", "deep")));
		assertEquals("""
				{
				    "quality": {
				        "weather_quality": "FAST",
				        "leaves": "FANCY"
				    },
				    "list": [
				        1,
				        {
				            "a": 2
				        }
				    ],
				    "new": {
				        "deep": true
				    }
				}
				""", out);
	}

	@Test
	void jsonReadFlattens() {
		List<Assignment> r = new JsonFormat().read("{\"a\":{\"b\":1,\"c\":[1,2]},\"d\":{}}");
		assertEquals(3, r.size());
		assertEquals(List.of("a", "b"), r.get(0).path());
		assertEquals(List.of("a", "c"), r.get(1).path());
		assertEquals(List.of("d"), r.get(2).path());
	}

	@Test
	void tomlReplaceAppendAndCreate() {
		String in = """
				# top comment
				enabled = false # keep me

				[general]
				distance = 32
				list = [
				  "a",
				  "b",
				]

				[[things]]
				distance = 5
				""";
		String out = new TomlFormat().apply(in, List.of(
				a(Boolean.TRUE, "enabled"),
				a(new Json.Num("8"), "general", "distance"),
				a(List.of("x"), "general", "list"),
				a("hi", "general", "added"),
				a(new Json.Num("1"), "other", "value"),
				a("r", "rootkey")));
		assertEquals("""
				# top comment
				enabled = true # keep me
				rootkey = "r"

				[general]
				distance = 8
				list = ["x"]
				added = "hi"

				[[things]]
				distance = 5

				[other]
				value = 1
				""", out);
	}

	@Test
	void tomlReadRawValues() {
		List<Assignment> r = new TomlFormat().read("a = 1\n[t]\nb = \"x\" # c\n");
		assertEquals(List.of("t", "b"), r.get(1).path());
		assertEquals("\"x\"", r.get(1).value().toString());
	}

	@Test
	void splitPathEscapes() {
		assertEquals(List.of("a.b", "c"), Applier.splitPath("a\\.b.c"));
	}

	@Test
	void json5DefinitionsParse() {
		Definitions d = Definitions.parse("""
				{ defaultPreset: 'b', presets: [
				  { id: 'a', name: {en_us: 'A', de_de: 'Ä'}, operations: [] },
				  { id: 'b', extends: 'a', operations: [ { type: 'merge', file: 'options.txt', values: { renderDistance: 4 } } ] },
				]}
				""");
		assertEquals("Ä", d.presets().get("a").name().get("de_de"));
		assertEquals("A", d.presets().get("a").name().get("fr_fr"));
		assertTrue(d.operationsOf("b").getFirst() instanceof Definitions.Merge);
	}
}
