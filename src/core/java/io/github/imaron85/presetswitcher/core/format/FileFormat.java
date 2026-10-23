package io.github.imaron85.presetswitcher.core.format;

import java.util.List;
import java.util.Locale;

/**
 * A config file format that can merge individual keys into an existing file while leaving every other key
 * (and, for line based formats, comments and layout) untouched.
 */
public interface FileFormat {
	String id();

	/**
	 * Whether keys given in preset definitions are split on unescaped dots into nested paths
	 * (JSON, TOML). Line based formats (options.txt, .properties) use keys verbatim.
	 */
	boolean nestedKeys();

	/**
	 * @param original the current file content, or {@code null} if the file does not exist yet
	 * @return the new file content
	 */
	String apply(String original, List<Assignment> assignments);

	/** Reads every leaf key of a file, used to merge a whole partial file shipped in a preset folder. */
	List<Assignment> read(String content);

	/** A key path (one element for line based formats) and the value to set. */
	record Assignment(List<String> path, Object value) {
		public String key() {
			return String.join(".", path);
		}
	}

	/** A value written verbatim (taken from a source file of the same format). */
	record Raw(String text) {
		@Override
		public String toString() {
			return text;
		}
	}

	static FileFormat byId(String id, int dataVersion) {
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "options", "keyvalue", "key_value" -> new OptionsFormat(dataVersion);
			case "properties" -> new PropertiesFormat();
			case "json", "json5" -> new JsonFormat();
			case "toml" -> new TomlFormat();
			default -> throw new IllegalArgumentException("unknown format '" + id + "' (options, properties, json, toml)");
		};
	}

	/** Infers the format from a file name: options*.txt, *.properties, *.json/*.json5, *.toml. Other names need "format". */
	static String inferId(String fileName) {
		String n = fileName.toLowerCase(Locale.ROOT);
		int slash = n.lastIndexOf('/');
		String base = slash >= 0 ? n.substring(slash + 1) : n;
		if (base.endsWith(".properties")) return "properties";
		if (base.endsWith(".json") || base.endsWith(".json5")) return "json";
		if (base.endsWith(".toml")) return "toml";
		if (base.startsWith("options") && base.endsWith(".txt")) return "options";
		throw new IllegalArgumentException("cannot infer the format of '" + fileName + "', set \"format\"");
	}
}
