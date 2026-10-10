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

package org.copperhead.ui.session;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.copperhead.core.build.BuildLine;
import org.copperhead.core.build.BuildReport;
import org.copperhead.core.build.Tone;
import org.copperhead.toolchain.BuildEvent;
import org.copperhead.toolchain.BuildJob;
import org.copperhead.toolchain.BuildRequest;
import org.copperhead.toolchain.Compiler;
import org.copperhead.toolchain.Fetcher;
import org.copperhead.toolchain.HashMismatch;
import org.copperhead.toolchain.PinnedSource;
import org.copperhead.toolchain.Prerequisites;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.toolchain.ToolStore;
import org.copperhead.toolchain.Toolchain;
import org.copperhead.toolchain.ToolchainException;
import org.copperhead.toolchain.TrustPrompt;
import org.copperhead.toolchain.XdgDirs;
import org.copperhead.ui.session.BuildRun.TrustRequest;
import org.copperhead.ui.view.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one build, or one fetch of the toolchain, at a time, off the thread that asked for it. What it writes lands
 * in the session line by line, where the page's polling finds it; a vasm source Copperhead does not pin waits for
 * the person to accept or refuse it, and an accepted one is remembered.
 */
@ApplicationScoped
public class BuildService {
	private static final Logger LOG = LoggerFactory.getLogger(BuildService.class);
	private static final Duration FETCH = Duration.ofMinutes(2);
	private static final Duration COMPILE = Duration.ofMinutes(5);
	private static final Duration STEP = Duration.ofMinutes(2);
	/* Killing the tools of a cancelled build takes moments; past this, one that will not die is left behind. */
	private static final Duration SHUTDOWN = Duration.ofSeconds(5);
	private static final Path OS_RELEASE = Path.of("/etc/os-release");
	private static final String TOOLS = "tools";
	private static final List<PinnedSource> SOURCES = List.of(PinnedSource.VASM, PinnedSource.SHRINKLER);

	private final ProjectSession session;
	private final Preferences preferences;
	private final Toolchain toolchain;
	private final Jobs jobs;
	private final Executor executor;
	private Optional<Runnable> stop = Optional.empty();
	private Optional<CompletableFuture<Boolean>> answer = Optional.empty();

	@Inject
	public BuildService(final ProjectSession session, final Preferences preferences) {
		this(session, preferences, toolchain(XdgDirs.system(), BuildService::onPath, OS_RELEASE), STEP,
			command -> Thread.ofVirtual().name("build").start(command));
	}

	public BuildService(final ProjectSession session, final Preferences preferences, final Toolchain toolchain,
		final Duration stepTimeout, final Executor executor) {

		this(session, preferences, toolchain, jobs(toolchain, stepTimeout), executor);
	}

	public BuildService(final ProjectSession session, final Preferences preferences, final Toolchain toolchain,
		final Jobs jobs, final Executor executor) {

		this.session = session;
		this.preferences = preferences;
		this.toolchain = toolchain;
		this.jobs = jobs;
		this.executor = executor;
	}

	/** A build as the service runs it, which it can stop. */
	public interface Job extends Runnable {
		void cancel();
	}

	/** Makes the job for a build: the toolchain's own, or one of a test's that stands in for its questions. */
	@FunctionalInterface
	public interface Jobs {
		Job create(BuildRequest request, TrustPrompt trust, Consumer<BuildEvent> sink);
	}

	public static Jobs jobs(final Toolchain toolchain, final Duration stepTimeout) {
		return (request, trust, sink) -> {
			final BuildJob job = new BuildJob(toolchain, request, stepTimeout, trust, sink);

			return new Job() {
				@Override
				public void run() {
					job.run();
				}

				@Override
				public void cancel() {
					job.cancel();
				}
			};
		};
	}

