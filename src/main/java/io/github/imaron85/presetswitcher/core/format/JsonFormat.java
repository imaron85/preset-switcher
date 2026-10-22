package io.github.imaron85.presetswitcher.core.format;

import io.github.imaron85.presetswitcher.core.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON and JSON5 files. Paths address object keys and array indices ({@code a.b.0.c}); arrays are otherwise set as a whole.
 * The file is re-serialised as standard JSON (key order and indent unit kept, comments dropped);
 * standard JSON is also valid JSON5.
 */
public final class JsonFormat implements FileFormat {
	@Override
	public String id() {
		return "json";
	}

	@Override
	public boolean nestedKeys() {
		return true;
	}

	@Override
	public String apply(String original, List<Assignment> assignments) {
		Object root = original == null || original.isBlank() ? new LinkedHashMap<String, Object>() : Json.parse(original);
		for (Assignment a : assignments) set(root, a.path(), toJson(a.value()));
		String indent = original == null ? "  " : Json.detectIndent(original);
		boolean trailingNewline = original == null || original.endsWith("\n");
		return Json.write(root, indent) + (trailingNewline ? "\n" : "");
	}

	private static Object toJson(Object value) {
		return value instanceof Raw raw ? Json.parse(raw.text()) : value;
	}

	@SuppressWarnings("unchecked")
	static void set(Object root, List<String> path, Object value) {
		Object current = root;
		for (int i = 0; i < path.size(); i++) {
			String seg = path.get(i);
			boolean last = i == path.size() - 1;
			if (current instanceof Map<?, ?> m) {
				Map<String, Object> map = (Map<String, Object>) m;
				if (last) {
					map.put(seg, value);
					return;
				}
				Object child = map.get(seg);
				if (!(child instanceof Map<?, ?>) && !(child instanceof List<?>)) {
					if (child != null) throw new IllegalArgumentException("'" + String.join(".", path.subList(0, i + 1)) + "' is not an object or array");
					child = new LinkedHashMap<String, Object>();
					map.put(seg, child);
				}
				current = child;
			} else if (current instanceof List<?> l) {
				List<Object> list = (List<Object>) l;
				int index;
				try {
					index = Integer.parseInt(seg);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("'" + String.join(".", path.subList(0, i)) + "' is an array, '" + seg + "' is not an index");
				}
				if (index < 0 || index > list.size()) throw new IllegalArgumentException("index " + index + " out of range for '" + String.join(".", path.subList(0, i)) + "' (size " + list.size() + ")");
				if (last) {
					if (index == list.size()) list.add(value);
					else list.set(index, value);
					return;
				}
				if (index == list.size()) list.add(new LinkedHashMap<String, Object>());
				Object child = list.get(index);
				if (!(child instanceof Map<?, ?>) && !(child instanceof List<?>)) throw new IllegalArgumentException("'" + String.join(".", path.subList(0, i + 1)) + "' is not an object or array");
				current = child;
			} else {
				throw new IllegalArgumentException("the document root is not an object or array");
			}
		}
	}

	@Override
	public List<Assignment> read(String content) {
		List<Assignment> out = new ArrayList<>();
		flatten(Json.parse(content), new ArrayList<>(), out);
		return out;
	}

	private static void flatten(Object value, List<String> path, List<Assignment> out) {
		if (value instanceof Map<?, ?> map && !map.isEmpty()) {
			for (Map.Entry<?, ?> e : map.entrySet()) {
				path.add((String) e.getKey());
				flatten(e.getValue(), path, out);
				path.removeLast();
			}
		} else if (!path.isEmpty()) {
			out.add(new Assignment(List.copyOf(path), value));
		}
	}
}
