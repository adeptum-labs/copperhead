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

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.copperhead.core.build.BuildLine;
import org.copperhead.core.build.BuildReport;
import org.copperhead.core.build.BuildWarning;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.Target;
import org.copperhead.toolchain.BuildEvent;
import org.copperhead.toolchain.Compiler;
import org.copperhead.toolchain.Fetcher;
import org.copperhead.toolchain.Prerequisites;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.toolchain.ToolStore;
import org.copperhead.toolchain.Toolchain;
import org.copperhead.toolchain.TrustPrompt;
import org.copperhead.toolchain.XdgDirs;
import org.copperhead.ui.TempDirectory;
import org.copperhead.ui.session.BuildRun.Phase;
import org.copperhead.ui.view.Modal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The question a run asks about a vasm source Copperhead does not pin, and who may answer it, and how often. */
public class TrustHandshakeTest {
	private static final String SHA256 = "5f".repeat(32);
	private static final String OTHER = "a1".repeat(32);
	private static final Duration PATIENT = Duration.ofSeconds(30);
	private static final Executor THREADS = command -> Thread.ofVirtual().start(command);
	private static final int ATTEMPTS = 40;
	private static final Function<String, Optional<Path>> NOWHERE = name -> Optional.empty();
	private static final Function<String, Optional<Path>> BUILD_TOOLS =
		name -> Optional.of(Path.of("/usr/bin", name));
	private static final String MAKEFILE = "all:\n\tprintf '#!/bin/sh\\necho vasm\\n' > vasmm68k_mot\n"
		+ "\tchmod +x vasmm68k_mot\n";

	private TempDirectory home;
	private Preferences preferences;
	private ProjectSession session;
	private Asking asking;
	private HttpServer proxy;

	/* A build that asks the question first, as the toolchain does on a mismatch, and keeps the answer it got. Its
	   cancel does not stop it, so only the answer to the question can. */
	private static final class Asking implements BuildService.Job {
		private final TrustPrompt trust;
		private final Consumer<BuildEvent> sink;
		private final CountDownLatch go = new CountDownLatch(1);
		private final CountDownLatch over = new CountDownLatch(1);
		private final AtomicReference<Boolean> outcome = new AtomicReference<>();

		private Asking(final TrustPrompt trust, final Consumer<BuildEvent> sink) {
			this.trust = trust;
			this.sink = sink;
		}

		@Override
		public void run() {
			try {
				go.await();
				outcome.set(trust.accept("vasm", "2.0g", SHA256));
			} catch (InterruptedException e) {
				outcome.set(false);
				Thread.currentThread().interrupt();
			}

			try {
				sink.accept(new BuildEvent.Finished(failed()));
			} finally {
				over.countDown();
			}
		}

		@Override
		public void cancel() {
		}
	}

	@BeforeTry
	public void open() throws Exception {
		home = new TempDirectory();
		preferences = new Preferences(home.path().resolve("preferences.json"));
		preferences.used(ProjectDirectory.create(home.path(), "demo", ProjectKind.DEMO, Target.A500_OCS).chp());
		session = new ProjectSession(preferences, () -> Instant.EPOCH);
	}

	@AfterTry
	public void close() throws Exception {
		if (proxy != null) {
			proxy.stop(0);
		}
		home.close();
	}

	private BuildService serving(final Executor executor) {
		final Toolchain toolchain = BuildService.toolchain(XdgDirs.of(Map.of(), home.path()), NOWHERE,
			home.path().resolve("os-release"));

		return new BuildService(session, preferences, toolchain, (request, trust, sink) -> {
			asking = new Asking(trust, sink);
			return asking;
		}, executor);
	}

	private BuildService asked() throws Exception {
		final BuildService service = serving(THREADS);

		service.start(false);
		asking.go.countDown();
		awaitQuestion();
		return service;
	}

	private void awaitQuestion() throws InterruptedException {
		final long deadline = System.nanoTime() + PATIENT.toNanos();

		while (session.state().run().trust().isEmpty()) {
			assertTrue(System.nanoTime() < deadline, "no question was asked");
			Thread.sleep(5);
		}
	}

	private void awaitIdle() throws InterruptedException {
		final long deadline = System.nanoTime() + PATIENT.toNanos();

		while (session.state().run().running()) {
			assertTrue(System.nanoTime() < deadline, "the run did not end");
			Thread.sleep(5);
		}
	}

	private void awaitOver() throws InterruptedException {
		assertTrue(asking.over.await(PATIENT.toMillis(), TimeUnit.MILLISECONDS), "the build did not end");
	}

	private static boolean took(final BuildService service, final boolean accepted) {
		try {
			service.answer(accepted, SHA256);
			return true;
		} catch (IllegalStateException e) {
			return false;
		}
	}

