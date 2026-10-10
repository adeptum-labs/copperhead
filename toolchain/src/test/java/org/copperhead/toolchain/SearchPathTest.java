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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SearchPathTest extends ScratchTest {
	@Example
	public void shouldFindTheFirstExecutableOfThatName() throws IOException {
		final Path first = program("first", "make", "rwxr-xr-x");
		final String path = String.join(File.pathSeparator, "", first.getParent().toString(), "  ",
			program("second", "make", "rwxr-xr-x").getParent().toString());

		assertEquals(Optional.of(first), SearchPath.of(path).apply("make"));
	}

	@Example
	public void shouldPassOverAFileThatCannotRun() throws IOException {
		final Path runnable = program("second", "cc", "rwxr-xr-x");
		final Path idle = program("first", "cc", "rw-r--r--");
		final String path = idle.getParent() + File.pathSeparator + runnable.getParent();

		assertEquals(Optional.of(runnable), SearchPath.of(path).apply("cc"));
		assertEquals(Optional.empty(), SearchPath.of(path).apply("c++"));
	}

	private Path program(final String folder, final String name, final String permissions) throws IOException {
		final Path file = Files.createDirectories(scratch().resolve(folder)).resolve(name);

		Files.writeString(file, "#!/bin/sh\n");
		Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(permissions));
		return file;
	}
}
