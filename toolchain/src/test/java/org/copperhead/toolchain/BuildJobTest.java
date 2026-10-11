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
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.stream.IntStream;
import net.jqwik.api.Example;
import org.copperhead.core.build.Bootblock;
import org.copperhead.core.build.BuildLine;
import org.copperhead.core.build.BuildReport;
import org.copperhead.core.build.BuildWarning;
import org.copperhead.core.build.Tone;
import org.copperhead.core.build.TrackDisk;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.Target;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuildJobTest extends ScratchTest {
	private static final Duration PATIENT = Duration.ofMinutes(1);
	private static final Duration BRIEF = Duration.ofSeconds(1);
	private static final Duration SETTLE = Duration.ofSeconds(10);
	private static final TrustPrompt REFUSE = (tool, version, sha256) -> false;
	private static final String WARNING = "warning 1007 in line 3 of \"src/main.s\": unused label";
	private static final List<BuildWarning> CANCELLED_ONLY = List.of(new BuildWarning("", 0, "build cancelled"));
	private static final String SHORT_ERROR = "error 2 in line 4 of \"src/main.s\": unknown mnemonic <foo>";
	private static final String ERROR = "error 2 in line 4 of \"src/main.s\": unknown mnemonic <foo>\n>\tfoo";
	/* Past what one array can hold, so a build that reads it whole fails however much memory it has. */
	private static final long HUGE = 3L << 30;

	private final List<BuildEvent> events = new CopyOnWriteArrayList<>();

	@Example
	public void shouldAssembleCrunchAndWriteABootableDisk() throws Exception {
		final ProjectDirectory project = demo();
		final Path vasm = StubTools.vasm(scratch().resolve("bin"), 2048, 300, WARNING);
		final Path shrinkler = StubTools.shrinkler(scratch().resolve("bin"), 700);

		final BuildReport report = build(project, vasm, shrinkler);

		assertTrue(report.ok(), report.log().toString());
		assertEquals("nightrun.adf", report.adf());
		assertEquals(700, report.bytes());
		assertEquals(List.of(new BuildWarning("src/main.s", 3, "unused label")), report.warnings());
		assertEquals(List.of("-quiet -m68000 -Fbin -DLOAD=$20000 -o build/.job/main.bin src/main.s",
			"-quiet -nocompdir -m68000 -Fbin -DLOAD=$20000 -DSTAGE=$20800 -DCRUNCHED=1 -DPAYLOAD_LEN=1024 "
				+ "-o ../.job/boot.bin bootblock.s"), jobCalls(vasm));
		assertEquals(List.of("-d -9 -p build/main.bin build/main.shr"), StubTools.calls(shrinkler));
		assertTrue(report.log().contains(new BuildLine("crunched 700 bytes", Tone.PLAIN)));
		assertEquals(2, Collections.frequency(report.log(), new BuildLine(WARNING, Tone.WARN)));
		assertEquals(new BuildLine("adf nightrun.adf  880K · 700 bytes · bootblock checksum ok", Tone.OK),
			report.log().getLast());

		final byte[] image = Files.readAllBytes(project.adf());

		assertEquals(TrackDisk.SIZE, image.length);
		assertTrue(Bootblock.valid(Arrays.copyOf(image, Bootblock.SIZE)));
		assertTrue(Files.isRegularFile(project.dir().resolve("build/boot/ShrinklerDecompress.S")));
		assertEquals(report.log().size() + 1, events.size());
		assertEquals(report.log(), events.stream().filter(BuildEvent.Line.class::isInstance)
			.map(event -> ((BuildEvent.Line) event).line()).toList());
	}

	@Example
	public void shouldAssembleForTheTargetAndLeaveCrunchingOutWhenAsked() throws Exception {
		final ProjectDirectory created = demo();
		final ProjectDirectory project = created.save(created.file().withCrunch(false).withSymbols(true)
			.withTarget(Target.A1200_AGA));
		final Path vasm = StubTools.vasm(scratch().resolve("bin"), 2000, 300);
		final Path shrinkler = StubTools.failingShrinkler(scratch().resolve("bin"));

		final BuildReport report = build(project, vasm, shrinkler);

		assertTrue(report.ok(), report.log().toString());
		assertEquals(2000, report.bytes());
		assertEquals(List.of("-quiet -m68020 -Fbin -DLOAD=$20000 -L build/.job/main.lst -o build/.job/main.bin "
				+ "src/main.s",
			"-quiet -nocompdir -m68000 -Fbin -DLOAD=$20000 -DSTAGE=$207D0 -DCRUNCHED=0 -DPAYLOAD_LEN=2048 "
				+ "-o ../.job/boot.bin bootblock.s"), jobCalls(vasm));
		assertEquals(List.of(), StubTools.calls(shrinkler));
	}

	@Example
	public void shouldKeepTheLastGoodDiskWhenTheSourceDoesNotAssemble() throws Exception {
		final ProjectDirectory project = demo();

		Files.writeString(project.adf(), "the last good disk");
		final BuildReport report = build(project, StubTools.failingVasm(scratch().resolve("bin"), ERROR),
			StubTools.shrinkler(scratch().resolve("bin"), 700));

		assertFalse(report.ok());
		assertEquals("", report.adf());
		assertEquals(List.of(new BuildWarning("src/main.s", 4, "unknown mnemonic <foo>")), report.errors());
		assertTrue(report.log().contains(new BuildLine("error 2 in line 4 of \"src/main.s\": unknown mnemonic <foo>",
			Tone.ERR)));
		assertEquals("the last good disk", Files.readString(project.adf()));
	}

	@Example
	public void shouldStopWhenTheCruncherFails() throws Exception {
		final BuildReport report = build(demo(), StubTools.vasm(scratch().resolve("bin"), 2048, 300),
			StubTools.failingShrinkler(scratch().resolve("bin")));

		assertEquals(List.of(new BuildWarning("", 0, "Shrinkler stopped with exit code 1")), report.errors());
		assertTrue(report.log().contains(new BuildLine("Error while reading file build/main.bin", Tone.PLAIN)));
	}

	@Example
	public void shouldRefuseAProgramThatDoesNotFitInChipRam() throws Exception {
		final ProjectDirectory created = demo();
		final ProjectDirectory project = created.save(created.file().withCrunch(false));

		final BuildReport report = build(project, StubTools.vasm(scratch().resolve("bin"), 400_000, 300),
			StubTools.shrinkler(scratch().resolve("bin"), 700));

		assertEquals(List.of(new BuildWarning("", 0, "program needs 531456 bytes of chip RAM, A500 has 524288")),
			report.errors());
		assertFalse(Files.exists(project.adf()));
	}

	@Example
	public void shouldRefuseAProgramLargerThanChipRamBeforeReadingIt() throws Exception {
		final Path shrinkler = StubTools.shrinkler(scratch().resolve("bin"), 700);
		final BuildReport report = build(demo(), StubTools.sparseVasm(scratch().resolve("bin"), HUGE), shrinkler);

		assertEquals(List.of(new BuildWarning("", 0, "build/main.bin is " + HUGE
			+ " bytes, more than the 524288 bytes of chip RAM the A500 has")), report.errors());
		assertEquals(List.of(), StubTools.calls(shrinkler));
	}

	@Example
	public void shouldFinishWithAReportWhenTheBuildRunsOutOfMemory() throws Exception {
		final Toolchain starved = toolchain(new Fetcher(HttpClient.newHttpClient(), PATIENT), program -> {
			throw new OutOfMemoryError("Java heap space");
		});

		new BuildJob(starved, new BuildRequest(demo(), Optional.empty(), Optional.empty(), Optional.empty()),
			PATIENT, REFUSE, events::add).run();

		assertEquals(List.of(new BuildWarning("", 0, "build failed: OutOfMemoryError: Java heap space")),
			finished().errors());
	}

	@Example
	public void shouldKeepTheUnpackersStackInsideChipRam() throws Exception {
		final BuildReport report = build(demo(), StubTools.vasm(scratch().resolve("bin"), 391_000, 300),
			StubTools.shrinkler(scratch().resolve("bin"), 700));

		assertEquals(List.of(new BuildWarning("", 0, "program needs 527192 bytes of chip RAM, A500 has 524288")),
			report.errors());
	}

	@Example
	public void shouldStageAnOddSizedProgramOnALongWord() throws Exception {
		final Path vasm = StubTools.vasm(scratch().resolve("bin"), 2049, 300);

		assertTrue(build(demo(), vasm, StubTools.shrinkler(scratch().resolve("bin"), 700)).ok());
		assertTrue(StubTools.calls(vasm).getLast().contains("-DSTAGE=$20804 "), StubTools.calls(vasm).toString());
	}

	@Example
	public void shouldRefuseABootLoaderThatOutgrowsItsBlock() throws Exception {
		final BuildReport report = build(demo(), StubTools.vasm(scratch().resolve("bin"), 2048, 1100),
			StubTools.shrinkler(scratch().resolve("bin"), 700));

		assertTrue(report.errors().getFirst().message().startsWith("bootblock too large"),
			report.errors().toString());
	}

	@Example
	public void shouldKillTheRunningToolWhenCancelled() throws Exception {
		final Path vasm = StubTools.sleeping(scratch().resolve("bin"), "vasm");
		final Path pids = vasm.resolveSibling("vasm.pid");
		final BuildJob job = job(demo(), vasm, StubTools.shrinkler(scratch().resolve("bin"), 700), REFUSE);
		final Thread thread = Thread.ofVirtual().start(job);

		while (Files.notExists(pids) || Files.readString(pids).isBlank()) {
			Thread.sleep(BRIEF.dividedBy(20));
		}
		job.cancel();
		thread.join(SETTLE);

		assertEquals(CANCELLED_ONLY, finished().errors());
		for (final String pid : Files.readString(pids).trim().split(" ")) {
			assertTrue(gone(Long.parseLong(pid)), pid);
		}
	}

	@Example
	public void shouldGiveUpOnAToolThatRunsTooLong() throws Exception {
		final Instant start = Instant.now();
		final BuildJob job = new BuildJob(toolchain(new Fetcher(HttpClient.newHttpClient(), PATIENT)),
			new BuildRequest(demo(), Optional.of(StubTools.sleeping(scratch().resolve("bin"), "vasm")),
				Optional.of(StubTools.shrinkler(scratch().resolve("bin"), 700)), Optional.empty()),
			BRIEF, REFUSE, events::add);

		job.run();

		assertEquals(List.of(new BuildWarning("", 0, "vasm timed out after 1 s")), finished().errors());
		assertTrue(Duration.between(start, Instant.now()).compareTo(SETTLE) < 0);
	}

	@Example
	public void shouldBuildWithAVasmReleaseTheUserAccepts() throws Exception {
		final byte[] release = vasmRelease();
		final List<String> asked = new CopyOnWriteArrayList<>();
		final TrustPrompt accept = (tool, version, sha256) -> {
			asked.add(tool + " " + version + " " + sha256);
			return true;
		};

		try (LocalServer server = new LocalServer().serve("/vasm/release/vasm.tar.gz", release)) {
			final Path shrinkler = StubTools.shrinkler(scratch().resolve("bin"), 700);
			final BuildRequest request = new BuildRequest(demo(), Optional.empty(), Optional.of(shrinkler),
				Optional.empty());
			final BuildJob job = new BuildJob(toolchain(new Fetcher(server.proxy(), PATIENT)), request, PATIENT,
				accept, events::add);

			job.run();

			assertTrue(finished().ok(), finished().log().toString());
			assertEquals(List.of("vasm 2.0g " + TarFixture.sha256(release)), asked);
			assertEquals(Optional.of(TarFixture.sha256(release)), job.trustAccepted());
			assertTrue(Files.isExecutable(scratch().resolve("tools/vasm").resolve(TarFixture.sha256(release))
				.resolve("vasmm68k_mot")));
		}
	}

	@Example
	public void shouldStopWhenTheUserRefusesAVasmRelease() throws Exception {
		final byte[] release = vasmRelease();

		try (LocalServer server = new LocalServer().serve("/vasm/release/vasm.tar.gz", release)) {
			final BuildJob job = new BuildJob(toolchain(new Fetcher(server.proxy(), PATIENT)),
				new BuildRequest(demo(), Optional.empty(), Optional.empty(), Optional.empty()), PATIENT,
				REFUSE, events::add);

			job.run();

			assertEquals(List.of(new BuildWarning("", 0, "refused vasm 2.0g (sha-256 "
				+ TarFixture.sha256(release) + ")")), finished().errors());
			assertEquals(Optional.empty(), job.trustAccepted());
			assertTrue(Files.notExists(scratch().resolve("tools/vasm")));
		}
	}

	@Example
	public void shouldDropBlankOutputOfTheAssemblers() throws Exception {
		final BuildReport report = build(demo(), StubTools.vasm(scratch().resolve("bin"), 2048, 300, "\n"),
			StubTools.shrinkler(scratch().resolve("bin"), 700));

		assertTrue(report.ok(), report.log().toString());
		assertFalse(report.log().contains(new BuildLine("", Tone.PLAIN)), report.log().toString());
	}

	@Example
	public void shouldAssembleIntoAFolderOfItsOwnThatIsGoneAfterwards() throws Exception {
		final ProjectDirectory project = demo();
		final Path vasm = StubTools.vasm(scratch().resolve("bin"), 2048, 300);

		assertTrue(build(project, vasm, StubTools.shrinkler(scratch().resolve("bin"), 700)).ok());
		final List<String> folders = StubTools.folders(vasm);
		final List<Path> outputs = StubTools.calls(vasm).stream()
			.map(call -> Path.of(call.replaceAll("^.* -o (\\S+) .*$", "$1"))).toList();
		final List<Path> jobs = IntStream.range(0, outputs.size()).mapToObj(call -> Path.of(folders.get(call))
			.resolve(outputs.get(call)).normalize().getParent()).distinct().toList();

		assertEquals(1, jobs.size(), jobs.toString());
		assertTrue(jobs.getFirst().toString().matches(".*/build/\\.job-\\w+"), jobs.toString());
		assertEquals(List.of(), jobFolders(project));
	}

	/* vasm takes an include from its working folder before any other, so the boot loader is assembled in the boot
	   folder, where the only ShrinklerDecompress.S is the one the build just copied there. */
	@Example
	public void shouldAssembleTheBootLoaderInTheBootFolderAwayFromFilesPlantedInTheProject() throws Exception {
		final ProjectDirectory project = demo();
		final Path vasm = StubTools.vasm(scratch().resolve("bin"), 2048, 300);

		Files.writeString(project.dir().resolve("ShrinklerDecompress.S"), "\tfail \"planted\"\n");

		assertTrue(build(project, vasm, StubTools.shrinkler(scratch().resolve("bin"), 700)).ok());
		assertEquals(List.of(project.dir().toRealPath().toString(),
			project.dir().resolve("build/boot").toRealPath().toString()), StubTools.folders(vasm));
		assertTrue(StubTools.calls(vasm).getLast().startsWith("-quiet -nocompdir "));
		assertTrue(StubTools.calls(vasm).getLast().endsWith(" bootblock.s"));
	}

	@Example
	public void shouldRemoveItsFolderAfterAFailedBuild() throws Exception {
		final ProjectDirectory project = demo();

		build(project, StubTools.failingVasm(scratch().resolve("bin"), ERROR),
			StubTools.shrinkler(scratch().resolve("bin"), 700));

		assertEquals(List.of(), jobFolders(project));
	}

	@Example
	public void shouldRemoveItsFolderAfterACancelledBuild() throws Exception {
		final ProjectDirectory project = demo();
		final Path vasm = StubTools.sleeping(scratch().resolve("bin"), "vasm");
		final Path pids = vasm.resolveSibling("vasm.pid");
		final BuildJob job = job(project, vasm, StubTools.shrinkler(scratch().resolve("bin"), 700), REFUSE);
		final Thread thread = Thread.ofVirtual().start(job);

		while (Files.notExists(pids) || Files.readString(pids).isBlank()) {
			Thread.sleep(BRIEF.dividedBy(20));
		}
		job.cancel();
		thread.join(SETTLE);

		assertEquals(List.of(), jobFolders(project));
	}

	@Example
	public void shouldRefuseWhatTheAssemblerLeavesInItsFolder() throws Exception {
		final ProjectDirectory project = demo();
		final BuildReport report = build(project, StubTools.straying(scratch().resolve("bin")),
			StubTools.shrinkler(scratch().resolve("bin"), 700));

		assertTrue(report.errors().getFirst().message().matches("the tool left build/\\.job-\\w+/stray"),
			report.errors().toString());
		assertEquals(List.of(), jobFolders(project));
		assertFalse(Files.exists(project.adf()));
	}

	private List<Path> jobFolders(final ProjectDirectory project) throws IOException {
		return listed(project.dir().resolve("build")).stream()
			.filter(path -> path.getFileName().toString().startsWith(".job-")).toList();
	}

	private static List<String> jobCalls(final Path script) throws IOException {
		return StubTools.calls(script).stream().map(call -> call.replaceAll("\\.job-\\w+", ".job")).toList();
	}

	@Example
	public void shouldNotWriteThroughLinksPlantedInTheBuildFolder() throws Exception {
		final ProjectDirectory created = demo();
		final ProjectDirectory project = created.save(created.file().withSymbols(true));
		final Path build = Files.createDirectories(project.dir().resolve("build/boot")).getParent();
		final List<Path> planted = List.of(build.resolve("main.bin"), build.resolve("main.shr"),
			build.resolve("main.lst"), build.resolve("boot.bin"), build.resolve("nightrun.adf.part"),
			build.resolve("boot/bootblock.s"), build.resolve("boot/ShrinklerDecompress.S"));

		final List<Path> victims = planted.stream().map(this::victimOf).toList();
		final BuildReport report = build(project, StubTools.vasm(scratch().resolve("bin"), 2048, 300),
			StubTools.shrinkler(scratch().resolve("bin"), 700));

		assertTrue(report.ok(), report.log().toString());
		for (final Path victim : victims) {
			assertEquals("victim", Files.readString(victim), victim.toString());
		}
		for (final Path output : List.of(build.resolve("main.bin"), build.resolve("main.shr"),
			build.resolve("boot.bin"), build.resolve("boot/bootblock.s"))) {

			assertTrue(Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS), output.toString());
		}
		assertTrue(Files.isRegularFile(project.adf(), LinkOption.NOFOLLOW_LINKS));
	}

	@Example
	public void shouldRefuseABuildFolderThatIsALink() throws Exception {
		final ProjectDirectory project = demo();
		final Path outside = Files.createDirectories(scratch().resolve("outside"));
		final Path vasm = StubTools.vasm(scratch().resolve("bin"), 2048, 300);

		Files.delete(project.dir().resolve("build"));
		Files.createSymbolicLink(project.dir().resolve("build"), outside);
		final BuildReport report = build(project, vasm, StubTools.shrinkler(scratch().resolve("bin"), 700));

		assertEquals(List.of(new BuildWarning("", 0, "build folder is a link")), report.errors());
		assertEquals(List.of(), StubTools.calls(vasm));
		assertEquals(List.of(), listed(outside));
	}

	@Example
	public void shouldRefuseABootFolderThatIsALink() throws Exception {
		final ProjectDirectory project = demo();
		final Path outside = Files.createDirectories(scratch().resolve("outside"));
		final Path vasm = StubTools.vasm(scratch().resolve("bin"), 2048, 300);

		Files.createSymbolicLink(project.dir().resolve("build/boot"), outside);
		final BuildReport report = build(project, vasm, StubTools.shrinkler(scratch().resolve("bin"), 700));

		assertEquals(List.of(new BuildWarning("", 0, "build/boot folder is a link")), report.errors());
		assertEquals(List.of(), StubTools.calls(vasm));
		assertEquals(List.of(), listed(outside));
	}

	@Example
	public void shouldNotTakeAnEarlierProgramForTheOneJustAssembled() throws Exception {
		final ProjectDirectory project = demo();

		Files.write(project.dir().resolve("build/main.bin"), new byte[2048]);
		final BuildReport report = build(project, StubTools.silentVasm(scratch().resolve("bin")),
			StubTools.shrinkler(scratch().resolve("bin"), 700));

		assertEquals(List.of(new BuildWarning("", 0, "build/main.bin was not written")), report.errors());
		assertFalse(Files.exists(project.adf()));
	}

	@Example
	public void shouldFinishOnceWhenTheWatcherFailsOnTheLines() throws Exception {
		final List<BuildEvent> finished = new CopyOnWriteArrayList<>();
		final BuildJob job = new BuildJob(toolchain(new Fetcher(HttpClient.newHttpClient(), PATIENT)),
			new BuildRequest(demo(), Optional.of(StubTools.failingVasm(scratch().resolve("bin"), ERROR)),
				Optional.of(StubTools.shrinkler(scratch().resolve("bin"), 700)), Optional.empty()),
			PATIENT, REFUSE, event -> {
				if (event instanceof BuildEvent.Finished) {
					finished.add(event);
				} else {
					throw new IllegalStateException("watcher gone");
				}
			});

		job.run();

		assertEquals(1, finished.size());
		assertFalse(((BuildEvent.Finished) finished.getFirst()).report().ok());
	}

	@Example
	public void shouldNotThrowWhenTheWatcherAlwaysFails() throws Exception {
		final List<BuildEvent> seen = new CopyOnWriteArrayList<>();
		final BuildJob job = new BuildJob(toolchain(new Fetcher(HttpClient.newHttpClient(), PATIENT)),
			new BuildRequest(demo(), Optional.of(StubTools.vasm(scratch().resolve("bin"), 2048, 300)),
				Optional.of(StubTools.shrinkler(scratch().resolve("bin"), 700)), Optional.empty()),
			PATIENT, REFUSE, event -> {
				seen.add(event);
				throw new IllegalStateException("watcher gone");
			});

		job.run();

		assertEquals(1, seen.stream().filter(BuildEvent.Finished.class::isInstance).count());
		assertInstanceOf(BuildEvent.Finished.class, seen.getLast());
	}

	@Example
	public void shouldReportACancelAfterTheAssemblerFoundErrors() throws Exception {
		final Path vasm = StubTools.failingThenSleeping(scratch().resolve("bin"), SHORT_ERROR);
		final BuildJob job = job(demo(), vasm, StubTools.shrinkler(scratch().resolve("bin"), 700), REFUSE);
		final Thread thread = Thread.ofVirtual().start(job);

		while (events.stream().noneMatch(event -> event instanceof BuildEvent.Line(BuildLine line)
			&& line.tone() == Tone.ERR)) {

			Thread.sleep(BRIEF.dividedBy(20));
		}
		job.cancel();
		thread.join(SETTLE);

		assertEquals(List.of(new BuildWarning("src/main.s", 4, "unknown mnemonic <foo>"),
			new BuildWarning("", 0, "build cancelled")), finished().errors());
	}

	@Example
	public void shouldNotStartWhenCancelledBeforeItRuns() throws Exception {
		final Path vasm = StubTools.vasm(scratch().resolve("bin"), 2048, 300);
		final BuildJob job = job(demo(), vasm, StubTools.shrinkler(scratch().resolve("bin"), 700), REFUSE);

		job.cancel();
		job.run();

		assertEquals(CANCELLED_ONLY, finished().errors());
		assertEquals(List.of(), StubTools.calls(vasm));
	}

	@Example
	public void shouldReportACancelWhileTheUserIsAskedAndRefuses() throws Exception {
		try (LocalServer server = new LocalServer().serve("/vasm/release/vasm.tar.gz", vasmRelease())) {
			assertEquals(CANCELLED_ONLY, cancelledDuringPrompt(server, false));
		}
	}

	@Example
	public void shouldNotBuildWhenCancelledWhileTheUserIsAskedAndAccepts() throws Exception {
		try (LocalServer server = new LocalServer().serve("/vasm/release/vasm.tar.gz", vasmRelease())) {
			assertEquals(CANCELLED_ONLY, cancelledDuringPrompt(server, true));
			assertTrue(Files.notExists(scratch().resolve("tools/vasm")));
		}
	}

	private List<BuildWarning> cancelledDuringPrompt(final LocalServer server, final boolean answer)
		throws Exception {

		final CountDownLatch asked = new CountDownLatch(1);
		final TrustPrompt prompt = (tool, version, sha256) -> {
			asked.countDown();
			try {
				Thread.sleep(SETTLE);
			} catch (final InterruptedException e) {
				return answer;
			}
			return answer;
		};
		final BuildJob job = new BuildJob(toolchain(new Fetcher(server.proxy(), PATIENT)),
			new BuildRequest(demo(), Optional.empty(), Optional.empty(), Optional.empty()), PATIENT, prompt,
			events::add);
		final Thread thread = Thread.ofVirtual().start(job);

		asked.await();
		job.cancel();
		thread.join(SETTLE);
		return finished().errors();
	}

	private Path victimOf(final Path link) {
		try {
			final Path victim = Files.writeString(scratch().resolve("victim-" + link.getFileName()), "victim");

			Files.createSymbolicLink(link, victim);
			return victim;
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private ProjectDirectory demo() throws IOException {
		return ProjectDirectory.create(scratch(), "nightrun", ProjectKind.DEMO, Target.A500_OCS);
	}

	private BuildReport build(final ProjectDirectory project, final Path vasm, final Path shrinkler) throws IOException {
		job(project, vasm, shrinkler, REFUSE).run();
		return finished();
	}

	private BuildJob job(final ProjectDirectory project, final Path vasm, final Path shrinkler,
		final TrustPrompt trust) {

		return new BuildJob(toolchain(new Fetcher(HttpClient.newHttpClient(), PATIENT)),
			new BuildRequest(project, Optional.of(vasm), Optional.of(shrinkler), Optional.empty()), PATIENT,
			trust, events::add);
	}

	private BuildReport finished() {
		assertEquals(1, events.stream().filter(BuildEvent.Finished.class::isInstance).count());
		return assertInstanceOf(BuildEvent.Finished.class, events.getLast()).report();
	}

	private Toolchain toolchain(final Fetcher fetcher) {
		return toolchain(fetcher, program -> Optional.empty());
	}

	private Toolchain toolchain(final Fetcher fetcher, final Function<String, Optional<Path>> onPath) {
		final Prerequisites present = new Prerequisites(program -> Optional.of(Path.of("/usr/bin", program)),
			scratch().resolve("os-release"));

		return new Toolchain(new ToolStore(scratch().resolve("tools")), fetcher, new Compiler(PATIENT), present,
			onPath, scratch().resolve("work"));
	}

	private static byte[] vasmRelease() throws IOException {
		return new TarFixture().directory("vasm").executable("vasm/stub", StubTools.vasmBody(2048, 300, ""))
			.file("vasm/Makefile", "all:\n\tcp stub vasmm68k_mot\n\tchmod +x vasmm68k_mot\n")
			.file("vasm/vasm.c", "#define _VER \"vasm 2.0g\"\n").bytes();
	}

	private static boolean gone(final long pid) throws InterruptedException {
		final Instant until = Instant.now().plus(SETTLE);

		while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
			if (Instant.now().isAfter(until)) {
				return false;
			}
			Thread.sleep(BRIEF.dividedBy(20));
		}
		return true;
	}
}
