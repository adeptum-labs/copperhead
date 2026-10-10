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

	private Processes() {
	}

	static int run(final String name, final List<String> command, final Path directory, final Duration timeout,
		final Consumer<String> output) throws IOException, InterruptedException {

		final Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
			.start();
		final Thread reader = Thread.ofVirtual().start(() -> forward(process, output));

		try {
			if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				throw new IOException(name + " timed out after " + timeout.toSeconds() + " s");
			}
			reader.join(DRAIN);
			return process.exitValue();
		} finally {
			process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
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
}
