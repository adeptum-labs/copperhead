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
import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TarballTest extends ScratchTest {
	@Example
	public void shouldUnpackTheTreeAndHandBackItsTopDirectory() throws Exception {
		final Path top = Tarball.extract(archive(new TarFixture().directory("tool").file("tool/Makefile", "all:\n")
			.directory("tool/src").file("tool/src/main.c", "int main(void) { return 0; }\n")
			.executable("tool/configure", "#!/bin/sh\n").link("tool/current", "src")), into());

		assertEquals(into().toRealPath().resolve("tool"), top);
		assertEquals("all:\n", Files.readString(top.resolve("Makefile")));
		assertEquals("int main(void) { return 0; }\n", Files.readString(top.resolve("current/main.c")));
		assertTrue(Files.isExecutable(top.resolve("configure")));
		assertFalse(Files.isExecutable(top.resolve("Makefile")));
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
	public void shouldRefuseALinkThatLeadsOutOfTheFolder() throws Exception {
		assertEquals("unsafe entry tool/up", refusal(new TarFixture().directory("tool").link("tool/up", "../../")
			.file("tool/up/escaped.s", "evil\n")));
		assertEquals("unsafe entry tool/etc", refusal(new TarFixture().directory("tool").link("tool/etc", "/etc")));
		assertFalse(Files.exists(scratch().resolve("escaped.s")));
	}

	@Example
	public void shouldRefuseToWriteThroughALinkInsideTheFolder() throws Exception {
		assertEquals("unsafe entry tool/here/main.c", refusal(new TarFixture().directory("tool")
			.directory("tool/src").link("tool/here", "src").file("tool/here/main.c", "int x;\n")));
	}

	@Example
	public void shouldRefuseAnArchiveWithoutASingleTopDirectory() throws Exception {
		assertEquals("archive does not hold a single top directory",
			refusal(new TarFixture().file("Makefile", "all:\n").file("other/README", "hi\n")));
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
}