	@Example
	public void shouldKeepTheFirstAnswerWhenAnotherComesAfterIt() throws Exception {
		final BuildService service = asked();

		service.answer(true, SHA256);
		assertThrows(IllegalStateException.class, () -> service.answer(false, SHA256));
		awaitOver();

		assertEquals(true, asking.outcome.get());
		assertEquals(Optional.of(SHA256), preferences.trustedVasm());
		assertFalse(session.state().run().running());
	}

	@Example
	public void shouldStoreNothingForAQuestionAlreadyRefused() throws Exception {
		final BuildService service = asked();

		service.answer(false, SHA256);
		assertThrows(IllegalStateException.class, () -> service.answer(true, SHA256));
		awaitOver();

		assertEquals(false, asking.outcome.get());
		assertEquals(Optional.empty(), preferences.trustedVasm());
		assertFalse(session.state().run().running());
		assertEquals(Modal.NONE, session.state().modal());
	}

	@Example
	public void shouldStoreNothingWhenACancelTookTheQuestionFirst() throws Exception {
		final BuildService service = asked();

		service.cancel();
		assertThrows(IllegalStateException.class, () -> service.answer(true, SHA256));
		awaitOver();

		assertEquals(false, asking.outcome.get());
		assertEquals(Optional.empty(), preferences.trustedVasm());
		assertFalse(session.state().run().running());
	}

