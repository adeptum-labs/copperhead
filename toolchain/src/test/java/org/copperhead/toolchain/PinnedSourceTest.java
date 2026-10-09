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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PinnedSourceTest {
	@Example
	public void shouldNameTheProgramAfterTheLastPartOfItsPath() {
		assertEquals("vasmm68k_mot", PinnedSource.VASM.executable());
		assertEquals("Shrinkler", PinnedSource.SHRINKLER.executable());
	}

	@Example
	public void shouldPinTheArchivesThatWereReviewed() {
		assertEquals("c84b2de1cbb87831795fe64a85c5d9a7002a766e3a7c30b0a2d7d5e99d878f49",
			PinnedSource.VASM.sha256());
		assertEquals("513ac06190e6a5dcf3af8e5a7db2cd8f4ab22bba2ae7a4eeaae3616c450bd3a4",
			PinnedSource.SHRINKLER.sha256());
		assertEquals(List.of("CPU=m68k", "SYNTAX=mot"), PinnedSource.VASM.makeArgs());
	}

	@Example
	public void shouldKeepItsMakeArgumentsAsTheyWereGiven() {
		final List<String> args = new ArrayList<>(List.of("CPU=m68k"));
		final PinnedSource source = new PinnedSource("vasm", "2.0f", URI.create("http://localhost/vasm.tar.gz"),
			"00", args, "vasmm68k_mot");

		args.add("SYNTAX=mot");
		assertEquals(List.of("CPU=m68k"), source.makeArgs());
		assertThrows(UnsupportedOperationException.class, () -> source.makeArgs().add("-j"));
	}
}
