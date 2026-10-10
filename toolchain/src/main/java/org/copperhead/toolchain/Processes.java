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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs a program to completion, handing on every line it prints on either stream as it comes. A program that
 * outlives its timeout, or whose caller is interrupted, is killed together with every process it started: make and
 * the compilers it runs would otherwise go on after the build was stopped.
 */
final class Processes {
	private static final Duration DRAIN = Duration.ofSeconds(5);
	private static final File NO_INPUT = new File("/dev/null");

	private Processes() {
	}

	/**
	 * The program reads nothing from its input. {@code output} is called from another thread, so it must be
	 * thread-safe, and is never called after this method has returned. A {@code RuntimeException} from it stops the
	 * program and fails the run with an {@code IOException}.
	 */
	static int run(final String name, final List<String> command, final Path directory, final Duration timeout,
		final Consumer<String> output) throws IOException, InterruptedException {

		return run(name, command, directory, timeout, DRAIN, output);
	}

	/** The drain is how long to wait for the rest of the output once the program has exited. */
	static int run(final String name, final List<String> command, final Path directory, final Duration timeout,
		final Duration drain, final Consumer<String> output) throws IOException, InterruptedException {

		final Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
			.redirectInput(ProcessBuilder.Redirect.from(NO_INPUT)).start();
		final GatedOutput gated = new GatedOutput(output, () -> kill(process));
		final Thread reader = Thread.ofVirtual().start(() -> forward(process, gated));

		try {
			if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				throw new IOException(name + " timed out after " + describe(timeout));
			}
			reader.join(drain);
			final RuntimeException failure = gated.failure();

			if (failure != null) {
				throw new IOException(name + " output handler failed: " + failure, failure);
			}
			return process.exitValue();
		} finally {
			kill(process);
			gated.close();
			closeOutputOf(process);
		}
	}

	private static String describe(final Duration timeout) {
		return timeout.toMillis() % 1000 == 0 ? timeout.toSeconds() + " s" : timeout.toMillis() + " ms";
	}

	/* The descendants are listed before their parent dies: afterwards init owns them and they cannot be found. */
	private static void kill(final Process process) {
		final List<ProcessHandle> descendants = process.descendants().toList();

		process.destroyForcibly();
		descendants.forEach(ProcessHandle::destroyForcibly);
	}

	/* A program left running in the background may hold the pipe open, and the reader blocked in it, for good. */
	private static void closeOutputOf(final Process process) {
		try {
			process.getInputStream().close();
		} catch (final IOException e) {
			/* Nothing is left to read, so a failure to close loses nothing. */
		}
	}

	private static void forward(final Process process, final Consumer<String> output) {
		try (BufferedReader lines = process.inputReader()) {
			lines.lines().forEach(output);
		} catch (final IOException | UncheckedIOException e) {
			/* Killing the program closes its output under this reader; the lines read until then went out
			   already. */
		}
	}

	/** Passes lines on until closed or until the consumer fails, so that none arrives after the run is over. */
	private static final class GatedOutput implements Consumer<String> {
		private final Consumer<String> consumer;
		private final Runnable onFailure;
		private boolean open = true;
		private RuntimeException failure;

		private GatedOutput(final Consumer<String> consumer, final Runnable onFailure) {
			this.consumer = consumer;
			this.onFailure = onFailure;
		}

		@Override
		public synchronized void accept(final String line) {
			if (!open) {
				return;
			}
			try {
				consumer.accept(line);
			} catch (final RuntimeException e) {
				failure = e;
				open = false;
				onFailure.run();
			}
		}

		private synchronized RuntimeException failure() {
			return failure;
		}

		private synchronized void close() {
			open = false;
		}
	}
}
