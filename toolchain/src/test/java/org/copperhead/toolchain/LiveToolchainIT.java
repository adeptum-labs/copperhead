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
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.copperhead.core.build.Bootblock;
import org.copperhead.core.build.BuildReport;
import org.copperhead.core.build.BuildWarning;
import org.copperhead.core.build.TrackDisk;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.Target;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fetches vasm and Shrinkler from their real hosts, compiles them with the machine's compilers and builds both
 * project templates into disks with them. It needs the network, make, cc and c++, so only the toolchain profile
 * runs it. A failure here after an upstream release usually means the pinned hash needs a look.
 */
public class LiveToolchainIT {
	private static final Duration FETCH = Duration.ofMinutes(2);
	private static final Duration COMPILE = Duration.ofMinutes(5);
	private static final Duration STEP = Duration.ofMinutes(2);
	private static final TrustPrompt REFUSE = (tool, version, sha256) -> false;
	/* The disks stay after the run, for CI to boot them in an emulator. */
	private static final Path DISKS = Path.of("target", "disks");

	private static Path root;
	private static Path vasm;
	private static Path shrinkler;

	@BeforeContainer
	public static void fetchAndCompileTheTools() throws Exception {
		removeDisksOfEarlierRuns();
		root = Files.createTempDirectory("copperhead-live");
		vasm = toolchain().ensure(PinnedSource.VASM, Optional.empty(), Optional.empty(), System.out::println);
		shrinkler = toolchain().ensure(PinnedSource.SHRINKLER, Optional.empty(), Optional.empty(),
			System.out::println);
	}

	private static void removeDisksOfEarlierRuns() throws IOException {
		if (!Files.isDirectory(DISKS)) {
			return;
		}
		try (Stream<Path> kept = Files.list(DISKS)) {
			for (final Path disk : kept.toList()) {
				Files.delete(disk);
			}
		}
	}

	@AfterContainer
	public static void removeTheTools() throws IOException {
		if (root == null) {
			return;
		}
		try (Stream<Path> made = Files.walk(root)) {
			for (final Path path : made.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		}
	}

	@Example
	public void shouldStoreTheToolsUnderTheirPinnedHashes() {
		assertEquals(root.resolve("tools/vasm").resolve(PinnedSource.VASM.sha256()).resolve("vasmm68k_mot"), vasm);
		assertEquals(root.resolve("tools/shrinkler").resolve(PinnedSource.SHRINKLER.sha256()).resolve("Shrinkler"),
			shrinkler);
	}

	@Example
	public void shouldBuildTheDemoCrunchedForTheA500() throws IOException {
		bootable(project("nightrun", ProjectKind.DEMO, Target.A500_OCS, true));
	}

	@Example
	public void shouldBuildTheDemoUncrunchedForTheA500() throws IOException {
		bootable(project("daybreak", ProjectKind.DEMO, Target.A500_OCS, false));
	}

	@Example
	public void shouldBuildTheGameCrunchedForTheA1200() throws IOException {
		bootable(project("shipshape", ProjectKind.GAME, Target.A1200_AGA, true));
	}

	@Example
	public void shouldBuildTheGameUncrunchedForTheA500() throws IOException {
		bootable(project("dogfight", ProjectKind.GAME, Target.A500_OCS, false));
	}

	@Example
	public void shouldReportWhereTheRealAssemblerFoundAnError() throws IOException {
		final ProjectDirectory project = project("broken", ProjectKind.DEMO, Target.A500_OCS, false);

		project.write("main.s", "\tfoo\n");
		final BuildReport report = build(project);

		assertFalse(report.ok());
		assertEquals(List.of(new BuildWarning("src/main.s", 1, "unknown mnemonic <foo>")), report.errors());
	}

	@Example
	public void shouldKeepAnOutputDirectiveInsideTheProject() throws IOException {
		final ProjectDirectory project = project("hostile", ProjectKind.DEMO, Target.A500_OCS, false);

		Files.createDirectories(project.dir().resolve("build/main.x"));
		project.write("main.s", "\toutput .x/../../../escaped.bin\n\trts\n");
		final BuildReport report = build(project);

		try (Stream<Path> made = Files.walk(root)) {
			assertEquals(List.of(), made.filter(path -> "escaped.bin".equals(path.getFileName().toString()))
				.toList());
		}
		assertTrue(report.ok() || !report.errors().isEmpty(), report.log().toString());
	}

	private static void bootable(final ProjectDirectory project) throws IOException {
		final BuildReport report = build(project);

		assertTrue(report.ok(), report.log().toString());
		final boolean crunch = project.file().crunch();
		final byte[] image = Files.readAllBytes(project.adf());
		final byte[] payload = Files.readAllBytes(project.dir().resolve(crunch ? "build/main.shr"
			: "build/main.bin"));
		final long loader = Files.size(project.dir().resolve("build/boot.bin"));

		System.out.printf("%s: program %d bytes, payload %d bytes, boot loader %d bytes%n", project.file().name(),
			Files.size(project.dir().resolve("build/main.bin")), payload.length, loader);
		assertEquals(payload.length, report.bytes());
		assertEquals(TrackDisk.SIZE, image.length);
		assertTrue(Bootblock.valid(Arrays.copyOf(image, Bootblock.SIZE)));
		assertTrue(loader <= Bootblock.SIZE, loader + " bytes");
		assertArrayEquals(payload, Arrays.copyOfRange(image, TrackDisk.PAYLOAD_OFFSET,
			TrackDisk.PAYLOAD_OFFSET + payload.length));
		Files.copy(project.adf(), Files.createDirectories(DISKS).resolve(project.adf().getFileName()),
			StandardCopyOption.REPLACE_EXISTING);
	}

	private static ProjectDirectory project(final String name, final ProjectKind kind, final Target target,
		final boolean crunch) throws IOException {

		final ProjectDirectory created = ProjectDirectory.create(root.resolve("projects"), name, kind, target);

		return created.save(created.file().withCrunch(crunch));
	}

	private static BuildReport build(final ProjectDirectory project) {
		final List<BuildEvent> events = new CopyOnWriteArrayList<>();

		new BuildJob(toolchain(), new BuildRequest(project, Optional.of(vasm), Optional.of(shrinkler),
			Optional.empty()), STEP, REFUSE, events::add).run();
		assertEquals(1, events.stream().filter(BuildEvent.Finished.class::isInstance).count());
		return assertInstanceOf(BuildEvent.Finished.class, events.getLast()).report();
	}

	/* Nothing is taken from the PATH, so the tools are fetched and compiled even where vasm is installed. */
	private static Toolchain toolchain() {
		final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

		return new Toolchain(new ToolStore(root.resolve("tools")), new Fetcher(client, FETCH), new Compiler(COMPILE),
			new Prerequisites(SearchPath.system(), Path.of("/etc/os-release")), program -> Optional.empty(),
			root.resolve("work"));
	}
}
