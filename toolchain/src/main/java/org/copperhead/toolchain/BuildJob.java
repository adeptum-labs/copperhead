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
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
	private static final String PROGRAM = "build/main.bin";
	private static final String CRUNCHED = "build/main.shr";
	private static final String LISTING = "build/main.lst";
	private static final String LOADER = "build/boot.bin";
	private static final String BOOT = "boot";
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
			fail(CANCELLED);
		} catch (final IOException | ToolchainException | IllegalArgumentException e) {
			fail(Objects.requireNonNullElse(e.getMessage(), e.toString()));
		} catch (final RuntimeException e) {
			LOG.warn("Build of {} failed", request.project().dir(), e);
			fail("build failed: " + e);
		} finally {
			ended();
		}
		sink.accept(new BuildEvent.Finished(report(adf, bytes, Duration.ofNanos(System.nanoTime() - start))));
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
		final Path vasm = tool(PinnedSource.VASM, request.vasm(), request.trustedVasm());
		final Optional<Path> shrinkler = file.crunch()
			? Optional.of(tool(PinnedSource.SHRINKLER, request.shrinkler(), Optional.empty()))
			: Optional.empty();

		Files.createDirectories(project.dir().resolve(ProjectLayout.BUILD));
		assemble(vasm, project);
		final int size = Math.toIntExact(Files.size(project.dir().resolve(PROGRAM)));
		final byte[] payload = shrinkler.isPresent() ? crunch(shrinkler.get(), project)
			: Files.readAllBytes(project.dir().resolve(PROGRAM));
		final int stage = (file.loadAddress() + size + LONG - 1) / LONG * LONG;

		fits(file, stage, size, payload.length);
		final byte[] image = TrackDisk.image(loader(vasm, project, stage, payload.length), payload);

		stopIfCancelled();
		replace(project.adf(), image);
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
			if (!trust.accept(mismatch.tool(), mismatch.version(), mismatch.found())) {
				throw new ToolchainException("refused " + mismatch.tool() + " " + mismatch.version()
					+ " (sha-256 " + mismatch.found() + ")");
			}
			accepted = Optional.of(mismatch.found());
			return toolchain.ensure(source, configured, accepted, this::plain);
		}
	}

	private void assemble(final Path vasm, final ProjectDirectory project)
		throws IOException, InterruptedException, ToolchainException {

		final ProjectFile file = project.file();
		final List<String> command = new ArrayList<>(List.of(vasm.toString(), "-quiet", cpu(file.target()), "-Fbin",
			"-DLOAD=" + LoadAddress.format(file.loadAddress())));

		if (file.symbols()) {
			command.addAll(List.of("-L", LISTING));
		}
		command.addAll(List.of("-o", PROGRAM, file.entry()));
		final int exit = run("vasm", command, project, this::assembler);

		if (exit != 0 || hasErrors()) {
			throw new ToolchainException("vasm stopped with exit code " + exit);
		}
	}

	private byte[] crunch(final Path shrinkler, final ProjectDirectory project)
		throws IOException, InterruptedException, ToolchainException {

		final List<String> output = new ArrayList<>();
		final int exit = run("Shrinkler", List.of(shrinkler.toString(), "-d", "-9", "-p", PROGRAM, CRUNCHED),
			project, output::add);

		if (exit != 0) {
			output.stream().filter(text -> !text.isBlank()).forEach(this::plain);
			throw new ToolchainException("Shrinkler stopped with exit code " + exit);
		}
		output.stream().map(FINAL_SIZE::matcher).filter(Matcher::matches)
			.forEach(found -> plain("crunched " + found.group(1) + " bytes"));
		return Files.readAllBytes(project.dir().resolve(CRUNCHED));
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

	private byte[] loader(final Path vasm, final ProjectDirectory project, final int stage, final int payload)
		throws IOException, InterruptedException, ToolchainException {

		final ProjectFile file = project.file();
		final Path boot = Files.createDirectories(project.dir().resolve(ProjectLayout.BUILD).resolve(BOOT));

		for (final String name : LOADER_FILES) {
			try (InputStream source = BuildJob.class.getResourceAsStream("/" + BOOT + "/" + name)) {
				Files.copy(source, boot.resolve(name), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		final int exit = run("vasm", List.of(vasm.toString(), "-quiet", "-m68000", "-Fbin",
			"-DLOAD=" + LoadAddress.format(file.loadAddress()), "-DSTAGE=" + LoadAddress.format(stage),
			"-DCRUNCHED=" + (file.crunch() ? 1 : 0), "-DPAYLOAD_LEN=" + TrackDisk.padded(payload), "-o", LOADER,
			ProjectLayout.BUILD + "/" + BOOT + "/" + LOADER_SOURCE), project, this::plain);

		if (exit != 0) {
			throw new ToolchainException("the boot loader did not assemble: vasm stopped with exit code "
				+ exit);
		}
		return Bootblock.seal(Files.readAllBytes(project.dir().resolve(LOADER)));
	}

	private int run(final String name, final List<String> command, final ProjectDirectory project,
		final Consumer<String> output) throws IOException, InterruptedException {

		stopIfCancelled();
		plain(name + " " + String.join(" ", command.subList(1, command.size())));
		return Processes.run(name, command, project.dir(), stepTimeout, output);
	}

	/* Called on the thread that reads the assembler's output. */
	private void assembler(final String output) {
		final List<AssemblerMessage> messages = VasmLog.parse(List.of(output));

		if (messages.isEmpty()) {
			if (!output.isBlank()) {
				line(output, Tone.PLAIN);
			}
			return;
		}
		final AssemblerMessage message = messages.getFirst();
		final boolean error = message.severity() == AssemblerMessage.Severity.ERROR;

		synchronized (log) {
			(error ? errors : warnings).add(new BuildWarning(message.file(), message.line(), message.text()));
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
		sink.accept(new BuildEvent.Line(line));
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

	private static void replace(final Path file, final byte[] content) throws IOException {
		final Path staged = file.resolveSibling(file.getFileName() + ".part");

		try {
			Files.write(staged, content);
			Files.move(staged, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(staged);
		}
	}
}
