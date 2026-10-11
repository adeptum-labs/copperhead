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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.copperhead.core.build.AssemblerMessage;
import org.copperhead.core.build.Bootblock;
import org.copperhead.core.build.BuildLine;
import org.copperhead.core.build.BuildReport;
import org.copperhead.core.build.BuildWarning;
import org.copperhead.core.build.LoadAddress;
import org.copperhead.core.build.Tone;
import org.copperhead.core.build.TrackDisk;
import org.copperhead.core.build.VasmLog;
import org.copperhead.core.project.ProjectFile;
import org.copperhead.core.project.ProjectLayout;
import org.copperhead.core.project.Target;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One build of a project into a bootable disk: make sure the tools are there, assemble, crunch, check that the
 * program fits in chip RAM, assemble the boot loader for it and write the disk image. It runs on a thread of its own
 * and reports through its sink; it never throws, and it ends with exactly one {@link BuildEvent.Finished}. The disk
 * of the last good build stays until a new one is written whole.
 */
public final class BuildJob implements Runnable {
	private static final Logger LOG = LoggerFactory.getLogger(BuildJob.class);
	private static final String PROGRAM_FILE = "main.bin";
	private static final String LISTING_FILE = "main.lst";
	private static final String LOADER_FILE = "boot.bin";
	private static final String PROGRAM = ProjectLayout.BUILD + "/" + PROGRAM_FILE;
	private static final String CRUNCHED = "build/main.shr";
	private static final String LISTING = ProjectLayout.BUILD + "/" + LISTING_FILE;
	private static final String LOADER = ProjectLayout.BUILD + "/" + LOADER_FILE;
	private static final String BOOT = "boot";
	private static final String BOOT_FOLDER = ProjectLayout.BUILD + "/" + BOOT;
	private static final String LOADER_SOURCE = "bootblock.s";
	private static final List<String> LOADER_FILES = List.of(LOADER_SOURCE, "ShrinklerDecompress.S");
	private static final Pattern FINAL_SIZE = Pattern.compile("Final file size: (\\d+)");
	/* bootblock.s gives the unpacker this much stack just above the staged data. */
	static final int DECRUNCH_STACK = 4096;
	/* The staged data starts on a long word, as the boot loader's read and the unpacker expect. */
	private static final int LONG = 4;
	private static final String CANCELLED = "build cancelled";

	private final Toolchain toolchain;
	private final BuildRequest request;
	private final Duration stepTimeout;
	private final TrustPrompt trust;
	private final Consumer<BuildEvent> sink;
	private final List<BuildLine> log = new ArrayList<>();
	private final List<BuildWarning> warnings = new ArrayList<>();
	private final List<BuildWarning> errors = new ArrayList<>();
	private Thread runner;
	private boolean cancelled;
	private volatile Optional<String> accepted = Optional.empty();

	public BuildJob(final Toolchain toolchain, final BuildRequest request, final Duration stepTimeout,
		final TrustPrompt trust, final Consumer<BuildEvent> sink) {

		this.toolchain = toolchain;
		this.request = request;
		this.stepTimeout = stepTimeout;
		this.trust = trust;
		this.sink = sink;
	}

	@Override
	public void run() {
		final long start = System.nanoTime();
		String adf = "";
		int bytes = 0;

		try {
			began();
			bytes = build(request.project());
			adf = ProjectLayout.adf(request.project().file().name());
		} catch (final InterruptedException e) {
			cancelled();
		} catch (final IOException | ToolchainException | IllegalArgumentException e) {
			fail(Objects.requireNonNullElse(e.getMessage(), e.toString()));
		} catch (final RuntimeException | Error e) {
			LOG.error("Build of {} failed", request.project().dir(), e);
			fail("build failed: " + readable(e));
		} finally {
			ended();
			emit(new BuildEvent.Finished(report(adf, bytes, Duration.ofNanos(System.nanoTime() - start))));
		}
	}

