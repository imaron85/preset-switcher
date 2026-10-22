package io.github.imaron85.presetswitcher.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The pack-owned preset definitions ({@code config/presetswitcher/presets.json5}). */
public record Definitions(
		Text title,
		Text subtitle,
		boolean showChooserOnFirstLaunch,
		String defaultPreset,
		List<Operation> base,
		Map<String, Preset> presets) {

	public static final int FORMAT_VERSION = 1;

	public record Preset(String id, Text name, Text description, List<String> parents, List<Operation> operations) {
	}

	/** Plain text, or a map of Minecraft language codes to text ({@code en_us} is the fallback). */
	public record Text(Map<String, String> byLanguage) {
		static Text of(String s) {
			return new Text(Map.of("en_us", s));
		}

		public String get(String language) {
			if (byLanguage.isEmpty()) return "";
			String s = byLanguage.get(language.toLowerCase(Locale.ROOT));
			if (s == null) s = byLanguage.get("en_us");
			return s != null ? s : byLanguage.values().iterator().next();
		}
	}

	public enum When {ALWAYS, MISSING}

	/** One step of a preset. {@code id} is "base#N" or "presetId#N", used to retry deferred steps. */
	public sealed interface Operation permits Merge, Copy, ResourcePacks {
		String id();

		boolean optional();
	}

	/** Merges keys into a config file. {@code file} may contain glob wildcards. */
	public record Merge(String id, String file, String format, String from, Map<String, Object> values, boolean createIfMissing, boolean optional) implements Operation {
	}

	/** Copies a file or folder from the definitions folder into the game folder. */
	public record Copy(String id, String from, String to, When when, boolean optional) implements Operation {
	}

	/** Changes the enabled resource packs in options.txt ({@code set} replaces the list; {@code disable}, then {@code enable}). */
	public record ResourcePacks(String id, List<Object> set, List<Object> enable, List<Object> disable, boolean optional) implements Operation {
	}

	/** The operations of a preset including those of the presets it extends, parents first. */
	public List<Operation> operationsOf(String presetId) {
		List<Operation> out = new ArrayList<>();
		collect(presetId, out, new LinkedHashSet<>());
		return out;
	}

	private void collect(String presetId, List<Operation> out, Set<String> visiting) {
		Preset p = presets.get(presetId);
		if (p == null) throw new IllegalArgumentException("unknown preset '" + presetId + "'");
		if (!visiting.add(presetId)) throw new IllegalArgumentException("preset '" + presetId + "' extends itself (cycle: " + visiting + ")");
		for (String parent : p.parents) collect(parent, out, visiting);
		out.addAll(p.operations);
		visiting.remove(presetId);
	}

	// ---------------------------------------------------------------- parsing

	public static Definitions parse(String text) {
		Object root = Json.parse(text);
		if (!(root instanceof Map<?, ?>)) throw new IllegalArgumentException("presets.json5 must contain an object");
		Map<String, Object> m = map(root, "root");
		checkKeys(m, Set.of("formatVersion", "title", "subtitle", "showChooserOnFirstLaunch", "defaultPreset", "base", "presets"), "presets.json5");
		Object fv = m.get("formatVersion");
		if (fv != null && Integer.parseInt(fv.toString()) > FORMAT_VERSION) {
			throw new IllegalArgumentException("formatVersion " + fv + " is newer than this mod supports (" + FORMAT_VERSION + "); update Preset Switcher");
		}
		Text title = m.containsKey("title") ? text(m.get("title"), "title") : null;
		Text subtitle = m.containsKey("subtitle") ? text(m.get("subtitle"), "subtitle") : null;
		boolean showChooser = bool(m.getOrDefault("showChooserOnFirstLaunch", Boolean.TRUE), "showChooserOnFirstLaunch");
		List<Operation> base = operations(m.getOrDefault("base", List.of()), "base");

		Map<String, Preset> presets = new LinkedHashMap<>();
		Object list = m.get("presets");
		if (!(list instanceof List<?> presetList) || presetList.isEmpty()) throw new IllegalArgumentException("\"presets\" must be a non-empty array");
		for (int i = 0; i < presetList.size(); i++) {
			Map<String, Object> p = map(presetList.get(i), "presets[" + i + "]");
			checkKeys(p, Set.of("id", "name", "description", "extends", "operations"), "presets[" + i + "]");
			String id = str(p.get("id"), "presets[" + i + "].id");
			if (!id.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("preset id '" + id + "' may only contain letters, digits, '_', '-' and '.'");
			if (presets.containsKey(id)) throw new IllegalArgumentException("duplicate preset id '" + id + "'");
			Text name = p.containsKey("name") ? text(p.get("name"), id + ".name") : Text.of(id);
			Text description = p.containsKey("description") ? text(p.get("description"), id + ".description") : Text.of("");
			List<String> parents = new ArrayList<>();
			Object ext = p.get("extends");
			if (ext instanceof String s) parents.add(s);
			else if (ext instanceof List<?> l) for (Object o : l) parents.add(str(o, id + ".extends"));
			else if (ext != null) throw new IllegalArgumentException(id + ".extends must be a preset id or a list of ids");
			presets.put(id, new Preset(id, name, description, List.copyOf(parents), operations(p.getOrDefault("operations", List.of()), id)));
		}
		String def = m.containsKey("defaultPreset") ? str(m.get("defaultPreset"), "defaultPreset") : presets.keySet().iterator().next();
		Definitions d = new Definitions(title, subtitle, showChooser, def, base, presets);
		if (!presets.containsKey(def)) throw new IllegalArgumentException("defaultPreset '" + def + "' is not defined");
		for (Preset p : presets.values()) {
			for (String parent : p.parents) if (!presets.containsKey(parent)) throw new IllegalArgumentException(p.id + " extends unknown preset '" + parent + "'");
			d.operationsOf(p.id); // cycle check
		}
		return d;
	}

	private static List<Operation> operations(Object value, String owner) {
		if (!(value instanceof List<?> list)) throw new IllegalArgumentException(owner + " operations must be an array");
		List<Operation> ops = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			String id = owner + "#" + i;
			Map<String, Object> o = map(list.get(i), id);
			String type = str(o.get("type"), id + ".type");
			boolean optional = bool(o.getOrDefault("optional", Boolean.FALSE), id + ".optional");
			Set<String> allowed = switch (type) {
				case "merge" -> Set.of("file", "format", "from", "values", "createIfMissing");
				case "copy" -> Set.of("from", "to", "when");
				case "resourcePacks" -> Set.of("set", "enable", "disable");
				default -> Set.of();
			};
			for (String key : o.keySet()) {
				if (!key.equals("type") && !key.equals("optional") && !key.equals("comment") && !allowed.contains(key)) {
					throw new IllegalArgumentException(id + ": unknown key '" + key + "' for type " + type + " (allowed: " + allowed + ", optional, comment)");
				}
			}
			ops.add(switch (type) {
				case "merge" -> {
					String file = path(o.get("file"), id + ".file");
					String from = o.containsKey("from") ? path(o.get("from"), id + ".from") : null;
					Map<String, Object> values = o.containsKey("values") ? map(o.get("values"), id + ".values") : Map.of();
					if (from == null && values.isEmpty()) throw new IllegalArgumentException(id + ": a merge needs \"values\" and/or \"from\"");
					String format = o.containsKey("format") ? str(o.get("format"), id + ".format") : null;
					yield new Merge(id, file, format, from, values, bool(o.getOrDefault("createIfMissing", Boolean.TRUE), id + ".createIfMissing"), optional);
				}
				case "copy" -> {
					String when = str(o.getOrDefault("when", "always"), id + ".when").toUpperCase(Locale.ROOT);
					if (!when.equals("ALWAYS") && !when.equals("MISSING")) throw new IllegalArgumentException(id + ".when must be \"always\" or \"missing\"");
					yield new Copy(id, path(o.get("from"), id + ".from"), path(o.get("to"), id + ".to"), When.valueOf(when), optional);
				}
				case "resourcePacks" -> {
					List<Object> set = o.containsKey("set") ? list(o.get("set"), id + ".set") : null;
					List<Object> enable = list(o.getOrDefault("enable", List.of()), id + ".enable");
					List<Object> disable = list(o.getOrDefault("disable", List.of()), id + ".disable");
					yield new ResourcePacks(id, set, enable, disable, optional);
				}
				default -> throw new IllegalArgumentException(id + ": unknown type '" + type + "' (merge, copy, resourcePacks)");
			});
		}
		return List.copyOf(ops);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object o, String where) {
		if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
		throw new IllegalArgumentException(where + " must be an object");
	}

	@SuppressWarnings("unchecked")
	private static List<Object> list(Object o, String where) {
		if (o instanceof List<?> l) return (List<Object>) l;
		throw new IllegalArgumentException(where + " must be an array");
	}

	private static String str(Object o, String where) {
		if (o instanceof String s && !s.isEmpty()) return s;
		throw new IllegalArgumentException(where + " must be a non-empty string");
	}

	private static String path(Object o, String where) {
		String s = str(o, where).replace('\\', '/');
		if (s.startsWith("/") || s.matches("^[A-Za-z]:.*")) throw new IllegalArgumentException(where + " must be a relative path: " + s);
		for (String part : s.split("/")) if (part.equals("..")) throw new IllegalArgumentException(where + " must not contain '..': " + s);
		return s;
	}

	private static void checkKeys(Map<String, Object> m, Set<String> allowed, String where) {
		for (String key : m.keySet()) {
			if (!allowed.contains(key) && !key.equals("comment")) throw new IllegalArgumentException(where + ": unknown key '" + key + "' (allowed: " + allowed + ", comment)");
		}
	}

	private static boolean bool(Object o, String where) {
		if (o instanceof Boolean b) return b;
		throw new IllegalArgumentException(where + " must be true or false");
	}

	private static Text text(Object o, String where) {
		if (o instanceof String s) return Text.of(s);
		if (o instanceof Map<?, ?> m) {
			Map<String, String> out = new LinkedHashMap<>();
			for (Map.Entry<?, ?> e : m.entrySet()) out.put(((String) e.getKey()).toLowerCase(Locale.ROOT), str(e.getValue(), where + "." + e.getKey()));
			return new Text(out);
		}
		throw new IllegalArgumentException(where + " must be a string or an object of language code to string");
	}
}
