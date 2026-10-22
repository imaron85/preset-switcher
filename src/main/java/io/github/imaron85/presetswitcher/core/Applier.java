package io.github.imaron85.presetswitcher.core;

import io.github.imaron85.presetswitcher.core.Definitions.Copy;
import io.github.imaron85.presetswitcher.core.Definitions.Merge;
import io.github.imaron85.presetswitcher.core.Definitions.Operation;
import io.github.imaron85.presetswitcher.core.Definitions.ResourcePacks;
import io.github.imaron85.presetswitcher.core.Definitions.When;
import io.github.imaron85.presetswitcher.core.format.FileFormat;
import io.github.imaron85.presetswitcher.core.format.FileFormat.Assignment;
import io.github.imaron85.presetswitcher.core.format.OptionsFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Executes preset operations against the game folder. Never touches anything an operation does not declare. */
public final class Applier {
	public interface Log {
		void info(String message);

		void warn(String message);
	}

	public record Result(List<String> errors, List<String> deferred, List<String> changedFiles) {
	}

	/** Thrown when an operation cannot run yet (a file or wildcard match does not exist); it is retried on the next launch. */
	static final class Unresolved extends Exception {
		Unresolved(String message) {
			super(message);
		}
	}

	private final Path gameDir;
	private final Path definitionsDir;
	private final int dataVersion;
	private final Log log;

	public Applier(Path gameDir, Path definitionsDir, int dataVersion, Log log) {
		this.gameDir = gameDir.toAbsolutePath().normalize();
		this.definitionsDir = definitionsDir.toAbsolutePath().normalize();
		this.dataVersion = dataVersion;
		this.log = log;
	}

	/** @param only operation ids to run, or {@code null} for all */
	public Result apply(List<Operation> ops, Set<String> only) {
		List<String> errors = new ArrayList<>();
		List<String> deferred = new ArrayList<>();
		List<String> changed = new ArrayList<>();
		for (Operation op : ops) {
			if (only != null && !only.contains(op.id())) continue;
			try {
				switch (op) {
					case Merge m -> merge(m, changed);
					case Copy c -> copy(c, changed);
					case ResourcePacks r -> resourcePacks(r, changed);
				}
			} catch (Unresolved e) {
				if (op.optional()) {
					log.info(op.id() + ": skipped (" + e.getMessage() + ")");
				} else {
					log.warn(op.id() + ": deferred to the next launch: " + e.getMessage());
					deferred.add(op.id());
				}
			} catch (Exception e) {
				String msg = op.id() + ": " + e.getClass().getSimpleName() + ": " + e.getMessage();
				log.warn("failed: " + msg);
				errors.add(msg);
			}
		}
		return new Result(errors, deferred, changed);
	}

	// ---------------------------------------------------------------- merge

	private void merge(Merge op, List<String> changed) throws IOException, Unresolved {
		FileFormat format = FileFormat.byId(op.format() != null ? op.format() : FileFormat.inferId(op.file()), dataVersion);
		List<Assignment> assignments = new ArrayList<>();
		if (op.from() != null) {
			Path src = FileOps.resolve(definitionsDir, op.from());
			if (!Files.isRegularFile(src)) throw new IOException("source file " + op.from() + " does not exist in " + definitionsDir);
			assignments.addAll(format.read(Files.readString(src)));
		}
		flatten(op.values(), new ArrayList<>(), format.nestedKeys(), assignments);

		List<Path> targets = new ArrayList<>();
		if (FileOps.isGlob(op.file())) {
			for (Path p : FileOps.glob(gameDir, op.file())) if (Files.isRegularFile(p)) targets.add(p);
			if (targets.isEmpty()) throw new Unresolved("no file matches " + op.file());
		} else {
			Path p = FileOps.resolve(gameDir, op.file());
			if (!Files.exists(p) && !op.createIfMissing()) throw new Unresolved(op.file() + " does not exist yet");
			targets.add(p);
		}
		for (Path target : targets) {
			String original = Files.exists(target) ? Files.readString(target) : null;
			String updated = format.apply(original, assignments);
			if (!updated.equals(original)) {
				FileOps.writeAtomically(target, updated);
				changed.add(FileOps.rel(gameDir, target));
			}
			log.info(op.id() + ": merged " + assignments.size() + " key(s) into " + FileOps.rel(gameDir, target));
			if (format instanceof OptionsFormat && target.getFileName().toString().equals("options.txt")) warnGraphicsPreset(op, updated, assignments);
		}
	}

