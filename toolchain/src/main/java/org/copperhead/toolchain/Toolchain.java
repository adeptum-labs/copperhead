/*
    Copperhead
    Copyright © 2026 Adam Waldenberg and Adeptum AB

    This program is free software: you can redistribute it and/or modify it under the terms of the
    GNU General Public License as published by the Free Software Foundation, version 3 of the License.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
    even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along with this program.
    If not, see <http://www.gnu.org/licenses/>.
 */

package org.copperhead.toolchain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Finds the assembler and the cruncher, or fetches, checks, compiles and stores them when they are not there yet. */
public final class Toolchain {
	private static final Pattern VERSION =
		Pattern.compile("#define\\s+_VER\\s+\"vasm\\s+([0-9A-Za-z.+-]{1,32})\"");
	private static final int SCANNED_BYTES = 1 << 20;
	private static final String UNKNOWN = "unknown";

	private final ToolStore store;
	private final Fetcher fetcher;
	private final Compiler compiler;
	private final Prerequisites prerequisites;
	private final Function<String, Optional<Path>> onPath;
	private final Path scratch;

	public Toolchain(final ToolStore store, final Fetcher fetcher, final Compiler compiler,
		final Prerequisites prerequisites, final Function<String, Optional<Path>> onPath, final Path scratch) {

		this.store = store;
		this.fetcher = fetcher;
		this.compiler = compiler;
		this.prerequisites = prerequisites;
		this.onPath = onPath;
		this.scratch = scratch;
	}

	public Optional<Path> locate(final PinnedSource source, final Optional<Path> configured,
		final Optional<String> trusted) {

		return configured.filter(path -> Files.isRegularFile(path) && Files.isExecutable(path))
			.or(() -> store.find(source, source.sha256()))
			.or(() -> trustedFor(source, trusted).flatMap(sha256 -> store.find(source, sha256)))
			.or(() -> onPath.apply(source.executable()));
	}

	public Path ensure(final PinnedSource source, final Optional<Path> configured, final Optional<String> trusted,
		final Consumer<String> log) throws ToolchainException, IOException, InterruptedException {

		final Optional<Path> found = locate(source, configured, trusted);

		if (found.isPresent()) {
			return found.get();
		}
		final Optional<String> missing = prerequisites.missing();

		if (missing.isPresent()) {
			throw new ToolchainException(missing.get());
		}
		try (Workspace workspace = new Workspace(Files.createTempDirectory(Files.createDirectories(scratch),
			source.tool()))) {

			final Path work = workspace.path();

			log.accept("fetching " + source.uri());
			final Fetcher.Fetched fetched = fetcher.fetch(source.uri(), work);

			if (!fetched.sha256().equals(source.sha256())
				&& !trustedFor(source, trusted).equals(Optional.of(fetched.sha256()))) {
				throw refusal(source, fetched, work);
			}
			final Path tree = Tarball.extract(fetched.file(), work.resolve("tree"));
			final Path installed = store.install(source, fetched.sha256(),
				compiler.build(tree, source.makeArgs(), source.binary(), log));

			log.accept("installed " + installed);
			return installed;
		}
	}

	/* Only vasm has a trust flow; for Shrinkler a different archive is refused whatever the preferences say. */
	private static Optional<String> trustedFor(final PinnedSource source, final Optional<String> trusted) {
		return trusted.filter(sha256 -> isVasm(source)).map(sha256 -> sha256.toLowerCase(Locale.ROOT));
	}

	private static boolean isVasm(final PinnedSource source) {
		return PinnedSource.VASM.tool().equals(source.tool());
	}

	/* vasm's host replaces its release archive in place, so a different archive is most likely a new release, which
	   the user may accept. Shrinkler is fetched by an unchanging tag, so a different archive there means tampering. */
	private static ToolchainException refusal(final PinnedSource source, final Fetcher.Fetched fetched, final Path work)
		throws IOException {

		if (!isVasm(source)) {
			return new ToolchainException(source.tool() + " " + source.version() + " arrived with sha-256 "
				+ fetched.sha256() + " instead of the pinned one; refusing it, as its tagged release never "
				+ "changes and this may be tampering");
		}
		final Path quarantined = Tarball.extract(fetched.file(), work.resolve("quarantine"));

		return new HashMismatch(source.tool(), version(quarantined), fetched.sha256());
	}

	/* Read from the unpacked sources, which are never built before the user accepts them. A link is not followed
	   and only the start of the file is scanned: the archive is not trusted yet, so neither its size nor the
	   characters of the version it claims are. */
	private static String version(final Path tree) throws IOException {
		final Path source = tree.resolve("vasm.c");

		if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
			return UNKNOWN;
		}
		try (InputStream in = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS)) {
			final Matcher found = VERSION.matcher(new String(in.readNBytes(SCANNED_BYTES),
				StandardCharsets.ISO_8859_1));

			return found.find() ? found.group(1) : UNKNOWN;
		}
	}

	/* A failed cleanup is attached to the failure that made it necessary instead of replacing it. */
	private record Workspace(Path path) implements AutoCloseable {
		@Override
		public void close() throws IOException {
			final List<Path> paths;

			try (Stream<Path> walked = Files.walk(path)) {
				paths = walked.sorted(Comparator.reverseOrder()).toList();
			}
			for (final Path each : paths) {
				Files.delete(each);
			}
		}
	}
}
