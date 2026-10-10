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
import java.util.concurrent.CopyOnWriteArrayList;
import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompilerTest extends ScratchTest {
	private static final Duration PATIENT = Duration.ofMinutes(1);
	private static final Duration IMPATIENT = Duration.ofMillis(500);
	private static final Duration SLEEP = Duration.ofSeconds(30);
	private static final String WRITES_TOOL = "all:\n\tprintf '#!/bin/sh\\necho built for $(CPU)\\n' > tool\n"
		+ "\tchmod +x tool\n";

	@Example
	public void shouldBuildTheTreeWithItsArgumentsAndHandBackTheProgram() throws Exception {
		final Path tree = unpacked(WRITES_TOOL);
		final List<String> log = new CopyOnWriteArrayList<>();

		final Path tool = new Compiler(PATIENT).build(tree, List.of("CPU=m68k"), "tool", log::add);

		assertEquals(tree.resolve("tool"), tool);
		assertTrue(Files.isExecutable(tool));
		assertEquals("#!/bin/sh\necho built for m68k\n", Files.readString(tool));
		assertEquals("make CPU=m68k", log.getFirst());
		assertTrue(log.stream().anyMatch(line -> line.startsWith("printf")), log.toString());
	}

	@Example
	public void shouldFailWhenMakeFails() throws Exception {
		final Path tree = unpacked("all:\n\texit 3\n");

		assertEquals("make failed with exit code 2", assertThrows(IOException.class,
			() -> new Compiler(PATIENT).build(tree, List.of(), "tool", line -> { })).getMessage());
	}

	@Example
	public void shouldFailWhenMakeLeavesNoProgram() throws Exception {
		final Path tree = unpacked("all:\n\ttrue\n");

		assertEquals("make did not produce tool", assertThrows(IOException.class,
			() -> new Compiler(PATIENT).build(tree, List.of(), "tool", line -> { })).getMessage());
	}

	@Example
	public void shouldStopABuildThatTakesTooLong() throws Exception {
		final Path tree = unpacked("all:\n\tsleep " + SLEEP.toSeconds() + "\n");
		final Instant start = Instant.now();

		final IOException late = assertThrows(IOException.class,
			() -> new Compiler(IMPATIENT).build(tree, List.of(), "tool", line -> { }));

		assertTrue(late.getMessage().contains("timed out"), late.getMessage());
		assertTrue(Duration.between(start, Instant.now()).compareTo(SLEEP) < 0);
	}

	private Path unpacked(final String makefile) throws IOException {
		return Tarball.extract(new TarFixture().directory("tool").file("tool/Makefile", makefile)
			.write(scratch().resolve("tool.tar.gz")), scratch().resolve("src"));
	}
}