	/** Options that vanilla's graphics preset (26.x) overwrites at every start unless graphicsPreset is "custom". */
	private static final Set<String> GRAPHICS_PRESET_KEYS = Set.of("renderDistance", "simulationDistance", "particles", "renderClouds",
			"ao", "biomeBlendRadius", "prioritizeChunkUpdates", "mipmapLevels", "entityShadows", "entityDistanceScaling", "menuBackgroundBlurriness",
			"cloudRange", "cutoutLeaves", "improvedTransparency", "weatherRadius", "maxAnisotropyBit", "textureFiltering");

	private void warnGraphicsPreset(Merge op, String options, List<Assignment> assignments) {
		String preset = OptionsFormat.get(options, "graphicsPreset");
		if (preset != null && preset.replace("\"", "").equals("custom")) return;
		List<String> overridden = assignments.stream().map(Assignment::key).filter(GRAPHICS_PRESET_KEYS::contains).toList();
		if (!overridden.isEmpty()) {
			log.warn(op.id() + ": options.txt has graphicsPreset " + (preset == null ? "unset (vanilla default: fancy)" : preset)
					+ "; the game re-applies that preset at every start and overrides " + overridden + ". Set graphicsPreset to \"custom\" in this preset.");
		}
	}

	@SuppressWarnings("unchecked")
	private void flatten(Map<String, Object> values, List<String> prefix, boolean nested, List<Assignment> out) throws Unresolved {
		for (Map.Entry<String, Object> e : values.entrySet()) {
			List<String> path = new ArrayList<>(prefix);
			if (nested) path.addAll(splitPath(e.getKey()));
			else path.add(e.getKey());
			Object v = e.getValue();
			if (v instanceof Map<?, ?> m && !m.isEmpty() && !isDirective(m)) {
				if (!nested) throw new IllegalArgumentException("nested objects are only supported for json and toml files (key '" + e.getKey() + "')");
				flatten((Map<String, Object>) m, path, true, out);
			} else {
				out.add(new Assignment(List.copyOf(path), resolveValue(v)));
			}
		}
	}

