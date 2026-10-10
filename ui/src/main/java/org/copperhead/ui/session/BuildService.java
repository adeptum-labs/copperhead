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
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.copperhead.core.build.BuildLine;
import org.copperhead.core.build.BuildReport;
import org.copperhead.toolchain.BuildEvent;
import org.copperhead.toolchain.BuildJob;
import org.copperhead.toolchain.BuildRequest;
import org.copperhead.toolchain.Compiler;
import org.copperhead.toolchain.Fetcher;
import org.copperhead.toolchain.PinnedSource;
import org.copperhead.toolchain.Prerequisites;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.toolchain.ToolStore;
import org.copperhead.toolchain.Toolchain;
import org.copperhead.toolchain.TrustPrompt;
import org.copperhead.toolchain.XdgDirs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one build at a time, off the thread that asked for it. What the build writes lands in the session line by
 * line, where the page's polling finds it, and its report becomes the studio's last build.
 */
@ApplicationScoped
public class BuildService {
	private static final Logger LOG = LoggerFactory.getLogger(BuildService.class);
	private static final Duration FETCH = Duration.ofMinutes(2);
	private static final Duration COMPILE = Duration.ofMinutes(5);
	private static final Duration STEP = Duration.ofMinutes(2);
	private static final Path OS_RELEASE = Path.of("/etc/os-release");
	private static final String TOOLS = "tools";
	/* Killing the tools of a cancelled build takes moments; past this, one that will not die is left behind. */
	private static final Duration SHUTDOWN = Duration.ofSeconds(5);
	/* Until the person can be asked, a vasm source Copperhead does not pin is refused rather than built with. */
	private static final TrustPrompt REFUSED = (tool, version, sha256) -> false;

	private final ProjectSession session;
	private final Preferences preferences;
	private final Jobs jobs;
	private final Executor executor;
	private Optional<Active> active = Optional.empty();

	@Inject
	public BuildService(final ProjectSession session, final Preferences preferences) {
		this(session, preferences, jobs(toolchain(XdgDirs.system(), BuildService::onPath, OS_RELEASE), STEP),
			command -> Thread.ofVirtual().name("build").start(command));
	}

	public BuildService(final ProjectSession session, final Preferences preferences, final Jobs jobs,
		final Executor executor) {

		this.session = session;
		this.preferences = preferences;
		this.jobs = jobs;
		this.executor = executor;
	}

	/* What stops the build that holds the slot, and the latch its thread counts down once it has cleaned up. */
	private record Active(Runnable stop, CountDownLatch over) {
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

	public synchronized void start(final boolean andRun) {
		final ProjectDirectory project = session.state().workspace()
			.orElseThrow(() -> new IllegalStateException("No project is open"));
		final BuildRequest request = new BuildRequest(project, preferences.tool(PinnedSource.VASM.tool()),
			preferences.tool(PinnedSource.SHRINKLER.tool()), preferences.trustedVasm());
		final CountDownLatch over = new CountDownLatch(1);
		final Job job = jobs.create(request, REFUSED, event -> take(event, andRun, over));

		begin(new Active(job::cancel, over));

		try {
			executor.execute(() -> {
				try {
					job.run();
				} finally {
					over.countDown();
				}
			});
		} catch (RuntimeException e) {
			abandon(over, "The build could not be started");
			throw e;
		}
	}

	/** Asks what runs to stop, and does not wait for it to; the page learns of the end from its next poll. */
	public void cancel() {
		running().ifPresent(active -> active.stop().run());
	}

	/**
	 * Called by the container when the studio closes: stops what runs and waits for the build's thread, which kills
	 * the tools it started, so none of them outlives the studio. A tool that will not die cannot hold the studio up
	 * for long.
	 */
	@PreDestroy
	public void shutDown() {
		final Optional<Active> active = running();

		active.ifPresent(running -> running.stop().run());

		try {
			if (active.isPresent() && !active.get().over().await(SHUTDOWN.toMillis(), TimeUnit.MILLISECONDS)) {
				LOG.warn("The build did not end within {} of the studio closing", SHUTDOWN);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private synchronized Optional<Active> running() {
		return active;
	}

	private void begin(final Active started) {
		if (session.state().run().running()) {
			throw new IllegalStateException("A build is already running");
		}

		active = Optional.of(started);
		session.change(state -> state.withRun(BuildRun.started()));
	}

	private void take(final BuildEvent event, final boolean andRun, final CountDownLatch over) {
		switch (event) {
			case BuildEvent.Line(final BuildLine line) -> add(line);
			case BuildEvent.Finished(final BuildReport report) -> finish(report, andRun, over);
		}
	}

	private void add(final BuildLine line) {
		session.change(state -> state.withRun(state.run().add(line)));
	}

	/* A build that fails or is cancelled does not start the run it was asked to start once it was done. */
	private void finish(final BuildReport report, final boolean andRun, final CountDownLatch over) {
		try {
			session.change(state -> state.withBuild(Optional.of(report)).withRun(BuildRun.idle())
				.withRunning(state.running() || andRun && report.ok()).withNote(note(report)));
		} catch (RuntimeException e) {
			LOG.error("The report of the build could not be shown", e);
			abandon(over, "The build ended but its report could not be shown");
		} finally {
			release(over);
		}
	}

	/* The studio never stays in a run that nothing is doing: it returns to idle however the build came to an end. */
	private void abandon(final CountDownLatch over, final String note) {
		try {
			session.change(state -> state.withRun(BuildRun.idle()).withNote(note));
		} finally {
			release(over);
		}
	}

	/* Only the build that still holds the slot gives it up: another may have started since it ended. */
	private synchronized void release(final CountDownLatch over) {
		active = active.filter(current -> current.over() != over);
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
