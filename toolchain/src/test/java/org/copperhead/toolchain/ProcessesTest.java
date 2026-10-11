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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProcessesTest extends ScratchTest {
	private static final Duration PATIENT = Duration.ofSeconds(20);
	private static final Duration QUICK = Duration.ofMillis(300);
	private static final Duration SETTLE = Duration.ofSeconds(3);
	private static final Duration SHORT_DRAIN = Duration.ofMillis(200);

	@Example
	public void shouldGiveTheProgramNothingToReadFromItsInput() throws Exception {
		final Instant start = Instant.now();

		assertEquals(0, Processes.run("cat", List.of("cat"), scratch(), PATIENT, line -> { }));
		assertTrue(Duration.between(start, Instant.now()).compareTo(Duration.ofSeconds(3)) < 0);
	}

	@Example
	public void shouldHandOnBothStreamsOfTheProgram() throws Exception {
		final List<String> lines = new CopyOnWriteArrayList<>();

		final int exit = Processes.run("sh", List.of("sh", "-c", "echo out; echo err >&2; exit 4"), scratch(),
			PATIENT, lines::add);

		assertEquals(4, exit);
		assertTrue(lines.containsAll(List.of("out", "err")), lines.toString());
	}

	@Example
	public void shouldKillTheWholeTreeOfAProgramThatTakesTooLong() throws Exception {
		final String marker = marker();

		final IOException late = assertThrows(IOException.class,
			() -> Processes.run("sh", sleeper(marker), scratch(), QUICK, line -> { }));

		assertEquals("sh timed out after 300 ms", late.getMessage());
		assertEquals(List.of(), survivors(marker));
	}

	@Example
	public void shouldNameWholeSecondsInTheTimeoutMessage() throws Exception {
		final String marker = marker();

		final Duration second = Duration.ofSeconds(1);

		assertEquals("sh timed out after 1 s", assertThrows(IOException.class,
			() -> Processes.run("sh", sleeper(marker), scratch(), second, line -> { })).getMessage());
		assertEquals(List.of(), survivors(marker));
	}

	@Example
	public void shouldKillTheWholeTreeWhenTheCallerIsInterrupted() throws Exception {
		final String marker = marker();
		final AtomicReference<Throwable> thrown = new AtomicReference<>();
		final Thread caller = new Thread(() -> {
			try {
				Processes.run("sh", sleeper(marker), scratch(), PATIENT, line -> { });
			} catch (final IOException | InterruptedException e) {
				thrown.set(e);
			}
		});

		caller.start();
		awaitSurvivors(marker);
		caller.interrupt();
		caller.join(SETTLE.toMillis());

		assertInstanceOf(InterruptedException.class, thrown.get());
		assertEquals(List.of(), survivors(marker));
	}

	@Example
	public void shouldStopHandingOnOutputOnceTheProgramHasExited() throws Exception {
		final List<String> lines = new CopyOnWriteArrayList<>();
		final Path written = scratch().resolve("late-written");
		final Instant start = Instant.now();

		/* The late child goes on past a closed pipe, so the file says when its line has been written. */
		final int exit = Processes.run("sh", List.of("sh", "-c", "(trap '' PIPE; sleep 1; echo late; touch '"
			+ written + "') & echo early"), scratch(), PATIENT, SHORT_DRAIN, lines::add);

		assertTrue(Duration.between(start, Instant.now()).compareTo(Duration.ofSeconds(3)) < 0);
		await(() -> Files.exists(written));

		assertEquals(0, exit);
		assertEquals(List.of("early"), lines);
	}

	@Example
	public void shouldFailWhenTheOutputHandlerFailsAndKillTheProgram() throws Exception {
		final String marker = marker();
		final Instant start = Instant.now();

		final IOException failed = assertThrows(IOException.class, () -> Processes.run("sh",
			List.of("sh", "-c", "echo first; sleep " + marker), scratch(), PATIENT, line -> {
				throw new IllegalStateException("no room for " + line);
			}));

		assertTrue(failed.getMessage().contains("no room for first"), failed.getMessage());
		assertTrue(Duration.between(start, Instant.now()).compareTo(SETTLE) < 0);
		assertEquals(List.of(), survivors(marker));
	}

	private static String marker() {
		return "30." + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L) + "1";
	}

	private static List<String> sleeper(final String marker) {
		return List.of("sh", "-c", "sleep " + marker + " & wait");
	}

	private static void awaitSurvivors(final String marker) throws InterruptedException {
		for (int attempt = 0; attempt < 100 && alive(marker).isEmpty(); attempt++) {
			Thread.sleep(20);
		}
		assertFalse(alive(marker).isEmpty());
	}

	private static void await(final BooleanSupplier condition) throws InterruptedException {
		final Instant until = Instant.now().plus(PATIENT);

		while (!condition.getAsBoolean()) {
			assertTrue(Instant.now().isBefore(until), "the condition never came");
			Thread.sleep(20);
		}
	}

	private static List<String> survivors(final String marker) throws InterruptedException {
		List<String> found = alive(marker);

		for (int attempt = 0; attempt < 50 && !found.isEmpty(); attempt++) {
			Thread.sleep(20);
			found = alive(marker);
		}
		return found;
	}

	private static List<String> alive(final String marker) {
		return ProcessHandle.allProcesses().filter(ProcessHandle::isAlive)
			.map(handle -> handle.info().commandLine().orElse("")).filter(line -> line.contains(marker))
			.toList();
	}
}
