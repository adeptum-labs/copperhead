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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ToolStoreTest extends ScratchTest {
	private static final String SHA = "c84b2de1cbb87831795fe64a85c5d9a7002a766e3a7c30b0a2d7d5e99d878f49";

	@Example
	public void shouldFindNothingBeforeAToolIsInstalled() {
		assertEquals(Optional.empty(), store().find(PinnedSource.VASM, SHA));
	}

	@Example
	public void shouldKeepAnInstalledToolUnderItsNameAndHash() throws IOException {
		final Path installed = store().install(PinnedSource.VASM, SHA, built("first"));

		assertEquals(scratch().resolve("tools/vasm").resolve(SHA).resolve("vasmm68k_mot"), installed);
		assertTrue(Files.isExecutable(installed));
		assertEquals(Optional.of(installed), store().find(PinnedSource.VASM, SHA));
		assertEquals(List.of(installed), listed(installed.getParent()));
	}

	@Example
	public void shouldReplaceAToolInstalledBefore() throws IOException {
		store().install(PinnedSource.SHRINKLER, SHA, built("first"));
		final Path installed = store().install(PinnedSource.SHRINKLER, SHA, built("second"));

		assertEquals("second", Files.readString(installed));
		assertEquals(Path.of("Shrinkler"), installed.getFileName());
	}

	@Example
	public void shouldNotTakeAFileThatCannotRunForATool() throws IOException {
		final Path folder = Files.createDirectories(scratch().resolve("tools/vasm").resolve(SHA));

		Files.writeString(folder.resolve("vasmm68k_mot"), "not runnable");
		assertEquals(Optional.empty(), store().find(PinnedSource.VASM, SHA));
	}

	@Example
	public void shouldTakeOnlyAHashForAFolderName() throws IOException {
		final Path binary = built("tool");

		assertEquals(Optional.empty(), store().find(PinnedSource.VASM, "../../bin"));
		assertThrows(IllegalArgumentException.class, () -> store().install(PinnedSource.VASM, "../../bin", binary));
	}

	@Example
	public void shouldTreatAHashInCapitalsAsTheSameHash() throws IOException {
		final String capitals = SHA.toUpperCase(Locale.ROOT);
		final Path installed = store().install(PinnedSource.VASM, capitals, built("first"));

		assertEquals(scratch().resolve("tools/vasm").resolve(SHA).resolve("vasmm68k_mot"), installed);
		assertEquals(Optional.of(installed), store().find(PinnedSource.VASM, capitals));
		assertEquals(Optional.of(installed), store().find(PinnedSource.VASM, SHA));
	}

	private ToolStore store() {
		return new ToolStore(scratch().resolve("tools"));
	}

	private Path built(final String text) throws IOException {
		return Files.writeString(scratch().resolve(text), text);
	}
}
