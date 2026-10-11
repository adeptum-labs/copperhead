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
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
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
import org.copperhead.toolchain.SearchPath;
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
	static final String STOPPED = "The build stopped without a report";

	private final ProjectSession session;
	private final Preferences preferences;
	private final Toolchain toolchain;
	private final Jobs jobs;
	private final Executor executor;
	private Optional<Run> active = Optional.empty();
	private Optional<Pending> pending = Optional.empty();
	private boolean refused;

	@Inject
	public BuildService(final ProjectSession session, final Preferences preferences) {
		this(session, preferences, toolchain(XdgDirs.system(), SearchPath.system(), OS_RELEASE), STEP,
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

	/* One build or fetch; it is told apart from the next by identity, and what stops it is set once it exists. */
	private static final class Run {
		private Runnable stop = () -> { };
	}

	/* The question a run waits on, and the answer it is given once, by whoever takes the question first. */
	private record Pending(Run run, TrustRequest request, CompletableFuture<Boolean> reply) {
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

	/** Where each tool of the build would come from if a build started now. */
	public List<ToolStatus> tools() {
		return SOURCES.stream().map(source -> new ToolStatus(source.tool(), source.version(),
			toolchain.locate(source, preferences.tool(source.tool()), trusted(source)))).toList();
	}

	public synchronized void start(final boolean andRun) {
		final Run run = new Run();
		final ProjectDirectory project = begin(run, true).workspace().orElseThrow();

		try {
			final BuildRequest request = new BuildRequest(project, preferences.tool(PinnedSource.VASM.tool()),
				preferences.tool(PinnedSource.SHRINKLER.tool()), preferences.trustedVasm());
			final TrustPrompt trust = (tool, version, sha256) -> ask(run, tool, version, sha256);
			final Job job = jobs.create(request, trust, event -> take(event, run, andRun));

			run.stop = job::cancel;
			executor.execute(() -> runToTheEnd(job, run));
		} catch (RuntimeException e) {
			abandon(run, "The build could not be started");
			throw e;
		}
	}

	/* A job is meant to end with its report, but one that stops without it must not leave the studio running a
	   build that nothing does: Cancel could never end it, and no other build could start until a restart. */
	private void runToTheEnd(final Job job, final Run run) {
		try {
			job.run();
		} catch (RuntimeException | Error e) {
			LOG.error("The build stopped without its report", e);
		} finally {
			if (holds(run)) {
				LOG.error("The build ended without a report; the studio returns to idle");
				abandon(run, STOPPED);
			}
		}
	}

	private synchronized boolean holds(final Run run) {
		return active.filter(current -> current == run).isPresent();
	}

	public synchronized void fetch() {
		final Run run = new Run();

		begin(run, false);

		try {
			executor.execute(() -> fetchAll(run));
		} catch (RuntimeException e) {
			abandon(run, "The fetch could not be started");
			throw e;
		}
	}

	/**
	 * Asks what runs to stop, and answers no to a question it waits on; it does not wait for the end. A cancel
	 * that comes before the run has asked its question is remembered, so the question is refused when it comes.
	 */
	public void cancel() {
		final Optional<Runnable> stopping;
		final Optional<Pending> unanswered;

		synchronized (this) {
			refused = true;
			stopping = active.map(run -> run.stop);
			unanswered = pending;
			pending = Optional.empty();
		}

		stopping.ifPresent(Runnable::run);
		unanswered.ifPresent(question -> question.reply().complete(false));
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

	/**
	 * The person's answer to the question shown, which carries the checksum it asks about so that a dialog left
	 * over from an earlier question cannot answer a later one. Only the first answer to a question counts;
	 * accepting remembers the source, and only a vasm source can be remembered.
	 */
	public synchronized void answer(final boolean accepted, final String sha256) {
		final Pending question = pending.orElseThrow(() -> new IllegalStateException("Nothing waits for an answer"));

		if (!question.request().sha256().equals(sha256)) {
			session.change(state -> state.withNote("That question is no longer the one asked"));
			return;
		}

		resolve(question, accepted);
	}

	/** Answers no to the question shown, if one still is; the dialog that held it is closed either way. */
	public synchronized void refuse() {
		pending.ifPresent(question -> resolve(question, false));
	}

	/* The question is taken before anything else is done, so whoever takes it alone settles it, once. */
	private void resolve(final Pending question, final boolean accepted) {
		boolean granted = false;

		pending = Optional.empty();

		try {
			if (accepted && PinnedSource.VASM.tool().equals(question.request().tool())) {
				preferences.trustedVasm(question.request().sha256());
				granted = true;
			}

			session.change(state -> Optional.of(question.request()).equals(state.run().trust())
				? state.withRun(state.run().resumed()).withModal(Modal.NONE) : state);
		} finally {
			question.reply().complete(granted);
		}
	}

	/* The project is read in the very change that starts the run, so a switch cannot land between the two. */
	private StudioState begin(final Run run, final boolean projectNeeded) {
		final StudioState started = session.change(state -> {
			if (state.run().running()) {
				throw new IllegalStateException("A build is already running");
			}

			if (projectNeeded) {
				state.workspace().orElseThrow(() -> new IllegalStateException("No project is open"));
			}

			return state.withRun(BuildRun.started());
		});

		refused = false;
		active = Optional.of(run);
		return started;
	}

	private boolean ask(final Run run, final String tool, final String version, final String sha256)
		throws InterruptedException {

		final CompletableFuture<Boolean> reply = new CompletableFuture<>();
		final TrustRequest request = new TrustRequest(tool, version, sha256);

		synchronized (this) {
			if (refused || active.filter(current -> current == run).isEmpty()) {
				return false;
			}

			pending = Optional.of(new Pending(run, request, reply));
			session.change(state -> state.withRun(state.run().waiting(request)).withModal(Modal.TRUST));
		}

		try {
			return reply.get();
		} catch (ExecutionException e) {
			return false;
		}
	}

	private void take(final BuildEvent event, final Run run, final boolean andRun) {
		switch (event) {
			case BuildEvent.Line(final BuildLine line) -> add(line);
			case BuildEvent.Finished(final BuildReport report) -> finish(report, run, andRun);
		}
	}

	private void add(final BuildLine line) {
		session.change(state -> state.withRun(state.run().add(line)));
	}

	/* A build that fails or is cancelled does not start the run it was asked to start once it was done. */
	private void finish(final BuildReport report, final Run run, final boolean andRun) {
		try {
			final List<ToolStatus> found = tools();

			session.change(state -> unasked(state).withBuild(Optional.of(report)).withRun(BuildRun.idle())
				.withRunning(state.running() || andRun && report.ok())
				.withEnvironment(state.environment().withTools(found)).withNote(note(report)));
		} catch (RuntimeException e) {
			LOG.error("The report of the build could not be shown", e);
			abandon(run, "The build ended but its report could not be shown");
		} finally {
			release(run);
		}
	}

	private void fetchAll(final Run run) {
		try {
			if (cancelledBeforeStart(run)) {
				throw new InterruptedException();
			}

			for (final PinnedSource source : SOURCES) {
				final Path tool = ensure(run, source);

				add(new BuildLine(source.tool() + " " + source.version() + " at " + tool, Tone.OK));
			}
		} catch (ToolchainException | IOException e) {
			add(new BuildLine(e.getMessage(), Tone.ERR));
		} catch (InterruptedException e) {
			add(new BuildLine("fetch cancelled", Tone.ERR));
		} catch (RuntimeException e) {
			LOG.warn("The fetch of the toolchain failed", e);
		} finally {
			try {
				final List<ToolStatus> found = tools();

				session.change(state -> unasked(state).withRun(state.run().ended())
					.withEnvironment(state.environment().withTools(found)));
			} finally {
				release(run);
			}
		}
	}

	/* The worker can be interrupted from here on; a cancel that came before it is told so it need not start. */
	private synchronized boolean cancelledBeforeStart(final Run run) {
		final Thread worker = Thread.currentThread();

		run.stop = worker::interrupt;
		return refused;
	}

	private Path ensure(final Run run, final PinnedSource source)
		throws ToolchainException, IOException, InterruptedException {

		try {
			return toolchain.ensure(source, preferences.tool(source.tool()), trusted(source), this::plain);
		} catch (HashMismatch mismatch) {
			if (!ask(run, mismatch.tool(), mismatch.version(), mismatch.found())) {
				throw new ToolchainException("the " + mismatch.tool() + " source was refused");
			}

			return toolchain.ensure(source, preferences.tool(source.tool()), Optional.of(mismatch.found()),
				this::plain);
		}
	}

	private void plain(final String text) {
		add(new BuildLine(text, Tone.PLAIN));
	}

	/*
	 * Only the run that holds the slot gives it up, and only once its state is written, so a wait for the end
	 * returns to an idle studio and an end never frees the slot of a run that began since. A question the run
	 * left unanswered goes with it.
	 */
	private void release(final Run run) {
		final Optional<Pending> unanswered;

		synchronized (this) {
			active = active.filter(current -> current != run);
			unanswered = pending.filter(question -> question.run() == run);
			pending = pending.filter(question -> question.run() != run);
			notifyAll();
		}

		unanswered.ifPresent(question -> question.reply().complete(false));
	}

	/* A run that ends while its question is still up, cancelled or refused, takes the question down with it. */
	private static StudioState unasked(final StudioState state) {
		return state.modal() == Modal.TRUST ? state.withModal(Modal.NONE) : state;
	}

	private Optional<String> trusted(final PinnedSource source) {
		return source == PinnedSource.VASM ? preferences.trustedVasm() : Optional.empty();
	}

	/* The studio never stays in a run that nothing is doing: it returns to idle however the build came to an end. */
	private void abandon(final Run run, final String note) {
		try {
			session.change(state -> unasked(state).withRun(BuildRun.idle()).withNote(note));
		} finally {
			release(run);
		}
	}

	/* A cancelled tool that will not die is left behind once the wait is out, rather than holding the studio up. */
	private void awaitEnd() throws InterruptedException {
		final long deadline = System.nanoTime() + SHUTDOWN.toNanos();

		synchronized (this) {
			while (active.isPresent()) {
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

		return failed(report.errors().size());
	}

	/** How a build that failed is told, in the note it ends with and on the status bar alike. */
	public static String failed(final int errors) {
		return "Build failed · " + errors + (errors == 1 ? " error" : " errors");
	}
}
