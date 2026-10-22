package io.github.imaron85.presetswitcher.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/** File helpers: confined path resolution, globbing, atomic writes and folder hashing. */
public final class FileOps {
	private FileOps() {
	}

	/** Resolves a relative, '/'-separated path inside {@code root}; refuses anything that escapes it. */
	public static Path resolve(Path root, String relative) {
		Path base = root.toAbsolutePath().normalize();
		Path p = base.resolve(relative).normalize();
		if (!p.startsWith(base)) throw new IllegalArgumentException("path escapes " + base + ": " + relative);
		return p;
	}

	public static boolean isGlob(String path) {
		return path.contains("*") || path.contains("?") || path.contains("[") || path.contains("{");
	}

	/** Expands a '/'-separated pattern with glob wildcards per segment. Matches are sorted by path. */
	public static List<Path> glob(Path root, String pattern) throws IOException {
		List<Path> current = List.of(root.toAbsolutePath().normalize());
		String[] segments = pattern.split("/");
		for (int i = 0; i < segments.length; i++) {
			String seg = segments[i];
			List<Path> next = new ArrayList<>();
			if (!isGlob(seg)) {
				for (Path dir : current) {
					Path child = dir.resolve(seg);
					if (Files.exists(child)) next.add(child);
				}
			} else {
				PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + seg);
				for (Path dir : current) {
					if (!Files.isDirectory(dir)) continue;
					try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
						for (Path child : stream) if (matcher.matches(child.getFileName())) next.add(child);
					}
				}
			}
			next.sort(null);
			current = next;
		}
		return current;
	}

	public static void writeAtomically(Path file, String content) throws IOException {
		writeAtomically(file, content.getBytes(StandardCharsets.UTF_8));
	}

	public static void writeAtomically(Path file, byte[] content) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		Path tmp = Files.createTempFile(parent, "." + file.getFileName(), ".tmp");
		try {
			Files.write(tmp, content);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	/** SHA-256 over every file below {@code dir} (relative path + content), independent of OS and traversal order. */
	public static String hashTree(Path dir) throws IOException {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		if (Files.isDirectory(dir)) {
			List<Path> files;
			try (Stream<Path> walk = Files.walk(dir)) {
				files = walk.filter(Files::isRegularFile).sorted((a, b) -> rel(dir, a).compareTo(rel(dir, b))).toList();
			}
			for (Path f : files) {
				md.update(rel(dir, f).getBytes(StandardCharsets.UTF_8));
				md.update((byte) 0);
				// Normalise line endings so a pack exported on Windows hashes like one exported on Linux.
				md.update(new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1).replace("\r\n", "\n").getBytes(StandardCharsets.ISO_8859_1));
				md.update((byte) 0);
			}
		}
		return HexFormat.of().formatHex(md.digest());
	}

	public static String rel(Path root, Path p) {
		return root.relativize(p).toString().replace('\\', '/');
	}
}