	/* An error such as running out of memory names no file or line, so its kind is what the person is told. */
	private static String readable(final Throwable failure) {
		final String message = failure.getMessage();

		return failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
	}

	/* The interrupt reaches whatever the build waits on: a download, make, the assembler or the trust prompt; a
	   program that is running is killed together with its children. */
	public synchronized void cancel() {
		cancelled = true;
		if (runner != null) {
			runner.interrupt();
		}
	}

	public Optional<String> trustAccepted() {
		return accepted;
	}

	private int build(final ProjectDirectory project) throws IOException, InterruptedException, ToolchainException {
		final ProjectFile file = project.file();

		requireOwnFolders(project);
		final Path vasm = tool(PinnedSource.VASM, request.vasm(), request.trustedVasm());
		final Optional<Path> shrinkler = file.crunch()
			? Optional.of(tool(PinnedSource.SHRINKLER, request.shrinkler(), Optional.empty()))
			: Optional.empty();

		try (JobFolder job = JobFolder.create(project)) {
			return build(project, vasm, shrinkler, job);
		}
	}

	private int build(final ProjectDirectory project, final Path vasm, final Optional<Path> shrinkler,
		final JobFolder job) throws IOException, InterruptedException, ToolchainException {

		final ProjectFile file = project.file();

		assemble(vasm, project, job);
		final int size = withinChipRam(file.target(), project.dir().resolve(PROGRAM));
		final Path packed = shrinkler.isPresent() ? crunch(shrinkler.get(), project)
			: project.dir().resolve(PROGRAM);
		final int stage = (file.loadAddress() + size + LONG - 1) / LONG * LONG;

		fits(file, stage, size, withinChipRam(file.target(), packed));
		final byte[] payload = Files.readAllBytes(packed);
		final byte[] image = TrackDisk.image(loader(vasm, project, job, stage, payload.length), payload);

		stopIfCancelled();
		ProjectDirectory.replace(project.adf(), image);
		line("adf " + ProjectLayout.adf(file.name()) + "  880K · " + payload.length
			+ " bytes · bootblock checksum ok", Tone.OK);
		return payload.length;
	}

	private Path tool(final PinnedSource source, final Optional<Path> configured, final Optional<String> trusted)
		throws IOException, InterruptedException, ToolchainException {

		try {
			return toolchain.ensure(source, configured, trusted, this::plain);
		} catch (final HashMismatch mismatch) {
			line(mismatch.getMessage(), Tone.WARN);
			final boolean answer = trust.accept(mismatch.tool(), mismatch.version(), mismatch.found());

			stopIfCancelled();
			if (!answer) {
				throw new ToolchainException("refused " + mismatch.tool() + " " + mismatch.version()
					+ " (sha-256 " + mismatch.found() + ")");
			}
			accepted = Optional.of(mismatch.found());
			return toolchain.ensure(source, configured, accepted, this::plain);
		}
	}

	private void assemble(final Path vasm, final ProjectDirectory project, final JobFolder job)
		throws IOException, InterruptedException, ToolchainException {

		final ProjectFile file = project.file();
		final List<String> command = new ArrayList<>(List.of(vasm.toString(), "-quiet", cpu(file.target()), "-Fbin",
			"-DLOAD=" + LoadAddress.format(file.loadAddress())));

		if (file.symbols()) {
			command.addAll(List.of("-L", job.path(LISTING_FILE)));
		}
		command.addAll(List.of("-o", job.path(PROGRAM_FILE), file.entry()));
		discard(project, PROGRAM, LISTING);
		final int exit = run("vasm", command, project, text -> assembler(text, true));

		if (exit != 0 || hasErrors()) {
			throw new ToolchainException("vasm stopped with exit code " + exit);
		}
		job.collect(project, Map.of(PROGRAM_FILE, PROGRAM, LISTING_FILE, LISTING));
		written(project, PROGRAM);
	}

