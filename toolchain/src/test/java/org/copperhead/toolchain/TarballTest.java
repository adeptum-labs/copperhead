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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Example;
import org.apache.commons.compress.archivers.tar.TarConstants;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TarballTest extends ScratchTest {
	private static final String TOO_MUCH = "archive holds too many entries or too many bytes";
	private static final String MALFORMED = "archive is malformed";

	@Example
	public void shouldUnpackTheTreeAndHandBackItsTopDirectory() throws Exception {
		final Path top = Tarball.extract(archive(new TarFixture().directory("tool").file("tool/Makefile", "all:\n")
			.directory("tool/src").file("tool/src/main.c", "int main(void) { return 0; }\n")
			.executable("tool/configure", "#!/bin/sh\n")), into());

		assertEquals(into().toRealPath().resolve("tool"), top);
		assertEquals("all:\n", Files.readString(top.resolve("Makefile")));
		assertEquals("int main(void) { return 0; }\n", Files.readString(top.resolve("src/main.c")));
		assertTrue(Files.isExecutable(top.resolve("configure")));
		assertFalse(Files.isExecutable(top.resolve("Makefile")));
	}

	@Example
	public void shouldNotKeepASetuidBit() throws Exception {
		final Path top = Tarball.extract(archive(new TarFixture().directory("tool").mode("tool/su", "x", 04777)),
			into());

		assertEquals(0, (int) Files.getAttribute(top.resolve("su"), "unix:mode") & 07000);
	}

	@Example
	public void shouldRefuseAnEntryThatClimbsOutOfTheFolder() throws Exception {
		assertEquals("unsafe entry tool/../../escaped.s",
			refusal(new TarFixture().directory("tool").file("tool/../../escaped.s", "evil\n")));
		assertFalse(Files.exists(scratch().resolve("escaped.s")));
	}

	@Example
	public void shouldRefuseAnEntryWithAnAbsoluteName() throws Exception {
		final Path outside = scratch().resolve("outside.s");

		assertTrue(refusal(new TarFixture().directory("tool").absolute(outside.toString(), "evil\n"))
			.startsWith("unsafe entry"));
		assertFalse(Files.exists(outside));
	}

	@Example
	public void shouldRefuseANameWithANulCharacter() throws Exception {
		assertTrue(refusal(new TarFixture().directory("tool").pax("path=tool/a\0b").file("tool/placeholder", "x"))
			.startsWith("unsafe entry"));
	}

	@Example
	public void shouldRefuseEveryKindOfLink() throws Exception {
		assertEquals("unsafe entry tool/up", refusal(new TarFixture().directory("tool").link("tool/up", "../../")
			.file("tool/up/escaped.s", "evil\n")));
		assertEquals("unsafe entry tool/etc", refusal(new TarFixture().directory("tool").link("tool/etc", "/etc")));
		assertEquals("unsafe entry tool/here", refusal(new TarFixture().directory("tool")
			.directory("tool/src").link("tool/here", "src").file("tool/here/main.c", "int x;\n")));
		assertEquals("unsafe entry tool/a", refusal(new TarFixture().directory("tool").link("tool/a", "..")
			.link("tool/z", "a/../..")));
		assertEquals("unsafe entry tool/h", refusal(new TarFixture().directory("tool").file("tool/f", "x")
			.hardLink("tool/h", "tool/f")));
		assertEquals("unsafe entry tool/s", refusal(new TarFixture().directory("tool").link("tool/s", ".")
			.hardLink("tool/h", "tool/s")));
		assertFalse(Files.exists(scratch().resolve("escaped.s")));
	}

	@Example
	public void shouldRefuseEverythingButPlainFilesAndFolders() throws Exception {
		assertEquals("unsafe entry tool/chr", refusal(new TarFixture().directory("tool")
			.special("tool/chr", TarConstants.LF_CHR)));
		assertEquals("unsafe entry tool/blk", refusal(new TarFixture().directory("tool")
			.special("tool/blk", TarConstants.LF_BLK)));
		assertEquals("unsafe entry tool/fifo", refusal(new TarFixture().directory("tool")
			.special("tool/fifo", TarConstants.LF_FIFO)));
	}

	@Example
	public void shouldRefuseSparseFilesOfEveryKind() throws Exception {
		assertEquals("unsafe entry tool/gnu", refusal(new TarFixture().directory("tool")
			.special("tool/gnu", TarConstants.LF_GNUTYPE_SPARSE)));
		assertEquals("unsafe entry tool/old", refusal(new TarFixture().directory("tool")
			.pax("GNU.sparse.size=300000000", "GNU.sparse.numblocks=1", "GNU.sparse.map=0,10")
			.file("tool/old", "x")));
		assertEquals("unsafe entry tool/new", refusal(new TarFixture().directory("tool")
			.pax("GNU.sparse.major=1", "GNU.sparse.minor=0", "GNU.sparse.realsize=300000000")
			.file("tool/new", "0\n".concat("\0".repeat(510)))));
	}

	@Example
	public void shouldRefuseMoreEntriesThanTheCap() throws Exception {
		assertEquals(TOO_MUCH, refusal(new TarFixture().directory("tool").file("tool/a", "a").file("tool/b", "b"), 2,
			1000));
	}

	@Example
	public void shouldRefuseMoreBytesThanTheCap() throws Exception {
		assertEquals(TOO_MUCH, refusal(new TarFixture().directory("tool").file("tool/a", "a".repeat(2000)), 10,
			1000));
		assertEquals(TOO_MUCH, refusal(new TarFixture().directory("tool").file("tool/a", "a".repeat(600))
			.file("tool/b", "b".repeat(600)), 10, 1000));
	}

	@Example
	public void shouldRefuseAHeaderTooBigToHoldInMemory() throws Exception {
		assertEquals(MALFORMED, refusal(new TarFixture().directory("tool").longNameOfSize(16 * 1024 * 1024)
			.file("tool/x", "x")));
	}

	@Example
	public void shouldRefuseAnEndlessRunOfHeaders() throws Exception {
		final TarFixture fixture = new TarFixture().directory("tool");

		for (int header = 0; header < 30_000; header++) {
			fixture.pax("a=b");
		}
		assertEquals(MALFORMED, refusal(fixture.file("tool/x", "x")));
	}

	@Example
	public void shouldRefuseAVeryLongName() throws Exception {
		final String refusal = refusal(new TarFixture().directory("tool").file("tool/" + "n".repeat(595), "x"));

		assertEquals("unsafe entry tool/" + "n".repeat(75), refusal);
	}

	@Example
	public void shouldRefuseAVeryDeepPathBeforeMakingItsFolders() throws Exception {
		final String deep = "tool/" + "d/".repeat(38) + "f";

		assertTrue(refusal(new TarFixture().directory("tool").file(deep, "x")).startsWith("unsafe entry tool/d/d/"));
		assertFalse(Files.exists(into().resolve("tool/d")));
	}

	@Example
	public void shouldUnpackAPathOfReasonableDepth() throws Exception {
		final String deep = "tool/" + "d/".repeat(18) + "f";
		final Path top = Tarball.extract(archive(new TarFixture().directory("tool").file(deep, "x")), into());

		assertEquals("x", Files.readString(top.resolve(deep.substring("tool/".length()))));
	}

	@Example
	public void shouldCloseTheArchiveItCannotReadAsGzip() throws Exception {
		final Path notGzip = Files.writeString(scratch().resolve("plain.tar.gz"), "not an archive at all");

		assertThrows(IOException.class, () -> Tarball.extract(notGzip, into()));
		assertFalse(openFiles().contains(notGzip.toRealPath()));
	}

	@Example
	public void shouldRefuseAnArchiveWithoutASingleTopDirectory() throws Exception {
		assertEquals("archive does not hold a single top directory",
			refusal(new TarFixture().file("Makefile", "all:\n").file("other/README", "hi\n")));
	}

	/* Where the system offers a list of open files (Linux); elsewhere nothing is listed and the check passes. */
	private static List<Path> openFiles() throws IOException {
		final Path descriptors = Path.of("/proc/self/fd");
		final List<Path> open = new ArrayList<>();

		if (Files.isDirectory(descriptors)) {
			for (final Path descriptor : listed(descriptors)) {
				try {
					open.add(Files.readSymbolicLink(descriptor));
				} catch (final NoSuchFileException closedMeanwhile) {
					continue;
				}
			}
		}
		return open;
	}

	private Path into() {
		return scratch().resolve("into");
	}

	private Path archive(final TarFixture fixture) throws IOException {
		return fixture.write(scratch().resolve("fixture.tar.gz"));
	}

	private String refusal(final TarFixture fixture) throws IOException {
		final Path archive = archive(fixture);

		return assertThrows(IOException.class, () -> Tarball.extract(archive, into())).getMessage();
	}

	private String refusal(final TarFixture fixture, final int maxEntries, final long maxBytes) throws IOException {
		final Path archive = archive(fixture);

		return assertThrows(IOException.class, () -> Tarball.extract(archive, into(), maxEntries, maxBytes))
			.getMessage();
	}
}
