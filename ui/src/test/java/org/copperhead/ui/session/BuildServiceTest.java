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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.copperhead.core.build.BuildReport;
import org.copperhead.core.build.BuildWarning;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.Target;
import org.copperhead.toolchain.BuildEvent;
import org.copperhead.toolchain.BuildRequest;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.toolchain.XdgDirs;
import org.copperhead.ui.TempDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The service with jobs of a test's own, so its handling of a build that ends oddly can be seen. */
public class BuildServiceTest {
	private static final Duration TAKES_A_WHILE = Duration.ofMillis(300);

	private TempDirectory home;
	private Preferences preferences;
	private ProjectSession session;

	@BeforeTry
	public void open() throws Exception {
		home = new TempDirectory();
		preferences = new Preferences(home.path().resolve("preferences.json"));
		preferences.used(ProjectDirectory.create(home.path(), "demo", ProjectKind.DEMO, Target.A500_OCS).chp());
		session = new ProjectSession(preferences, () -> Instant.EPOCH);
	}

	@AfterTry
	public void remove() throws Exception {
		home.close();
	}

	private BuildService serving(final BuildService.Jobs jobs, final Executor executor) {
		return new BuildService(session, preferences, BuildService.toolchain(XdgDirs.of(Map.of(), home.path()),
			name -> Optional.empty(), home.path().resolve("os-release")), jobs, executor);
	}

	private static BuildService.Jobs finishing(final BuildReport report) {
		return (request, trust, sink) -> new BuildService.Job() {
			@Override
			public void run() {
				sink.accept(new BuildEvent.Finished(report));
			}

			@Override
			public void cancel() {
			}
		};
	}

	@Example
	public void shouldWaitForTheBuildToEndWhenTheStudioCloses() throws Exception {
		final CountDownLatch stopped = new CountDownLatch(1);
		final AtomicBoolean cleaned = new AtomicBoolean();
		final BuildService service = serving((request, trust, sink) -> new BuildService.Job() {
			@Override
			public void run() {
				try {
					stopped.await();
					Thread.sleep(TAKES_A_WHILE);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				cleaned.set(true);
				sink.accept(new BuildEvent.Finished(failed()));
			}

			@Override
			public void cancel() {
				stopped.countDown();
			}
		}, command -> Thread.ofVirtual().start(command));

		service.start(false);
		service.shutDown();

		assertTrue(cleaned.get());
	}

	@Example
	public void shouldAnswerACancelAtOnceWithoutWaitingForTheBuild() throws Exception {
		final CountDownLatch release = new CountDownLatch(1);
		final BuildService service = serving((request, trust, sink) -> new BuildService.Job() {
			@Override
			public void run() {
				try {
					release.await();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				sink.accept(new BuildEvent.Finished(failed()));
			}

			@Override
			public void cancel() {
			}
		}, command -> Thread.ofVirtual().start(command));

		service.start(false);
		service.cancel();

		assertTrue(session.state().run().running());
		release.countDown();
		service.shutDown();
	}

	/* A build cancelled while its question is up is answered no, or the job would wait forever for an answer. */
	@Example
	public void shouldAnswerNoToTheQuestionACancelledBuildWaitsOn() throws Exception {
		final AtomicBoolean accepted = new AtomicBoolean(true);
		final BuildService service = serving((request, trust, sink) -> new BuildService.Job() {
			@Override
			public void run() {
				try {
					accepted.set(trust.accept("vasm", "2.0g", "5f".repeat(32)));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				sink.accept(new BuildEvent.Finished(failed()));
			}

			@Override
			public void cancel() {
			}
		}, command -> Thread.ofVirtual().start(command));

		service.start(false);

		for (int attempt = 0; attempt < 250 && session.state().run().trust().isEmpty(); attempt++) {
			Thread.sleep(20);
		}
		assertTrue(session.state().run().trust().isPresent());
		service.cancel();
		service.shutDown();

		assertFalse(accepted.get());
		assertFalse(session.state().run().running());
	}

	@Example
	public void shouldReturnToIdleWhenTheBuildCannotBeHandedToAThread() {
		final BuildService service = serving(finishing(failed()), command -> {
			throw new RejectedExecutionException("no thread");
		});

		assertThrows(RejectedExecutionException.class, () -> service.start(false));
		assertFalse(session.state().run().running());
	}

	@Example
	public void shouldReturnToIdleWhenTheReportCannotBeShown() {
		final BuildReport unreadable = new BuildReport("\0", List.of(), List.of(), List.of(), 0, Duration.ZERO);
		final BuildService service = serving(finishing(unreadable), Runnable::run);

		service.start(false);

		assertFalse(session.state().run().running());
		assertEquals("The build ended but its report could not be shown", session.state().note());
	}

	@Example
	public void shouldBuildTheProjectOpenWhenTheRunBegins() {
		final AtomicReference<BuildRequest> asked = new AtomicReference<>();
		final BuildService service = serving((request, trust, sink) -> {
			asked.set(request);
			return finishing(failed()).create(request, trust, sink);
		}, Runnable::run);

		service.start(false);

		assertEquals(session.state().workspace().orElseThrow(), asked.get().project());
	}

	@Example
	public void shouldRunOnlyAfterABuildThatSucceeded() {
		final BuildReport good = new BuildReport("demo.adf", List.of(), List.of(), List.of(), 7, Duration.ZERO);
		final BuildService service = serving(finishing(good), Runnable::run);

		session.change(state -> state.withRunning(false));
		service.start(true);

		assertTrue(session.state().running());
	}

	@Example
	public void shouldNotRunAfterABuildThatFailed() {
		final BuildService service = serving(finishing(failed()), Runnable::run);

		session.change(state -> state.withRunning(false));
		service.start(true);

		assertFalse(session.state().running());
	}

	private static BuildReport failed() {
		return new BuildReport("", List.of(), List.of(),
			List.of(new BuildWarning("", 0, "build cancelled")), 0, Duration.ZERO);
	}
}