	/** The toolchain as the studio keeps it: tools under the data folder, sources fetched into the cache. */
	public static Toolchain toolchain(final XdgDirs dirs, final Function<String, Optional<Path>> onPath,
		final Path osRelease) {

		return new Toolchain(new ToolStore(dirs.data().resolve(TOOLS)),
			new Fetcher(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(), FETCH),
			new Compiler(COMPILE), new Prerequisites(onPath, osRelease), onPath, dirs.cache());
	}

	/** Where the system's search path holds an executable of the name, if it does. */
	public static Optional<Path> onPath(final String name) {
		return Arrays.stream(System.getenv().getOrDefault("PATH", "").split(File.pathSeparator))
			.filter(directory -> !directory.isEmpty()).flatMap(directory -> located(directory, name))
			.filter(Files::isExecutable).findFirst();
	}

	private static Stream<Path> located(final String directory, final String name) {
		try {
			return Stream.of(Path.of(directory, name));
		} catch (InvalidPathException e) {
			return Stream.empty();
		}
	}

	/** Where each tool of the build would come from if a build started now. */
	public List<ToolStatus> tools() {
		return SOURCES.stream().map(source -> new ToolStatus(source.tool(), source.version(),
			toolchain.locate(source, preferences.tool(source.tool()), trusted(source)))).toList();
	}

	public synchronized void start(final boolean andRun) {
		final ProjectDirectory project = begin(true).workspace().orElseThrow();

		try {
			final BuildRequest request = new BuildRequest(project, preferences.tool(PinnedSource.VASM.tool()),
				preferences.tool(PinnedSource.SHRINKLER.tool()), preferences.trustedVasm());
			final Job job = jobs.create(request, this::ask, event -> take(event, andRun));

			stop = Optional.of(job::cancel);
			executor.execute(job);
		} catch (RuntimeException e) {
			abandon("The build could not be started");
			throw e;
		}
	}

	public synchronized void fetch() {
		begin(false);

		try {
			stop = Optional.of(() -> { });
			executor.execute(this::fetchAll);
		} catch (RuntimeException e) {
			abandon("The fetch could not be started");
			throw e;
		}
	}

	/** Asks what runs to stop, and answers no to a question it waits on; it does not wait for the end. */
	public void cancel() {
		final Optional<Runnable> stopping;

		synchronized (this) {
			stopping = stop;
		}

		stopping.ifPresent(Runnable::run);
		settle(false);
	}

