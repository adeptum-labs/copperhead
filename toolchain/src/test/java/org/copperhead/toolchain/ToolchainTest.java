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
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Stream;
import net.jqwik.api.Assume;
import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ToolchainTest extends ScratchTest {
	private static final Duration PATIENT = Duration.ofMinutes(1);
	private static final String NOT_PINNED = "0".repeat(64);
	private static final String TRUSTED = "1".repeat(64);
	private static final Function<String, Optional<Path>> NOTHING = program -> Optional.empty();
	private static final Function<String, Optional<Path>> BUILD_TOOLS =
		program -> Optional.of(Path.of("/usr/bin", program));
	private static final String MAKEFILE = "all:\n\tprintf '#!/bin/sh\\necho vasm\\n' > vasmm68k_mot\n"
		+ "\tchmod +x vasmm68k_mot\n";

	@Example
	public void shouldLookInThePreferencesThenTheStoreThenThePath() throws IOException {
		final Path configured = program(scratch().resolve("mine/vasm"));
		final Path pinned = program(stored(PinnedSource.VASM.sha256()));
		final Path trusted = program(stored(TRUSTED));
		final Path onPath = program(scratch().resolve("bin/vasmm68k_mot"));
		final Toolchain toolchain = toolchain(idle(), SearchPath.of(onPath.getParent().toString()), BUILD_TOOLS);

		assertEquals(Optional.of(configured), locate(toolchain, configured));
		Files.setPosixFilePermissions(configured, PosixFilePermissions.fromString("rw-r--r--"));
		assertEquals(Optional.of(pinned), locate(toolchain, configured));
		Files.delete(pinned);
		assertEquals(Optional.of(trusted), locate(toolchain, configured));
		Files.delete(trusted);
		assertEquals(Optional.of(onPath), locate(toolchain, configured));
		Files.delete(onPath);
		assertEquals(Optional.empty(), locate(toolchain, configured));
	}

	@Example
	public void shouldFetchCompileAndStoreAToolThatIsNowhereYet() throws Exception {
		final byte[] archive = vasmArchive("2.0f");
		final String sha256 = TarFixture.sha256(archive);
		final List<String> log = new CopyOnWriteArrayList<>();
		final PinnedSource source;
		final Path installed;

		try (LocalServer server = new LocalServer().serve("/vasm.tar.gz", archive)) {
			source = vasm(server, sha256);
			installed = toolchain(server, BUILD_TOOLS).ensure(source, Optional.empty(), Optional.empty(),
				log::add);

			assertEquals(stored(sha256), installed);
			assertEquals("#!/bin/sh\necho vasm\n", Files.readString(installed));
			assertEquals("fetching " + server.uri("/vasm.tar.gz"), log.getFirst());
			assertTrue(log.contains("make CPU=m68k SYNTAX=mot"), log.toString());
			assertEquals("installed " + installed, log.getLast());
			assertEquals(List.of(), listed(scratch().resolve("work")));
		}
		assertEquals(installed, toolchain(idle(), NOTHING, BUILD_TOOLS).ensure(source, Optional.empty(),
			Optional.empty(), log::add));
	}

	@Example
	public void shouldAskAboutAVasmReleaseOtherThanThePinnedOne() throws Exception {
		final byte[] archive = vasmArchive("2.0g", marking(marker()));
		final List<String> log = new CopyOnWriteArrayList<>();

		try (LocalServer server = new LocalServer().serve("/vasm.tar.gz", archive)) {
			final HashMismatch mismatch = assertThrows(HashMismatch.class, () -> toolchain(server, BUILD_TOOLS)
				.ensure(vasm(server, NOT_PINNED), Optional.empty(), Optional.empty(), log::add));

			assertEquals("vasm", mismatch.tool());
			assertEquals("2.0g", mismatch.version());
			assertEquals(TarFixture.sha256(archive), mismatch.found());
			assertTrue(Files.notExists(stored(TarFixture.sha256(archive))));
			assertEquals(List.of(), listed(scratch().resolve("work")));
			assertEquals(List.of("fetching " + server.uri("/vasm.tar.gz")), log);
			assertTrue(Files.notExists(marker()), "the mismatched archive's Makefile ran");
		}
	}

	@Example
	public void shouldReadNoVersionThatIsLongOrHoldsOddCharacters() throws Exception {
		assertEquals("unknown", mismatchedVersion("9".repeat(33)));
		assertEquals("unknown", mismatchedVersion("2.0\u0007g"));
		assertEquals("unknown", mismatchedVersion("2.0\ng"));
		assertEquals("9".repeat(32), mismatchedVersion("9".repeat(32)));
	}

	@Example
	public void shouldBuildAVasmReleaseTheUserTrusted() throws Exception {
		final byte[] archive = vasmArchive("2.0g");
		final String found = TarFixture.sha256(archive);

		try (LocalServer server = new LocalServer().serve("/vasm.tar.gz", archive)) {
			assertEquals(stored(found), toolchain(server, BUILD_TOOLS).ensure(vasm(server, NOT_PINNED),
				Optional.empty(), Optional.of(found), line -> { }));
		}
	}

	@Example
	public void shouldRefuseAShrinklerArchiveOtherThanThePinnedOne() throws Exception {
		try (LocalServer server = new LocalServer().serve("/shrinkler.tar.gz", vasmArchive("2.0f"))) {
			final PinnedSource shrinkler = new PinnedSource("shrinkler", "4.7", server.uri("/shrinkler.tar.gz"),
				NOT_PINNED, List.of(), "build/native/Shrinkler");
			final ToolchainException refused = assertThrows(ToolchainException.class, () -> toolchain(server,
				BUILD_TOOLS).ensure(shrinkler, Optional.empty(), Optional.empty(), line -> { }));

			assertEquals(ToolchainException.class, refused.getClass());
			assertTrue(refused.getMessage().contains("tampering"), refused.getMessage());
		}
	}

	@Example
	public void shouldNeverLetATrustedHashOverrideAShrinklerRefusal() throws Exception {
		final byte[] archive = vasmArchive("2.0f", marking(marker()));
		final String found = TarFixture.sha256(archive);

		try (LocalServer server = new LocalServer().serve("/shrinkler.tar.gz", archive)) {
			final PinnedSource shrinkler = new PinnedSource("shrinkler", "4.7", server.uri("/shrinkler.tar.gz"),
				NOT_PINNED, List.of(), "vasmm68k_mot");
			final ToolchainException refused = assertThrows(ToolchainException.class, () -> toolchain(server,
				BUILD_TOOLS).ensure(shrinkler, Optional.empty(), Optional.of(found), line -> { }));

			assertTrue(refused.getMessage().contains("tampering"), refused.getMessage());
			assertTrue(Files.notExists(marker()), "the refused archive's Makefile ran");
			assertTrue(Files.notExists(scratch().resolve("tools/shrinkler")));
		}
	}

	@Example
	public void shouldIgnoreATrustedHashWhenLookingUpAShrinkler() throws IOException {
		new ToolStore(scratch().resolve("tools")).install(PinnedSource.SHRINKLER, TRUSTED,
			program(scratch().resolve("built/Shrinkler")));

		assertEquals(Optional.empty(), toolchain(idle(), NOTHING, BUILD_TOOLS).locate(PinnedSource.SHRINKLER,
			Optional.empty(), Optional.of(TRUSTED)));
	}

	@Example
	public void shouldTakeATrustedHashInCapitals() throws Exception {
		final byte[] archive = vasmArchive("2.0g");
		final String found = TarFixture.sha256(archive);
		final String capitals = found.toUpperCase(Locale.ROOT);

		assertNotEquals(found, capitals);
		try (LocalServer server = new LocalServer().serve("/vasm.tar.gz", archive)) {
			assertEquals(stored(found), toolchain(server, BUILD_TOOLS).ensure(vasm(server, NOT_PINNED),
				Optional.empty(), Optional.of(capitals), line -> { }));
		}
		assertEquals(Optional.of(stored(found)), toolchain(idle(), NOTHING, BUILD_TOOLS).locate(
			PinnedSource.VASM, Optional.empty(), Optional.of(capitals)));
	}

	@Example
	public void shouldKeepTheBuildFailureWhenTheCleanupFailsToo() throws Exception {
		Assume.that(!"root".equals(System.getProperty("user.name")));
		final byte[] archive = vasmArchive("2.0f", "all:\n\tmkdir locked\n\ttouch locked/file\n"
			+ "\tchmod 555 locked\n\tfalse\n");

		try (LocalServer server = new LocalServer().serve("/vasm.tar.gz", archive)) {
			final PinnedSource source = vasm(server, TarFixture.sha256(archive));
			final IOException failure = assertThrows(IOException.class, () -> toolchain(server, BUILD_TOOLS)
				.ensure(source, Optional.empty(), Optional.empty(), line -> { }));

			assertTrue(failure.getMessage().startsWith("make failed"), failure.getMessage());
			assertEquals(1, failure.getSuppressed().length);
		} finally {
			writable(scratch().resolve("work"));
		}
	}

	@Example
	public void shouldSayWhatToInstallBeforeFetchingAnything() throws Exception {
		Files.writeString(scratch().resolve("os-release"), "ID=ubuntu\nID_LIKE=debian\n");

		try (LocalServer server = new LocalServer()) {
			final ToolchainException missing = assertThrows(ToolchainException.class, () -> toolchain(server,
				NOTHING).ensure(vasm(server, NOT_PINNED), Optional.empty(), Optional.empty(), line -> { }));

			assertEquals("make, cc, c++ not found; run sudo apt install build-essential", missing.getMessage());
		}
	}

	private Path stored(final String sha256) {
		return scratch().resolve("tools/vasm").resolve(sha256).resolve("vasmm68k_mot");
	}

	private Optional<Path> locate(final Toolchain toolchain, final Path configured) {
		return toolchain.locate(PinnedSource.VASM, Optional.of(configured), Optional.of(TRUSTED));
	}

	private Toolchain toolchain(final LocalServer server, final Function<String, Optional<Path>> buildTools) {
		return toolchain(new Fetcher(server.client(), PATIENT), NOTHING, buildTools);
	}

	private Toolchain toolchain(final Fetcher fetcher, final Function<String, Optional<Path>> onPath,
		final Function<String, Optional<Path>> buildTools) {

		return new Toolchain(new ToolStore(scratch().resolve("tools")), fetcher, new Compiler(PATIENT),
			new Prerequisites(buildTools, scratch().resolve("os-release")), onPath, scratch().resolve("work"));
	}

	private static Fetcher idle() {
		return new Fetcher(HttpClient.newHttpClient(), PATIENT);
	}

	private String mismatchedVersion(final String version) throws Exception {
		try (LocalServer server = new LocalServer().serve("/vasm.tar.gz", vasmArchive(version))) {
			return assertThrows(HashMismatch.class, () -> toolchain(server, BUILD_TOOLS).ensure(
				vasm(server, NOT_PINNED), Optional.empty(), Optional.empty(), line -> { })).version();
		}
	}

	/* Outside every extracted tree, so it shows whether a build ran at all. */
	private Path marker() {
		return scratch().resolve("marker");
	}

	private static String marking(final Path marker) {
		return "all:\n\ttouch " + marker + "\n" + MAKEFILE.substring("all:\n".length());
	}

	private static void writable(final Path folder) throws IOException {
		try (Stream<Path> made = Files.walk(folder)) {
			for (final Path path : made.filter(Files::isDirectory).toList()) {
				Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
			}
		}
	}

	private static PinnedSource vasm(final LocalServer server, final String sha256) {
		return new PinnedSource("vasm", "2.0f", server.uri("/vasm.tar.gz"), sha256,
			List.of("CPU=m68k", "SYNTAX=mot"), "vasmm68k_mot");
	}

	private static byte[] vasmArchive(final String version) throws IOException {
		return vasmArchive(version, MAKEFILE);
	}

	private static byte[] vasmArchive(final String version, final String makefile) throws IOException {
		return new TarFixture().directory("vasm").file("vasm/Makefile", makefile)
			.file("vasm/vasm.c", "#define _VER \"vasm " + version + "\"\n").bytes();
	}

	private static Path program(final Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, "#!/bin/sh\n");
		Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
		return file;
	}
}