	/* Asked before the file is read: a program of gigabytes would otherwise fill the studio's memory first. */
	private static int withinChipRam(final Target target, final Path output) throws IOException, ToolchainException {
		final long size = Files.size(output);

		if (size > target.chipEnd()) {
			throw new ToolchainException(ProjectLayout.BUILD + "/" + output.getFileName() + " is " + size
				+ " bytes, more than the " + target.chipEnd() + " bytes of chip RAM the " + target.machine()
				+ " has");
		}
		return (int) size;
	}

	private Path crunch(final Path shrinkler, final ProjectDirectory project)
		throws IOException, InterruptedException, ToolchainException {

		final List<String> output = new ArrayList<>();

		discard(project, CRUNCHED);
		final int exit = run("Shrinkler", List.of(shrinkler.toString(), "-d", "-9", "-p", PROGRAM, CRUNCHED),
			project, output::add);

		if (exit != 0) {
			output.stream().filter(text -> !text.isBlank()).forEach(this::plain);
			throw new ToolchainException("Shrinkler stopped with exit code " + exit);
		}
		output.stream().map(FINAL_SIZE::matcher).filter(Matcher::matches)
			.forEach(found -> plain("crunched " + found.group(1) + " bytes"));
		return written(project, CRUNCHED);
	}

	/* Uncrunched, the program is read straight to its load address. Crunched, it is read to the stage above itself
	   and unpacked down, with the unpacker's stack above the staged data. */
	private static void fits(final ProjectFile file, final int stage, final int size, final int payload)
		throws ToolchainException {

		final Target target = file.target();
		final long end = file.crunch() ? (long) stage + TrackDisk.padded(payload) + DECRUNCH_STACK
			: (long) file.loadAddress() + TrackDisk.padded(size);

		if (end > target.chipEnd()) {
			throw new ToolchainException("program needs " + end + " bytes of chip RAM, " + target.machine()
				+ " has " + target.chipEnd());
		}
	}

	private byte[] loader(final Path vasm, final ProjectDirectory project, final JobFolder job, final int stage,
		final int payload) throws IOException, InterruptedException, ToolchainException {

		final ProjectFile file = project.file();
		final Path boot = Files.createDirectories(project.dir().resolve(BOOT_FOLDER));

		for (final String name : LOADER_FILES) {
			discard(project, BOOT_FOLDER + "/" + name);
			try (InputStream source = BuildJob.class.getResourceAsStream("/" + BOOT + "/" + name)) {
				Files.copy(source, boot.resolve(name));
			}
		}
		discard(project, LOADER);
		/* vasm looks for an include in its working folder before any other, so a ShrinklerDecompress.S planted in
		   the project folder would be assembled into the boot block. In the boot folder, the one just copied is
		   the only one it finds. */
		final int exit = run("vasm", List.of(vasm.toString(), "-quiet", "-nocompdir", "-m68000", "-Fbin",
			"-DLOAD=" + LoadAddress.format(file.loadAddress()), "-DSTAGE=" + LoadAddress.format(stage),
			"-DCRUNCHED=" + (file.crunch() ? 1 : 0), "-DPAYLOAD_LEN=" + TrackDisk.padded(payload), "-o",
			boot.relativize(project.dir().resolve(job.path(LOADER_FILE))).toString(), LOADER_SOURCE), boot,
			text -> assembler(text, false));

		if (exit != 0) {
			throw new ToolchainException("the boot loader did not assemble: vasm stopped with exit code "
				+ exit);
		}
		job.collect(project, Map.of(LOADER_FILE, LOADER));
		return Bootblock.seal(Files.readAllBytes(written(project, LOADER)));
	}

	/* A cloned project can plant links in its build folder, and the tools open their outputs through links. */
	private static void requireOwnFolders(final ProjectDirectory project) throws IOException, ToolchainException {
		final Path root = project.dir().toRealPath();

		for (final String name : List.of(ProjectLayout.BUILD, BOOT_FOLDER)) {
			final Path folder = project.dir().resolve(name);

			if (Files.isSymbolicLink(folder) || Files.exists(folder) && !folder.toRealPath().startsWith(root)) {
				throw new ToolchainException(name + " folder is a link");
			}
		}
	}