	@Example
	public void shouldEndIdleWhateverWinsWhenAnAcceptRacesACancel() throws Exception {
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			final boolean before = preferences.trustedVasm().isPresent();
			final BuildService service = asked();
			final CountDownLatch start = new CountDownLatch(1);
			final Thread accepting = Thread.ofVirtual().unstarted(() -> {
				await(start);
				took(service, true);
			});
			final Thread cancelling = Thread.ofVirtual().unstarted(() -> {
				await(start);
				service.cancel();
			});

			accepting.start();
			cancelling.start();
			start.countDown();
			accepting.join();
			cancelling.join();
			awaitOver();

			assertFalse(session.state().run().running());
			assertEquals(Phase.IDLE, session.state().run().phase());
			assertEquals(before || asking.outcome.get(), preferences.trustedVasm().isPresent());
		}
	}

	@Example
	public void shouldLetExactlyOneOfTwoConcurrentAnswersWin() throws Exception {
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			final BuildService service = asked();
			final CountDownLatch start = new CountDownLatch(1);
			final AtomicInteger won = new AtomicInteger();
			final AtomicInteger acceptedWon = new AtomicInteger();
			final Thread accepting = Thread.ofVirtual().unstarted(() -> {
				await(start);
				if (took(service, true)) {
					won.incrementAndGet();
					acceptedWon.incrementAndGet();
				}
			});
			final Thread refusing = Thread.ofVirtual().unstarted(() -> {
				await(start);
				if (took(service, false)) {
					won.incrementAndGet();
				}
			});

			accepting.start();
			refusing.start();
			start.countDown();
			accepting.join();
			refusing.join();
			awaitOver();

			assertEquals(1, won.get());
			assertEquals(acceptedWon.get() == 1, asking.outcome.get());
			assertFalse(session.state().run().running());
		}
	}

	@Example
	public void shouldFailAnAnswerWhenNothingWaitsAndStoreNothing() {
		final BuildService service = serving(THREADS);

		assertThrows(IllegalStateException.class, () -> service.answer(true, SHA256));
		assertEquals(Optional.empty(), preferences.trustedVasm());
	}

	@Example
	public void shouldLeaveTheQuestionAloneWhenAnAnswerNamesAnotherChecksum() throws Exception {
		final BuildService service = asked();

		service.answer(true, OTHER);

		assertEquals("That question is no longer the one asked", session.state().note());
		assertEquals(Phase.WAITING, session.state().run().phase());
		assertEquals(Optional.empty(), preferences.trustedVasm());
		service.answer(true, SHA256);
		awaitOver();
		assertEquals(Optional.of(SHA256), preferences.trustedVasm());
	}

	@Example
	public void shouldTakeTheQuestionDownWhenItsDialogIsClosed() throws Exception {
		final BuildService service = asked();

		service.refuse();
		service.refuse();
		awaitOver();

		assertEquals(false, asking.outcome.get());
		assertEquals(Modal.NONE, session.state().modal());
		assertEquals(Optional.empty(), preferences.trustedVasm());
	}

	@Example
	public void shouldRefuseTheQuestionAtOnceWhenTheCancelCameBeforeIt() throws Exception {
		final BuildService service = serving(THREADS);

		service.start(false);
		service.cancel();
		asking.go.countDown();
		awaitOver();

		assertEquals(false, asking.outcome.get());
		assertTrue(session.state().run().trust().isEmpty());
		assertFalse(session.state().run().running());
	}

	@Example
	public void shouldAskAgainInTheRunAfterACancelledOne() throws Exception {
		final BuildService service = serving(THREADS);

		service.start(false);
		service.cancel();
		asking.go.countDown();
		awaitOver();
		service.start(false);
		asking.go.countDown();
		awaitQuestion();

		assertEquals(Phase.WAITING, session.state().run().phase());
		service.refuse();
		awaitOver();
	}

	@Example
	public void shouldReleaseAWaitingBuildWhenTheStudioCloses() throws Exception {
		final BuildService service = asked();

		service.shutDown();

		assertEquals(false, asking.outcome.get());
		assertFalse(session.state().run().running());
		assertEquals(Optional.empty(), preferences.trustedVasm());
	}

	@Example
	public void shouldStopAFetchCancelledBeforeItsThreadStarted() {
		final AtomicReference<Runnable> held = new AtomicReference<>();
		final BuildService service = serving(held::set);

		service.fetch();
		service.cancel();
		held.get().run();

		final List<String> lines = session.state().run().log().stream().map(BuildLine::text).toList();

		assertEquals(List.of("fetch cancelled"), lines);
		assertFalse(session.state().run().running());
	}

	@Example
	public void shouldRememberTheVasmAFetchWasAskedAboutOnceItIsAccepted() throws Exception {
		final BuildService service = fetching();

		service.fetch();
		awaitQuestion();
		final String sha256 = session.state().run().trust().orElseThrow().sha256();

		assertEquals("9.9z", session.state().run().trust().orElseThrow().version());
		service.answer(true, sha256);
		awaitIdle();

		assertEquals(Optional.of(sha256), preferences.trustedVasm());
		assertTrue(texts().stream().anyMatch(text -> text.startsWith("vasm 2.0f at ")), texts().toString());
		assertTrue(service.tools().getFirst().found().isPresent());
	}

	@Example
	public void shouldFetchNothingMoreOnceTheVasmItAskedAboutIsRefused() throws Exception {
		final BuildService service = fetching();

		service.fetch();
		awaitQuestion();
		service.answer(false, session.state().run().trust().orElseThrow().sha256());
		awaitIdle();

		assertEquals(Optional.empty(), preferences.trustedVasm());
		assertTrue(texts().contains("the vasm source was refused"), texts().toString());
		assertTrue(service.tools().getFirst().found().isEmpty());
		assertEquals(Modal.NONE, session.state().modal());
	}

	private List<String> texts() {
		return session.state().run().log().stream().map(BuildLine::text).toList();
	}

	/* The vasm download goes through a proxy of the test's own that hands out an archive which is not the pinned. */
	private BuildService fetching() throws IOException {
		final byte[] archive = vasmArchive("9.9z");
		final InetAddress loopback = InetAddress.getLoopbackAddress();

		proxy = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
		proxy.createContext("/", exchange -> {
			exchange.sendResponseHeaders(200, archive.length);
			try (OutputStream body = exchange.getResponseBody()) {
				body.write(archive);
			}
		});
		proxy.start();

		final HttpClient client = HttpClient.newBuilder()
			.proxy(ProxySelector.of(new InetSocketAddress(loopback, proxy.getAddress().getPort()))).build();
		final Toolchain toolchain = new Toolchain(new ToolStore(home.path().resolve("tools")),
			new Fetcher(client, PATIENT), new Compiler(PATIENT),
			new Prerequisites(BUILD_TOOLS, home.path().resolve("os-release")), NOWHERE,
			home.path().resolve("work"));
		final Path shrinkler = Files.writeString(home.path().resolve("shrinkler"), "#!/bin/sh\n");

		Files.setPosixFilePermissions(shrinkler, PosixFilePermissions.fromString("rwxr-xr-x"));
		preferences.tool("shrinkler", Optional.of(shrinkler));
		return new BuildService(session, preferences, toolchain, BuildService.jobs(toolchain, PATIENT), THREADS);
	}

	private static byte[] vasmArchive(final String version) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GzipCompressorOutputStream(bytes))) {
			entry(tar, new TarArchiveEntry("vasm/"), "");
			entry(tar, new TarArchiveEntry("vasm/Makefile"), MAKEFILE);
			entry(tar, new TarArchiveEntry("vasm/vasm.c"), "#define _VER \"vasm " + version + "\"\n");
		}

		return bytes.toByteArray();
	}

	private static void entry(final TarArchiveOutputStream tar, final TarArchiveEntry entry, final String text)
		throws IOException {

		final byte[] content = text.getBytes(StandardCharsets.UTF_8);

		entry.setSize(content.length);
		tar.putArchiveEntry(entry);
		tar.write(content);
		tar.closeArchiveEntry();
	}

	private static void await(final CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static BuildReport failed() {
		return new BuildReport("", List.of(), List.of(),
			List.of(new BuildWarning("", 0, "build cancelled")), 0, Duration.ZERO);
	}
}
