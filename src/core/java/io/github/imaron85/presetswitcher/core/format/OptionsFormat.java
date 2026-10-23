package io.github.imaron85.presetswitcher.core.format;

import io.github.imaron85.presetswitcher.core.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft's {@code options.txt} style: one {@code key:value} per line, split at the first colon.
 * Keys are used verbatim (key bindings such as {@code key_key.attack} contain dots).
 * String values are written as-is; lists and objects as compact JSON, e.g. {@code resourcePacks:["vanilla"]}.
 */
public final class OptionsFormat implements FileFormat {
	private final int dataVersion;

	/** @param dataVersion written as {@code version:} when the file is created, so vanilla does not run its data fixers from version 0; -1 if unknown */
	public OptionsFormat(int dataVersion) {
		this.dataVersion = dataVersion;
	}

	@Override
	public String id() {
		return "options";
	}

	@Override
	public boolean nestedKeys() {
		return false;
	}

	@Override
	public String apply(String original, List<Assignment> assignments) {
		Lines file = Lines.of(original);
		if (original == null && dataVersion > 0) file.lines.add("version:" + dataVersion);
		Map<String, Object> pending = new LinkedHashMap<>();
		for (Assignment a : assignments) pending.put(a.key(), a.value());
		for (int i = 0; i < file.lines.size() && !pending.isEmpty(); i++) {
			String line = file.lines.get(i);
			int colon = line.indexOf(':');
			if (colon < 0) continue;
			String key = line.substring(0, colon);
			if (!pending.containsKey(key)) continue;
			file.lines.set(i, key + ":" + render(pending.remove(key), line.substring(colon + 1)));
		}
		pending.forEach((k, v) -> file.lines.add(k + ":" + render(v, null)));
		return file.join();
	}

	/**
	 * Vanilla stores option-instance values as JSON (enums quoted: {@code graphicsPreset:"custom"}) and plain string
	 * fields raw ({@code lang:en_us}). A string value is therefore quoted when the existing value in the file is quoted.
	 */
	private static String render(Object value, String existing) {
		if (value instanceof String s && existing != null && existing.startsWith("\"") && !s.startsWith("\"")) return Json.quote(s);
		return Lines.render(value);
	}

	@Override
	public List<Assignment> read(String content) {
		List<Assignment> out = new ArrayList<>();
		for (String line : Lines.of(content).lines) {
			int colon = line.indexOf(':');
			if (colon <= 0) continue;
			String key = line.substring(0, colon);
			if (key.equals("version")) continue;
			out.add(new Assignment(List.of(key), new Raw(line.substring(colon + 1))));
		}
		return out;
	}

	/** Returns the raw value of a key, or {@code null}. */
	public static String get(String content, String key) {
		for (String line : Lines.of(content).lines) {
			int colon = line.indexOf(':');
			if (colon > 0 && line.substring(0, colon).equals(key)) return line.substring(colon + 1);
		}
		return null;
	}
}