	/* Deleting removes a planted link itself, and keeps an output of an earlier build from passing as new. */
	private static void discard(final ProjectDirectory project, final String... names) throws IOException {
		for (final String name : names) {
			Files.deleteIfExists(project.dir().resolve(name));
		}
	}

	private static Path written(final ProjectDirectory project, final String name) throws ToolchainException {
		final Path output = project.dir().resolve(name);

		if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)) {
			throw new ToolchainException(name + " was not written");
		}
		return output;
	}

	private int run(final String name, final List<String> command, final ProjectDirectory project,
		final Consumer<String> output) throws IOException, InterruptedException {

		return run(name, command, project.dir(), output);
	}

	private int run(final String name, final List<String> command, final Path folder, final Consumer<String> output)
		throws IOException, InterruptedException {

		stopIfCancelled();
		plain(name + " " + String.join(" ", command.subList(1, command.size())));
		return Processes.run(name, command, folder, stepTimeout, output);
	}

	/* Called on the thread that reads the assembler's output. */
	private void assembler(final String output, final boolean located) {
		final List<AssemblerMessage> messages = VasmLog.parse(List.of(output));

		if (messages.isEmpty()) {
			if (!output.isBlank()) {
				line(output, Tone.PLAIN);
			}
			return;
		}
		final AssemblerMessage message = messages.getFirst();
		final boolean error = message.severity() == AssemblerMessage.Severity.ERROR;

		if (located) {
			synchronized (log) {
				(error ? errors : warnings)
					.add(new BuildWarning(message.file(), message.line(), message.text()));
			}
		}
		line(output, error ? Tone.ERR : Tone.WARN);
	}

	private void plain(final String text) {
		line(text, Tone.PLAIN);
	}

	/* The sink is called outside the lock: it may wait on the studio's own lock while the studio cancels. */
	private void line(final String text, final Tone tone) {
		final BuildLine line = new BuildLine(text, tone);

		synchronized (log) {
			log.add(line);
		}
		emit(new BuildEvent.Line(line));
	}

	/* A watcher that fails must not stop the build or keep its report from being delivered. */
	private void emit(final BuildEvent event) {
		try {
			sink.accept(event);
		} catch (final RuntimeException e) {
			LOG.warn("The watcher of the build of {} failed on {}", request.project().dir(), event, e);
		}
	}

	/* The assembler's own errors already say why the build stopped, and they are what the editor marks. */
	private void fail(final String message) {
		line(message, Tone.ERR);
		synchronized (log) {
			if (errors.isEmpty()) {
				errors.add(new BuildWarning("", 0, message));
			}
		}
	}

	private void cancelled() {
		line(CANCELLED, Tone.ERR);
		synchronized (log) {
			errors.add(new BuildWarning("", 0, CANCELLED));
		}
	}

	private boolean hasErrors() {
		synchronized (log) {
			return !errors.isEmpty();
		}
	}

	private BuildReport report(final String adf, final int bytes, final Duration took) {
		synchronized (log) {
			return new BuildReport(adf, log, warnings, errors, bytes, took);
		}
	}

	private synchronized void began() throws InterruptedException {
		runner = Thread.currentThread();
		stopIfCancelled();
	}

	/* Cleared under the lock, so an interrupt from cancel() never outlives the build on a thread that goes on to
	   other work. */
	private synchronized void ended() {
		runner = null;
		Thread.interrupted();
	}

	private synchronized void stopIfCancelled() throws InterruptedException {
		if (cancelled) {
			throw new InterruptedException(CANCELLED);
		}
	}

	private static String cpu(final Target target) {
		return target == Target.A1200_AGA ? "-m68020" : "-m68000";
	}
}
