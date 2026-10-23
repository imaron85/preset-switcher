package io.github.imaron85.presetswitcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Player-owned state. It lives outside the pack's config folder ({@code .presetswitcher/state.json} in the game
 * folder) so that a pack update can never ship or overwrite it.
 */
public final class State {
	/** The preset the player chose (or the default one before the first choice). */
	public String selected;
	/** The preset whose operations were last applied to the live files. */
	public String applied;
	/** Hash of the definitions folder at the time of the last apply. */
	public String appliedHash;
	/** A preset chosen in game that is applied on the next launch. */
	public String pending;
	/** Operations that could not be resolved yet (e.g. a file a mod creates on its first run); retried on the next launch. */
	public List<String> deferred = new ArrayList<>();
	/** Whether the player has seen the first-launch chooser. */
	public boolean chooserDone;

	public static State load(Path file) throws IOException {
		State s = new State();
		if (!Files.exists(file)) return s;
		Object root = Json.parse(Files.readString(file));
		if (!(root instanceof Map<?, ?> m)) return s;
		s.selected = string(m.get("selected"));
		s.applied = string(m.get("applied"));
		s.appliedHash = string(m.get("appliedHash"));
		s.pending = string(m.get("pending"));
		if (m.get("deferred") instanceof List<?> l) for (Object o : l) if (o instanceof String str) s.deferred.add(str);
		s.chooserDone = Boolean.TRUE.equals(m.get("chooserDone"));
		return s;
	}

	public void save(Path file) throws IOException {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("_comment", "Preset Switcher state of this installation. Modpacks must never ship this file.");
		m.put("selected", orNull(selected));
		m.put("applied", orNull(applied));
		m.put("appliedHash", orNull(appliedHash));
		m.put("pending", orNull(pending));
		m.put("deferred", new ArrayList<Object>(deferred));
		m.put("chooserDone", chooserDone);
		Files.createDirectories(file.getParent());
		FileOps.writeAtomically(file, Json.write(m, "  ") + "\n");
	}

	private static String string(Object o) {
		return o instanceof String s ? s : null;
	}

	private static Object orNull(String s) {
		return s == null ? Json.NULL : s;
	}
}
