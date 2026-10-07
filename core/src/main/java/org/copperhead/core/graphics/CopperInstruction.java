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

/**
 * One instruction of a copper list: two words that either write a chipset register or wait for the beam.
 * A wait has the lowest bit of its first word set; a move has it clear.
 */
public record CopperInstruction(int address, int first, int second) {
	private static final int END_FIRST = 0xFFFF;
	private static final int END_SECOND = 0xFFFE;
	private static final int WAIT = 1;
	private static final int BYTE = 8;
	private static final int HORIZONTAL = 0xFE;
	private static final int REGISTER = 0x1FE;
	private static final int BPLCON0 = 0x100;
	private static final int COLOR00 = 0x180;
	private static final int COLOR31 = 0x1BE;
	private static final int COLOUR_BITS = 0xFFF;
	private static final int WORD = 2;

	public String location() {
		return "$%X".formatted(address);
	}

	public String words() {
		return "%04X %04X".formatted(first, second);
	}

	public String decode() {
		if (first == END_FIRST && second == END_SECOND) {
			return "END";
		}

		if ((first & WAIT) != 0) {
			return waitFor();
		}

		return "MOVE " + register() + "," + value();
	}

	private String waitFor() {
		final int vertical = first >>> BYTE;
		final int horizontal = first & HORIZONTAL;

		return horizontal == 0
			? "WAIT vp=$%02X".formatted(vertical)
			: "WAIT vp=$%02X hp=$%02X".formatted(vertical, horizontal);
	}

	private boolean colour() {
		final int register = first & REGISTER;

		return register >= COLOR00 && register <= COLOR31;
	}

	private String register() {
		final int register = first & REGISTER;

		if (register == BPLCON0) {
			return "BPLCON0";
		}

		return colour() ? "COLOR%02d".formatted((register - COLOR00) / WORD) : "$%03X".formatted(register);
	}

	private String value() {
		return colour() ? "$%03X".formatted(second & COLOUR_BITS) : "$%04X".formatted(second);
	}
}
