package io.github.imaron85.presetswitcher.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free JSON / JSON5 reader and JSON writer.
 *
 * <p>It runs before Minecraft's libraries (Gson) are on the class path, so it cannot use them.
 * Reading accepts JSON5 conveniences: comments ({@code //}, {@code /* *&#47;}, {@code #}), trailing commas,
 * unquoted keys and single-quoted strings. Objects keep their key order. Numbers keep their literal text.
 *
 * <p>Value model: {@link Map} (LinkedHashMap), {@link List}, {@link String}, {@link Boolean}, {@link Num}, {@link #NULL}.
 */
public final class Json {
	/** JSON {@code null}. */
	public static final Object NULL = new Object() {
		@Override
		public String toString() {
			return "null";
		}
	};

	/** A number, kept as its literal text so that writing it back does not change formatting. */
	public record Num(String literal) {
		@Override
		public String toString() {
			return literal;
		}
	}

	private Json() {
	}

	public static Object parse(String text) {
		Parser p = new Parser(text);
		p.skipWs();
		Object value = p.value();
		p.skipWs();
		if (p.pos < text.length()) throw p.error("unexpected trailing content");
		return value;
	}

	/** Pretty-prints with the given indent unit (e.g. two spaces or a tab). */
	public static String write(Object value, String indent) {
		StringBuilder sb = new StringBuilder();
		write(sb, value, indent, "");
		return sb.toString();
	}

	/** Single-line output without spaces (the style of options.txt values). */
	public static String writeCompact(Object value) {
		StringBuilder sb = new StringBuilder();
		write(sb, value, null, "");
		return sb.toString();
	}

	private static void write(StringBuilder sb, Object value, String indent, String current) {
		if (value instanceof Map<?, ?> map) {
			if (map.isEmpty()) {
				sb.append("{}");
				return;
			}
			sb.append('{');
			String inner = indent == null ? "" : current + indent;
			boolean first = true;
			for (Map.Entry<?, ?> e : map.entrySet()) {
				if (!first) sb.append(',');
				first = false;
				if (indent != null) sb.append('\n').append(inner);
				quote(sb, (String) e.getKey());
				sb.append(indent == null ? ":" : ": ");
				write(sb, e.getValue(), indent, inner);
			}
			if (indent != null) sb.append('\n').append(current);
			sb.append('}');
		} else if (value instanceof List<?> list) {
			if (list.isEmpty()) {
				sb.append("[]");
				return;
			}
			sb.append('[');
			String inner = indent == null ? "" : current + indent;
			boolean first = true;
			for (Object o : list) {
				if (!first) sb.append(',');
				first = false;
				if (indent != null) sb.append('\n').append(inner);
				write(sb, o, indent, inner);
			}
			if (indent != null) sb.append('\n').append(current);
			sb.append(']');
		} else if (value instanceof String s) {
			quote(sb, s);
		} else if (value == null) {
			sb.append("null");
		} else {
			sb.append(value);
		}
	}

	public static String quote(String s) {
		StringBuilder sb = new StringBuilder(s.length() + 2);
		quote(sb, s);
		return sb.toString();
	}

	private static void quote(StringBuilder sb, String s) {
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				case '\b' -> sb.append("\\b");
				case '\f' -> sb.append("\\f");
				default -> {
					if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
					else sb.append(c);
				}
			}
		}
		sb.append('"');
	}

	/** Detects the indent unit of an existing JSON document (defaults to two spaces). */
	public static String detectIndent(String text) {
		for (String line : text.split("\n", 64)) {
			if (line.isBlank()) continue;
			int i = 0;
			while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
			if (i > 0) return line.substring(0, i);
		}
		return "  ";
	}

	public static final class ParseException extends RuntimeException {
		public ParseException(String message) {
			super(message);
		}
	}

	private static final class Parser {
		private final String s;
		private int pos;

		Parser(String s) {
			this.s = s.startsWith("\uFEFF") ? s.substring(1) : s;
		}

		ParseException error(String msg) {
			int line = 1, col = 1;
			for (int i = 0; i < Math.min(pos, s.length()); i++) {
				if (s.charAt(i) == '\n') {
					line++;
					col = 1;
				} else {
					col++;
				}
			}
			return new ParseException(msg + " at line " + line + ", column " + col);
		}

		void skipWs() {
			while (pos < s.length()) {
				char c = s.charAt(pos);
				if (Character.isWhitespace(c)) {
					pos++;
				} else if (c == '#' || (c == '/' && pos + 1 < s.length() && s.charAt(pos + 1) == '/')) {
					while (pos < s.length() && s.charAt(pos) != '\n') pos++;
				} else if (c == '/' && pos + 1 < s.length() && s.charAt(pos + 1) == '*') {
					int end = s.indexOf("*/", pos + 2);
					if (end < 0) throw error("unterminated comment");
					pos = end + 2;
				} else {
					return;
				}
			}
		}

		Object value() {
			if (pos >= s.length()) throw error("unexpected end of input");
			char c = s.charAt(pos);
			return switch (c) {
				case '{' -> object();
				case '[' -> array();
				case '"', '\'' -> string();
				default -> bare();
			};
		}

		Map<String, Object> object() {
			pos++;
			Map<String, Object> map = new LinkedHashMap<>();
			while (true) {
				skipWs();
				if (pos >= s.length()) throw error("unterminated object");
				if (s.charAt(pos) == '}') {
					pos++;
					return map;
				}
				String key;
				char c = s.charAt(pos);
				if (c == '"' || c == '\'') {
					key = string();
				} else {
					int start = pos;
					while (pos < s.length() && !Character.isWhitespace(s.charAt(pos)) && s.charAt(pos) != ':') pos++;
					key = s.substring(start, pos);
					if (key.isEmpty()) throw error("expected key");
				}
				skipWs();
				if (pos >= s.length() || s.charAt(pos) != ':') throw error("expected ':'");
				pos++;
				skipWs();
				map.put(key, value());
				skipWs();
				if (pos < s.length() && s.charAt(pos) == ',') {
					pos++;
				} else {
					skipWs();
					if (pos >= s.length() || s.charAt(pos) != '}') throw error("expected ',' or '}'");
				}
			}
		}

		List<Object> array() {
			pos++;
			List<Object> list = new ArrayList<>();
			while (true) {
				skipWs();
				if (pos >= s.length()) throw error("unterminated array");
				if (s.charAt(pos) == ']') {
					pos++;
					return list;
				}
				list.add(value());
				skipWs();
				if (pos < s.length() && s.charAt(pos) == ',') {
					pos++;
				} else if (pos >= s.length() || s.charAt(pos) != ']') {
					throw error("expected ',' or ']'");
				}
			}
		}

		String string() {
			char quote = s.charAt(pos++);
			StringBuilder sb = new StringBuilder();
			while (true) {
				if (pos >= s.length()) throw error("unterminated string");
				char c = s.charAt(pos++);
				if (c == quote) return sb.toString();
				if (c != '\\') {
					sb.append(c);
					continue;
				}
				if (pos >= s.length()) throw error("unterminated string");
				char e = s.charAt(pos++);
				switch (e) {
					case 'n' -> sb.append('\n');
					case 'r' -> sb.append('\r');
					case 't' -> sb.append('\t');
					case 'b' -> sb.append('\b');
					case 'f' -> sb.append('\f');
					case '0' -> sb.append('\0');
					case '\n' -> {
					}
					case 'u' -> {
						if (pos + 4 > s.length()) throw error("bad unicode escape");
						sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
						pos += 4;
					}
					default -> sb.append(e);
				}
			}
		}

		Object bare() {
			int start = pos;
			while (pos < s.length()) {
				char c = s.charAt(pos);
				if (Character.isWhitespace(c) || c == ',' || c == '}' || c == ']' || c == ':' || c == '/' || c == '#') break;
				pos++;
			}
			String token = s.substring(start, pos);
			switch (token) {
				case "true":
					return Boolean.TRUE;
				case "false":
					return Boolean.FALSE;
				case "null":
					return NULL;
				default:
					if (token.matches("[+-]?(\\d+\\.?\\d*([eE][+-]?\\d+)?|\\.\\d+([eE][+-]?\\d+)?|0[xX][0-9a-fA-F]+|Infinity|NaN)")) {
						return new Num(token);
					}
					pos = start;
					throw error("unexpected token '" + token + "'");
			}
		}
	}
}
