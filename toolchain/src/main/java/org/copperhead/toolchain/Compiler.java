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
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Builds an unpacked source tree with make and hands back the program it made. */
public final class Compiler {
	private static final String MAKE = "make";

	private final Duration timeout;

	public Compiler(final Duration timeout) {
		this.timeout = timeout;
	}

	public Path build(final Path sourceDir, final List<String> makeArgs, final String binary, final Consumer<String> log)
		throws IOException, InterruptedException {

		final List<String> command = Stream.concat(Stream.of(MAKE), makeArgs.stream()).toList();
		final Path built = sourceDir.resolve(binary);

		log.accept(String.join(" ", command));
		final int exit = Processes.run(MAKE, command, sourceDir, timeout, log);

		if (exit != 0) {
			throw new IOException("make failed with exit code " + exit);
		}
		if (!SearchPath.usable(built)) {
			throw new IOException("make did not produce " + binary);
		}
		return built;
	}
}
