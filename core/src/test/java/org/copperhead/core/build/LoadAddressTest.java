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

package org.copperhead.core.build;

import java.util.stream.Stream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LoadAddressTest {
	@Example
	public void shouldReadTheAddressInEachWayItIsWritten() {
		assertEquals(0x20000, LoadAddress.parse("$20000"));
		assertEquals(0x20000, LoadAddress.parse("0x20000"));
		assertEquals(0x20000, LoadAddress.parse("131072"));
		assertEquals(0x20000, LoadAddress.parse(" $00020000 "));
	}

	@Example
	public void shouldWriteUpperCaseHexAfterADollar() {
		assertEquals("$20000", LoadAddress.format(0x20000));
		assertEquals("$400", LoadAddress.format(0x400));
		assertEquals("$FFFFFC", LoadAddress.format(0xFF_FFFC));
	}

	@Example
	public void shouldRefuseWhatIsNoAddress() {
		assertAll(Stream.of("", "$", "0x", "twenty", "20000h", "$-400", "-1024", "+1024", "$123456789",
			"12345678901").map(text -> () -> assertThrows(IllegalArgumentException.class,
				() -> LoadAddress.parse(text), text)));
	}

	@Example
	public void shouldRefuseAnAddressNoProgramCanStartAt() {
		assertEquals("load address $3FC is not a multiple of 4 between $400 and $FFFFFC",
			assertThrows(IllegalArgumentException.class, () -> LoadAddress.parse("$3FC")).getMessage());
		assertThrows(IllegalArgumentException.class, () -> LoadAddress.parse("$1000000"));
		assertThrows(IllegalArgumentException.class, () -> LoadAddress.parse("$20002"));
		assertThrows(IllegalArgumentException.class, () -> LoadAddress.parse("$FFFFFFFF"));
		assertThrows(IllegalArgumentException.class, () -> LoadAddress.format(0x20001));
		assertThrows(IllegalArgumentException.class, () -> LoadAddress.format(-4));
	}

	@Property
	public void shouldReadBackWhatItWrites(@ForAll("addresses") final int address) {
		assertEquals(address, LoadAddress.parse(LoadAddress.format(address)));
		assertEquals(address, LoadAddress.parse(Integer.toString(address)));
		assertEquals(address, LoadAddress.parse("0x" + Integer.toHexString(address)));
	}

	@Provide
	public Arbitrary<Integer> addresses() {
		return Arbitraries.integers().between(0x400 / 4, 0xFF_FFFC / 4).map(slot -> slot * 4);
	}
}
