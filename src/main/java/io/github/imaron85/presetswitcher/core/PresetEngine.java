package io.github.imaron85.presetswitcher.core;

import io.github.imaron85.presetswitcher.core.Definitions.Operation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Loader-independent core: decides at startup whether a preset has to be (re-)applied and applies it,
 * and records the player's choice for the next start.
 *
 * <p>Rules: a pending choice is applied; on the very first start the default preset is applied; when the
 * pack-owned definitions changed (pack update) the chosen preset is applied again; otherwise live files are
 * not touched (except for retrying steps that were deferred because their target did not exist yet).
 */
public final class PresetEngine {
	public static final String DEFINITIONS_DIR = "config/presetswitcher";
	public static final String STATE_FILE = ".presetswitcher/state.json";
	private static final String[] DEFINITION_FILES = {"presets.json5", "presets.json"};

	private final Path gameDir;
	private final Path definitionsDir;
	private final Path stateFile;
	private final int dataVersion;
	private final Applier.Log log;

	private Definitions definitions;
	private String loadError;
	private State state = new State();
	private String startupAction;
	private Applier.Result startupResult;

	public PresetEngine(Path gameDir, int dataVersion, Applier.Log log) {
		this.gameDir = gameDir.toAbsolutePath().normalize();
		this.definitionsDir = this.gameDir.resolve(DEFINITIONS_DIR);
		this.stateFile = this.gameDir.resolve(STATE_FILE);
		this.dataVersion = dataVersion;
		this.log = log;
	}

	/** Runs once, before other mods read their configs. Never throws. */
	public void startup() {
		try {
			Path file = null;
			for (String name : DEFINITION_FILES) {
				Path p = definitionsDir.resolve(name);
				if (Files.isRegularFile(p)) {
					file = p;
					break;
				}
			}
			if (file == null) {
				log.info("no " + DEFINITIONS_DIR + "/presets.json5 found; nothing to do");
				return;
			}
			try {
				definitions = Definitions.parse(Files.readString(file));
			} catch (RuntimeException e) {
				loadError = file.getFileName() + ": " + e.getMessage();
				log.warn("invalid preset definitions, presets are disabled: " + loadError);
				return;
			}
			try {
				state = State.load(stateFile);
			} catch (IOException | RuntimeException e) {
				log.warn("unreadable " + STATE_FILE + " (" + e.getMessage() + "); treating this as a first launch");
				state = new State();
			}
			String hash = FileOps.hashTree(definitionsDir);

			String target;
			String reason = null;
			if (state.pending != null && definitions.presets().containsKey(state.pending)) {
				target = state.pending;
				reason = "switching from " + state.applied + " to " + target;
			} else {
				if (state.pending != null) log.warn("pending preset '" + state.pending + "' no longer exists; ignoring it");
				target = state.selected;
				if (target == null || !definitions.presets().containsKey(target)) {
					if (target != null) {
						log.warn("chosen preset '" + target + "' no longer exists; using the default and asking again");
						state.chooserDone = false;
					}
					target = definitions.defaultPreset();
				}
				if (state.applied == null) reason = "first launch, applying '" + target + "'";
				else if (!target.equals(state.applied)) reason = "applying '" + target + "' (was '" + state.applied + "')";
				else if (!hash.equals(state.appliedHash)) reason = "preset definitions changed (pack update), re-applying '" + target + "'";
			}

			List<Operation> ops = new ArrayList<>(definitions.base());
			ops.addAll(definitions.operationsOf(target));
			Applier applier = new Applier(gameDir, definitionsDir, dataVersion, log);
			if (reason != null) {
				log.info(reason);
				startupAction = reason;
				startupResult = applier.apply(ops, null);
				state.applied = target;
				state.appliedHash = hash;
				state.deferred = new ArrayList<>(startupResult.deferred());
			} else if (!state.deferred.isEmpty()) {
				startupAction = "retrying deferred steps " + state.deferred;
				log.info(startupAction);
				startupResult = applier.apply(ops, Set.copyOf(state.deferred));
				state.deferred = new ArrayList<>(startupResult.deferred());
			} else {
				log.info("preset '" + target + "' is up to date; no files touched");
			}
			state.selected = target;
			state.pending = null;
			if (startupResult != null) {
				log.info("changed files: " + new java.util.LinkedHashSet<>(startupResult.changedFiles()) + (startupResult.errors().isEmpty() ? "" : "; " + startupResult.errors().size() + " error(s)"));
			}
			state.save(stateFile);
		} catch (Exception e) {
			loadError = e.getClass().getSimpleName() + ": " + e.getMessage();
			log.warn("preset startup failed: " + loadError);
		}
	}

	public boolean hasDefinitions() {
		return definitions != null;
	}

	/** {@code null} unless the definitions or the startup step failed. */
	public String loadError() {
		return loadError;
	}

	public Definitions definitions() {
		return definitions;
	}

	public State state() {
		return state;
	}

	/** Errors of the operations that ran at this startup (empty if none ran). */
	public List<String> startupErrors() {
		return startupResult == null ? List.of() : startupResult.errors();
	}

	public String startupAction() {
		return startupAction;
	}

	public boolean shouldShowChooser() {
		return definitions != null && definitions.showChooserOnFirstLaunch() && !state.chooserDone && definitions.presets().size() > 1;
	}

	/** The preset that will be active after the next start. */
	public String effectivePreset() {
		return state.pending != null ? state.pending : state.applied;
	}

	public boolean restartPending() {
		return state.pending != null;
	}

	/**
	 * Records the player's choice; it is applied on the next start.
	 *
	 * @return whether a restart is needed
	 */
	public boolean choose(String presetId) throws IOException {
		if (definitions == null || !definitions.presets().containsKey(presetId)) throw new IllegalArgumentException("unknown preset " + presetId);
		state.selected = presetId;
		state.chooserDone = true;
		state.pending = presetId.equals(state.applied) ? null : presetId;
		state.save(stateFile);
		log.info("preset '" + presetId + "' chosen" + (state.pending != null ? "; it is applied on the next start" : " (already active)"));
		return state.pending != null;
	}

	/** Marks the first-launch chooser as seen without changing the preset. */
	public void dismissChooser() throws IOException {
		if (state.chooserDone) return;
		state.chooserDone = true;
		state.save(stateFile);
	}
}
