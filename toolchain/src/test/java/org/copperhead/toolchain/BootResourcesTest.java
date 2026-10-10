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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BootResourcesTest {
	private static final String KEPT = "b14625af050ad0156a660778c2b56555ea929a66be12ba094dbf1d9ab0a85431";

	@Example
	public void shouldShipTheDecompressorTheProvenanceRecords() throws IOException {
		assertEquals(KEPT, TarFixture.sha256(resource("ShrinklerDecompress.S")));
		assertTrue(text("ShrinklerDecompress.S.provenance").contains("kept       sha256 " + KEPT));
	}

	@Example
	public void shouldKeepOnlyTheDataDecompressorWithItsLicence() throws IOException {
		final String decompressor = text("ShrinklerDecompress.S");

		assertTrue(decompressor.startsWith("; Copyright 1999-2022 Aske Simon Christensen.\n"));
		assertTrue(decompressor.contains("; modified or as is, for any legal purpose.\n"));
		assertTrue(decompressor.contains("\nShrinklerDecompress:\n"));
		assertFalse(decompressor.contains("ShrinklerLoad"));
	}

	@Example
	public void shouldLoadWithWhatTheBuildDefines() throws IOException {
		final String loader = text("bootblock.s");

		assertAll(List.of("\tdc.b\t\"DOS\",0\n", "\tif\tCRUNCHED\n", "\tmove.l\t#PAYLOAD_LEN,IO_LENGTH(a2)\n",
			"\tlea\tSTAGE,a0\n", "\tlea\tLOAD,a1\n", "\tjmp\tLOAD\n", "\tinclude\t\"ShrinklerDecompress.S\"\n")
			.stream().map(line -> () -> assertTrue(loader.contains(line), line)));
		assertTrue(loader.contains("DECRUNCH_STACK\t\t=\t4096\n"), "the build reserves 4096 bytes for it");
	}

	private static byte[] resource(final String name) throws IOException {
		try (InputStream in = BootResourcesTest.class.getResourceAsStream("/boot/" + name)) {
			return in.readAllBytes();
		}
	}

	private static String text(final String name) throws IOException {
		return new String(resource(name), StandardCharsets.UTF_8);
	}
}
