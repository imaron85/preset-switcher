package io.github.imaron85.presetswitcher.core.format;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java {@code .properties} files ({@code key=value}, {@code key: value} or {@code key value}; {@code #}/{@code !} comments;
 * backslash line continuations). Comments, order and untouched entries are preserved.
 */
public final class PropertiesFormat implements FileFormat {
	@Override
	public String id() {
		return "properties";
	}

	@Override
	public boolean nestedKeys() {
		return false;
	}

	/** One logical entry: lines [start, end] of the file, the parsed key and where its value text begins. */
	private record Entry(int start, int end, String key, String prefix, String rawValue) {
	}

	private static List<Entry> entries(List<String> lines) {
		List<Entry> out = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			int p = 0;
			while (p < line.length() && isWs(line.charAt(p))) p++;
			if (p >= line.length() || line.charAt(p) == '#' || line.charAt(p) == '!') continue;
			int start = i;
			StringBuilder logical = new StringBuilder(line);
			while (endsWithContinuation(logical) && i + 1 < lines.size()) {
				logical.setLength(logical.length() - 1);
				String next = lines.get(++i);
				int q = 0;
				while (q < next.length() && isWs(next.charAt(q))) q++;
				logical.append(next.substring(q));
			}
			String l = logical.toString();
			int k = p;
			StringBuilder key = new StringBuilder();
			while (k < l.length()) {
				char c = l.charAt(k);
				if (c == '\\' && k + 1 < l.length()) {
					key.append(unescapeChar(l, k));
					k += escapeLength(l, k);
					continue;
				}
				if (c == '=' || c == ':' || isWs(c)) break;
				key.append(c);
				k++;
			}
			int v = k;
			while (v < l.length() && isWs(l.charAt(v))) v++;
			if (v < l.length() && (l.charAt(v) == '=' || l.charAt(v) == ':')) {
				v++;
				while (v < l.length() && isWs(l.charAt(v))) v++;
			}
			out.add(new Entry(start, i, key.toString(), l.substring(0, v), l.substring(v)));
		}
		return out;
	}

	private static boolean isWs(char c) {
		return c == ' ' || c == '\t' || c == '\f';
	}

	private static boolean endsWithContinuation(CharSequence s) {
		int n = 0;
		for (int i = s.length() - 1; i >= 0 && s.charAt(i) == '\\'; i--) n++;
		return n % 2 == 1;
	}

	private static int escapeLength(String s, int i) {
		return s.charAt(i + 1) == 'u' && i + 6 <= s.length() ? 6 : 2;
	}

	private static char unescapeChar(String s, int i) {
		char e = s.charAt(i + 1);
		return switch (e) {
			case 't' -> '\t';
			case 'n' -> '\n';
			case 'r' -> '\r';
			case 'f' -> '\f';
			case 'u' -> i + 6 <= s.length() ? (char) Integer.parseInt(s.substring(i + 2, i + 6), 16) : 'u';
			default -> e;
		};
	}

	@Override
	public String apply(String original, List<Assignment> assignments) {
		Lines file = Lines.of(original);
		Map<String, String> pending = new LinkedHashMap<>();
		for (Assignment a : assignments) pending.put(a.key(), render(a.value()));
		List<Entry> entries = entries(file.lines);
		// Replace from the bottom so that removing continuation lines keeps earlier indices valid.
		for (int i = entries.size() - 1; i >= 0; i--) {
			Entry e = entries.get(i);
			if (!pending.containsKey(e.key)) continue;
			// Duplicate keys: the last occurrence wins in java.util.Properties, so only that one is replaced.
			String value = pending.remove(e.key);
			for (int j = e.end; j > e.start; j--) file.lines.remove(j);
			file.lines.set(e.start, e.prefix + value);
		}
		pending.forEach((k, v) -> file.lines.add(escape(k, true) + "=" + v));
		return file.join();
	}

	@Override
	public List<Assignment> read(String content) {
		List<Assignment> out = new ArrayList<>();
		for (Entry e : entries(Lines.of(content).lines)) out.add(new Assignment(List.of(e.key), new Raw(e.rawValue)));
		return out;
	}

	private static String render(Object value) {
		if (value instanceof Raw raw) return raw.text();
		return escape(Lines.render(value), false);
	}

	static String escape(String s, boolean key) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				case '\f' -> sb.append("\\f");
				case '=', ':', '#', '!' -> sb.append(key ? "\\" + c : String.valueOf(c));
				case ' ' -> sb.append(key || i == 0 ? "\\ " : " ");
				default -> {
					if (c < 0x20 || c > 0x7e) sb.append(String.format("\\u%04X", (int) c));
					else sb.append(c);
				}
			}
		}
		return sb.toString();
	}
}
