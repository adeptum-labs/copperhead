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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;

/** A test that works in a folder of its own, made empty before every example and removed after it. */
public abstract class ScratchTest {
	private Path scratch;

	@BeforeTry
	public void makeScratch() throws IOException {
		scratch = Files.createTempDirectory("copperhead");
	}

	@AfterTry
	public void removeScratch() throws IOException {
		try (Stream<Path> made = Files.walk(scratch)) {
			for (final Path path : made.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		}
	}

	protected Path scratch() {
		return scratch;
	}

	protected static List<Path> listed(final Path directory) throws IOException {
		try (Stream<Path> files = Files.list(directory)) {
			return files.toList();
		}
	}
}