	/** Splits a key on dots; {@code \.} is a literal dot and {@code \\} a literal backslash. */
	static List<String> splitPath(String key) {
		List<String> parts = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			if (c == '\\' && i + 1 < key.length()) {
				cur.append(key.charAt(++i));
			} else if (c == '.') {
				parts.add(cur.toString());
				cur.setLength(0);
			} else {
				cur.append(c);
			}
		}
		parts.add(cur.toString());
		return parts;
	}

	private static boolean isDirective(Map<?, ?> m) {
		for (Object k : m.keySet()) if (k instanceof String s && s.startsWith("$")) return true;
		return false;
	}

	/** Resolves value directives: {@code {"$firstMatch": "glob", "prefix": "...", "fallback": value}}. */
	private Object resolveValue(Object v) throws Unresolved {
		if (!(v instanceof Map<?, ?> m) || !isDirective(m)) return v;
		Object pattern = m.get("$firstMatch");
		if (!(pattern instanceof String glob)) throw new IllegalArgumentException("unknown value directive " + m.keySet() + " (supported: $firstMatch)");
		try {
			List<Path> matches = FileOps.glob(gameDir, glob.replace('\\', '/'));
			if (!matches.isEmpty()) {
				Object prefix = m.get("prefix");
				return (prefix instanceof String p ? p : "") + matches.getFirst().getFileName().toString();
			}
		} catch (IOException e) {
			throw new Unresolved("cannot list " + glob + ": " + e.getMessage());
		}
		if (m.containsKey("fallback")) return m.get("fallback");
		throw new Unresolved("nothing matches " + glob);
	}

	// ---------------------------------------------------------------- copy

	private void copy(Copy op, List<String> changed) throws IOException {
		Path src = FileOps.resolve(definitionsDir, op.from());
		Path dst = FileOps.resolve(gameDir, op.to());
		if (!Files.exists(src)) throw new IOException("source " + op.from() + " does not exist in " + definitionsDir);
		int written = 0;
		int total = 1;
		if (Files.isDirectory(src)) {
			List<Path> files;
			try (Stream<Path> walk = Files.walk(src)) {
				files = walk.filter(Files::isRegularFile).sorted().toList();
			}
			total = files.size();
			for (Path f : files) if (copyFile(f, dst.resolve(src.relativize(f).toString()), op.when(), changed)) written++;
		} else if (copyFile(src, dst, op.when(), changed)) {
			written++;
		}
		log.info(op.id() + ": " + op.from() + " -> " + op.to() + " (when " + op.when().name().toLowerCase(java.util.Locale.ROOT) + "): "
				+ written + " of " + total + " file(s) written, the rest already present");
	}

	/** @return whether the file was written */
	private boolean copyFile(Path src, Path dst, When when, List<String> changed) throws IOException {
		if (Files.exists(dst)) {
			if (when == When.MISSING) return false;
			if (Arrays.equals(Files.readAllBytes(src), Files.readAllBytes(dst))) return false;
		}
		FileOps.writeAtomically(dst, Files.readAllBytes(src));
		changed.add(FileOps.rel(gameDir, dst));
		return true;
	}

	// ---------------------------------------------------------------- resource packs

	private void resourcePacks(ResourcePacks op, List<String> changed) throws IOException, Unresolved {
		Path options = FileOps.resolve(gameDir, "options.txt");
		String original = Files.exists(options) ? Files.readString(options, StandardCharsets.UTF_8) : null;
		List<String> packs = stringList(original == null ? null : OptionsFormat.get(original, "resourcePacks"));
		List<String> set = op.set() == null ? null : resolvePacks(op.set());
		List<String> enable = resolvePacks(op.enable());
		List<String> disable = resolvePacks(op.disable());

		if (set != null) packs = new ArrayList<>(set);
		for (String d : disable) packs.removeIf(p -> samePack(p, d));
		for (String e : enable) if (packs.stream().noneMatch(p -> samePack(p, e))) packs.add(e);

		List<Assignment> assignments = List.of(new Assignment(List.of("resourcePacks"), new ArrayList<Object>(packs)));
		String updated = new OptionsFormat(dataVersion).apply(original, assignments);
		if (!updated.equals(original)) {
			FileOps.writeAtomically(options, updated);
			changed.add("options.txt");
		}
		log.info(op.id() + ": resource packs -> " + packs);
	}

	private List<String> resolvePacks(List<Object> entries) throws Unresolved {
		List<String> out = new ArrayList<>();
		for (Object o : entries) {
			Object v = resolveValue(o);
			if (!(v instanceof String s)) throw new IllegalArgumentException("resource pack entries must be strings or $firstMatch directives: " + o);
			out.add(s);
		}
		return out;
	}

	private static boolean samePack(String a, String b) {
		return strip(a).equals(strip(b));
	}

	private static String strip(String id) {
		return id.startsWith("file/") ? id.substring(5) : id;
	}

	private static List<String> stringList(String json) {
		List<String> out = new ArrayList<>();
		if (json == null || json.isBlank()) return out;
		try {
			if (Json.parse(json) instanceof List<?> l) for (Object o : l) if (o instanceof String s) out.add(s);
		} catch (Json.ParseException ignored) {
			// vanilla would discard an unreadable list too
		}
		return out;
	}
}
