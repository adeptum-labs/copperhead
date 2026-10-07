/*
    Copperhead
    Copyright © 2026 Adam Waldenberg

    This program is free software: you can redistribute it and/or modify it under the terms of the
    GNU General Public License as published by the Free Software Foundation, version 3 of the License.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
    even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along with this program.
    If not, see <http://www.gnu.org/licenses/>.
 */

package org.copperhead.core.graphics;

import java.util.List;
import net.jqwik.api.Example;
import org.copperhead.core.sample.SampleMachine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CopperInstructionTest {
	private static String decode(final int first, final int second) {
		return new CopperInstruction(0x3F800, first, second).decode();
	}

	@Example
	public void shouldNameTheRegisterAMoveWrites() {
		assertEquals("MOVE BPLCON0,$1200", decode(0x0100, 0x1200));
		assertEquals("MOVE COLOR00,$000", decode(0x0180, 0x0000));
		assertEquals("MOVE COLOR00,$A3F", decode(0x0180, 0x0A3F));
		assertEquals("MOVE COLOR17,$FFF", decode(0x01A2, 0x0FFF));
	}

	@Example
	public void shouldGiveTheAddressOfARegisterItHasNoNameFor() {
		assertEquals("MOVE $096,$8020", decode(0x0096, 0x8020));
	}

	@Example
	public void shouldReadTheBeamPositionAWaitWaitsFor() {
		assertEquals("WAIT vp=$30", decode(0x3001, 0xFFFE));
		assertEquals("WAIT vp=$30 hp=$20", decode(0x3021, 0xFFFE));
	}

	@Example
	public void shouldRecogniseTheEndOfTheList() {
		assertEquals("END", decode(0xFFFF, 0xFFFE));
	}

	@Example
	public void shouldShowWhereItLivesAndTheWordsItIsMadeOf() {
		final CopperInstruction move = new CopperInstruction(0x3F800, 0x0100, 0x1200);

		assertEquals("$3F800", move.location());
		assertEquals("0100 1200", move.words());
	}

	@Example
	public void shouldLayTheListOutFourBytesApart() {
		final CopperList list = CopperList.of(0x3F800, 0x0100, 0x1200, 0xFFFF, 0xFFFE);

		assertEquals(List.of(0x3F800, 0x3F804),
			list.instructions().stream().map(CopperInstruction::address).toList());
		assertThrows(IllegalArgumentException.class, () -> CopperList.of(0x3F800, 0x0100));
	}

	@Example
	public void shouldEndTheSampleListWithEnd() {
		assertEquals("END", SampleMachine.copperList().instructions().getLast().decode());
		assertEquals(7, SampleMachine.copperList().instructions().size());
	}
}
