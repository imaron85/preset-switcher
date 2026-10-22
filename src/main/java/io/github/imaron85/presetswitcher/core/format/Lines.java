package io.github.imaron85.presetswitcher.core.format;

import java.util.ArrayList;
import java.util.List;

/** A text file as lines, remembering its line separator so a rewrite keeps the file's style. */
final class Lines {
	final List<String> lines;
	final String newline;

	private Lines(List<String> lines, String newline) {
		this.lines = lines;
		this.newline = newline;
	}

	static Lines of(String content) {
		if (content == null || content.isEmpty()) return new Lines(new ArrayList<>(), System.lineSeparator().equals("\r\n") ? "\r\n" : "\n");
		if (content.startsWith("\uFEFF")) content = content.substring(1);
		String newline = content.contains("\r\n") ? "\r\n" : "\n";
		List<String> lines = new ArrayList<>(List.of(content.split("\r?\n", -1)));
		// A trailing newline produces one empty last element; it is re-added on output.
		if (!lines.isEmpty() && lines.getLast().isEmpty()) lines.removeLast();
		return new Lines(lines, newline);
	}

	String join() {
		StringBuilder sb = new StringBuilder();
		for (String line : lines) sb.append(line).append(newline);
		return sb.toString();
	}

	static String render(Object value) {
		if (value instanceof List<?> || value instanceof java.util.Map<?, ?>) return io.github.imaron85.presetswitcher.core.Json.writeCompact(value);
		return String.valueOf(value);
	}
}