	/**
	 * Called by the container when the studio closes: stops what runs and waits for its thread, which kills the
	 * tools it started, so none of them outlives the studio. A tool that will not die cannot hold the studio up
	 * for long.
	 */
	@PreDestroy
	public void shutDown() {
		cancel();

		try {
			awaitEnd();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** The person's answer to the question the run waits on; accepting remembers the source they accepted. */
	public void answer(final boolean accepted) {
		final TrustRequest request = session.state().run().trust()
			.orElseThrow(() -> new IllegalStateException("Nothing waits for an answer"));

		if (accepted) {
			preferences.trustedVasm(request.sha256());
		}

		session.change(state -> state.withRun(state.run().resumed()).withModal(Modal.NONE));
		settle(accepted);
	}

	/* The project is read in the very change that starts the run, so a switch cannot land between the two. */
	private StudioState begin(final boolean projectNeeded) {
		return session.change(state -> {
			if (state.run().running()) {
				throw new IllegalStateException("A build is already running");
			}

			if (projectNeeded) {
				state.workspace().orElseThrow(() -> new IllegalStateException("No project is open"));
			}

			return state.withRun(BuildRun.started());
		});
	}

	private void settle(final boolean accepted) {
		final Optional<CompletableFuture<Boolean>> waiting;

		synchronized (this) {
			waiting = answer;
			answer = Optional.empty();
		}

		waiting.ifPresent(question -> question.complete(accepted));
	}

	private boolean ask(final String tool, final String version, final String sha256) throws InterruptedException {
		final CompletableFuture<Boolean> question = new CompletableFuture<>();

		synchronized (this) {
			answer = Optional.of(question);
		}

		session.change(state -> state.withRun(state.run().waiting(new TrustRequest(tool, version, sha256)))
			.withModal(Modal.TRUST));

		try {
			return question.get();
		} catch (ExecutionException e) {
			return false;
		}
	}

	private void take(final BuildEvent event, final boolean andRun) {
		switch (event) {
			case BuildEvent.Line(final BuildLine line) -> add(line);
			case BuildEvent.Finished(final BuildReport report) -> finish(report, andRun);
		}
	}

	private void add(final BuildLine line) {
		session.change(state -> state.withRun(state.run().add(line)));
	}

	/* A build that fails or is cancelled does not start the run it was asked to start once it was done. */
	private void finish(final BuildReport report, final boolean andRun) {
		final List<ToolStatus> found = tools();

		done();

		try {
			session.change(state -> unasked(state).withBuild(Optional.of(report)).withRun(BuildRun.idle())
				.withRunning(state.running() || andRun && report.ok())
				.withEnvironment(state.environment().withTools(found)).withNote(note(report)));
		} catch (RuntimeException e) {
			LOG.error("The report of the build could not be shown", e);
			abandon("The build ended but its report could not be shown");
		}
	}

	private void fetchAll() {
		final Thread worker = Thread.currentThread();

		synchronized (this) {
			stop = Optional.of(worker::interrupt);
		}

		try {
			for (final PinnedSource source : SOURCES) {
				final Path tool = ensure(source);

				add(new BuildLine(source.tool() + " " + source.version() + " at " + tool, Tone.OK));
			}
		} catch (ToolchainException | IOException e) {
			add(new BuildLine(e.getMessage(), Tone.ERR));
		} catch (InterruptedException e) {
			add(new BuildLine("fetch cancelled", Tone.ERR));
		} finally {
			final List<ToolStatus> found = tools();

			done();
			session.change(state -> unasked(state).withRun(state.run().ended())
				.withEnvironment(state.environment().withTools(found)));
		}
	}

	private Path ensure(final PinnedSource source) throws ToolchainException, IOException, InterruptedException {
		try {
			return toolchain.ensure(source, preferences.tool(source.tool()), trusted(source), this::plain);
		} catch (HashMismatch mismatch) {
			if (!ask(mismatch.tool(), mismatch.version(), mismatch.found())) {
				throw new ToolchainException("the " + mismatch.tool() + " source was refused");
			}

			return toolchain.ensure(source, preferences.tool(source.tool()), Optional.of(mismatch.found()),
				this::plain);
		}
	}

	private void plain(final String text) {
		add(new BuildLine(text, Tone.PLAIN));
	}

	private synchronized void done() {
		stop = Optional.empty();
		notifyAll();
	}

	/* A run that ends while its question is still up, cancelled or refused, takes the question down with it. */
	private static StudioState unasked(final StudioState state) {
		return state.modal() == Modal.TRUST ? state.withModal(Modal.NONE) : state;
	}

	private Optional<String> trusted(final PinnedSource source) {
		return source == PinnedSource.VASM ? preferences.trustedVasm() : Optional.empty();
	}

	/* The studio never stays in a run that nothing is doing: it returns to idle however the build came to an end. */
	private void abandon(final String note) {
		done();
		session.change(state -> unasked(state).withRun(BuildRun.idle()).withNote(note));
	}

	/* A cancelled tool that will not die is left behind once the wait is out, rather than holding the studio up. */
	private void awaitEnd() throws InterruptedException {
		final long deadline = System.nanoTime() + SHUTDOWN.toNanos();

		synchronized (this) {
			while (stop.isPresent()) {
				final long left = deadline - System.nanoTime();

				if (left <= 0) {
					return;
				}
				wait(left / 1_000_000, (int) (left % 1_000_000));
			}
		}
	}

	private static String note(final BuildReport report) {
		if (report.ok()) {
			return String.format(Locale.ROOT, "Built %s · %,d bytes", Path.of(report.adf()).getFileName(),
				report.bytes());
		}

		final int errors = report.errors().size();

		return "Build failed · " + errors + (errors == 1 ? " error" : " errors");
	}
}
