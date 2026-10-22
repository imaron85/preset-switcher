package io.github.imaron85.presetswitcher.core.format;

import io.github.imaron85.presetswitcher.core.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TOML files, edited line by line so comments and layout survive. A path is {@code table.subtable.key};
 * values in {@code [table]} sections and dotted keys are found, missing keys are appended to their table
 * (or a new {@code [table]} section at the end). Entries inside {@code [[array tables]]} are not addressable.
 */
public final class TomlFormat implements FileFormat {
	@Override
	public String id() {
		return "toml";
	}

	@Override
	public boolean nestedKeys() {
		return true;
	}

	private record Entry(String path, List<String> parts, int startLine, int valueCol, int endLine, int endCol) {
	}

	private record Table(int headerLine, int lastLine) {
	}

	private static final class Scan {
		final List<Entry> entries = new ArrayList<>();
		/** Joined table path ("" = root) to its header line and its last content line. */
		final Map<String, Table> tables = new LinkedHashMap<>();
		int firstHeader = -1;
	}

	private static Scan scan(List<String> lines) {
		Scan scan = new Scan();
		String table = "";
		List<String> tableParts = List.of();
		boolean inArrayTable = false;
		int rootLast = -1;
		scan.tables.put("", new Table(-1, -1));
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			String t = line.strip();
			if (t.isEmpty() || t.startsWith("#")) continue;
			if (t.startsWith("[")) {
				if (scan.firstHeader < 0) scan.firstHeader = i;
				if (t.startsWith("[[")) {
					inArrayTable = true;
					continue;
				}
				inArrayTable = false;
				int close = findHeaderEnd(t);
				tableParts = parseKey(t.substring(1, close));
				table = String.join(".", tableParts);
				scan.tables.put(table, new Table(i, i));
				continue;
			}
			int eq = findKeyEnd(line);
			if (eq < 0) continue;
			List<String> key = parseKey(line.substring(0, eq));
			int valueCol = eq + 1;
			while (valueCol < line.length() && (line.charAt(valueCol) == ' ' || line.charAt(valueCol) == '\t')) valueCol++;
			int[] end = valueEnd(lines, i, valueCol);
			if (!inArrayTable) {
				List<String> parts = new ArrayList<>(tableParts);
				parts.addAll(key);
				scan.entries.add(new Entry(String.join(".", parts), List.copyOf(parts), i, valueCol, end[0], end[1]));
				Table tb = scan.tables.get(table);
				scan.tables.put(table, new Table(tb.headerLine, end[0]));
				if (table.isEmpty()) rootLast = end[0];
			}
			i = end[0];
		}
		scan.tables.put("", new Table(-1, rootLast));
		return scan;
	}

	private static int findHeaderEnd(String t) {
		char quote = 0;
		for (int i = 1; i < t.length(); i++) {
			char c = t.charAt(i);
			if (quote != 0) {
				if (c == '\\' && quote == '"') i++;
				else if (c == quote) quote = 0;
			} else if (c == '"' || c == '\'') {
				quote = c;
			} else if (c == ']') {
				return i;
			}
		}
		throw new IllegalArgumentException("unterminated table header: " + t);
	}

	/** Index of the '=' that ends the key, or -1. */
	private static int findKeyEnd(String line) {
		char quote = 0;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (quote != 0) {
				if (c == '\\' && quote == '"') i++;
				else if (c == quote) quote = 0;
			} else if (c == '"' || c == '\'') {
				quote = c;
			} else if (c == '=') {
				return i;
			} else if (c == '#') {
				return -1;
			}
		}
		return -1;
	}

	/** Splits a (possibly dotted, possibly quoted) TOML key into its parts. */
	static List<String> parseKey(String raw) {
		List<String> parts = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		String s = raw.strip();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '"' || c == '\'') {
				int j = i + 1;
				while (j < s.length() && s.charAt(j) != c) {
					if (c == '"' && s.charAt(j) == '\\' && j + 1 < s.length()) {
						j++;
						char e = s.charAt(j);
						cur.append(switch (e) {
							case 'n' -> '\n';
							case 't' -> '\t';
							default -> e;
						});
					} else {
						cur.append(s.charAt(j));
					}
					j++;
				}
				i = j;
			} else if (c == '.') {
				parts.add(cur.toString().strip());
				cur.setLength(0);
			} else if (c != ' ' && c != '\t') {
				cur.append(c);
			}
		}
		parts.add(cur.toString().strip());
		return parts;
	}

	/** Returns {line, column} just after the value starting at (line, col). */
	private static int[] valueEnd(List<String> lines, int line, int col) {
		int depth = 0;
		int i = line;
		int p = col;
		String s = lines.get(i);
		int lastNonWs = col;
		while (true) {
			if (p >= s.length()) {
				if (depth == 0 || i + 1 >= lines.size()) return new int[]{i, Math.min(lastNonWs, s.length())};
				s = lines.get(++i);
				p = 0;
				lastNonWs = 0;
				continue;
			}
			char c = s.charAt(p);
			if (c == '#') {
				if (depth == 0) return new int[]{i, lastNonWs};
				p = s.length();
				continue;
			}
			if (c == '"' || c == '\'') {
				boolean multi = s.startsWith(String.valueOf(c).repeat(3), p);
				if (multi) {
					String delim = String.valueOf(c).repeat(3);
					int from = p + 3;
					while (true) {
						int idx = s.indexOf(delim, from);
						if (idx >= 0 && (c == '\'' || !escaped(s, idx))) {
							p = idx + 3;
							while (p < s.length() && s.charAt(p) == c) p++;
							break;
						}
						if (idx >= 0) {
							from = idx + 1;
							continue;
						}
						if (i + 1 >= lines.size()) throw new IllegalArgumentException("unterminated multi-line string at line " + (line + 1));
						s = lines.get(++i);
						from = 0;
					}
				} else {
					p++;
					while (p < s.length() && s.charAt(p) != c) {
						if (c == '"' && s.charAt(p) == '\\') p++;
						p++;
					}
					p++;
				}
				lastNonWs = Math.min(p, s.length());
				continue;
			}
			if (c == '[' || c == '{') depth++;
			if (c == ']' || c == '}') depth--;
			p++;
			if (c != ' ' && c != '\t') lastNonWs = p;
		}
	}

	private static boolean escaped(String s, int idx) {
		int n = 0;
		for (int i = idx - 1; i >= 0 && s.charAt(i) == '\\'; i--) n++;
		return n % 2 == 1;
	}

	@Override
	public String apply(String original, List<Assignment> assignments) {
		Lines file = Lines.of(original);
		Map<String, Assignment> pending = new LinkedHashMap<>();
		for (Assignment a : assignments) pending.put(a.key(), a);

		// 1. Replace existing values (bottom-up, so removed continuation lines don't shift pending entries).
		List<Entry> entries = scan(file.lines).entries;
		for (int i = entries.size() - 1; i >= 0; i--) {
			Entry e = entries.get(i);
			Assignment a = pending.remove(e.path);
			if (a == null) continue;
			String head = file.lines.get(e.startLine).substring(0, e.valueCol);
			String tail = file.lines.get(e.endLine).substring(e.endCol);
			for (int j = e.endLine; j > e.startLine; j--) file.lines.remove(j);
			file.lines.set(e.startLine, head + render(a.value()) + tail);
		}
		if (pending.isEmpty()) return file.join();

		// 2. Append missing keys to their table, grouped by table.
		Map<String, List<Assignment>> byTable = new LinkedHashMap<>();
		for (Assignment a : pending.values()) {
			String table = String.join(".", a.path().subList(0, a.path().size() - 1));
			byTable.computeIfAbsent(table, k -> new ArrayList<>()).add(a);
		}
		for (Map.Entry<String, List<Assignment>> group : byTable.entrySet()) {
			Scan scan = scan(file.lines);
			Table table = scan.tables.get(group.getKey());
			List<String> newLines = new ArrayList<>();
			for (Assignment a : group.getValue()) newLines.add(renderKey(a.path().getLast()) + " = " + render(a.value()));
			if (table != null) {
				int at;
				if (group.getKey().isEmpty()) at = table.lastLine >= 0 ? table.lastLine + 1 : (scan.firstHeader >= 0 ? scan.firstHeader : file.lines.size());
				else at = table.lastLine + 1;
				if (group.getKey().isEmpty() && table.lastLine < 0 && scan.firstHeader >= 0) newLines.add("");
				file.lines.addAll(at, newLines);
			} else {
				if (!file.lines.isEmpty() && !file.lines.getLast().isBlank()) file.lines.add("");
				List<String> header = new ArrayList<>();
				for (String part : group.getValue().getFirst().path().subList(0, group.getValue().getFirst().path().size() - 1)) header.add(renderKey(part));
				file.lines.add("[" + String.join(".", header) + "]");
				file.lines.addAll(newLines);
			}
		}
		return file.join();
	}

	@Override
	public List<Assignment> read(String content) {
		List<String> lines = Lines.of(content).lines;
		List<Assignment> out = new ArrayList<>();
		for (Entry e : scan(lines).entries) {
			StringBuilder value = new StringBuilder();
			for (int i = e.startLine; i <= e.endLine; i++) {
				String l = lines.get(i);
				int from = i == e.startLine ? e.valueCol : 0;
				int to = i == e.endLine ? e.endCol : l.length();
				if (i > e.startLine) value.append('\n');
				value.append(l, from, to);
			}
			out.add(new Assignment(e.parts, new Raw(value.toString())));
		}
		return out;
	}

	static String renderKey(String key) {
		return key.matches("[A-Za-z0-9_-]+") ? key : Json.quote(key);
	}

	static String render(Object value) {
		if (value instanceof Raw raw) return raw.text();
		if (value instanceof String s) return Json.quote(s);
		if (value instanceof List<?> list) {
			List<String> items = new ArrayList<>();
			for (Object o : list) items.add(render(o));
			return "[" + String.join(", ", items) + "]";
		}
		if (value instanceof Map<?, ?> map) {
			List<String> items = new ArrayList<>();
			for (Map.Entry<?, ?> e : map.entrySet()) items.add(renderKey((String) e.getKey()) + " = " + render(e.getValue()));
			return items.isEmpty() ? "{}" : "{ " + String.join(", ", items) + " }";
		}
		if (value == Json.NULL || value == null) throw new IllegalArgumentException("TOML has no null value");
		return String.valueOf(value);
	}
}
