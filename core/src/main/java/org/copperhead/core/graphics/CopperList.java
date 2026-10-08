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

package org.copperhead.core.graphics;

import java.util.List;
import java.util.stream.IntStream;

/** A copper list as it lies in chip memory from its base address, one instruction every four bytes. */
public record CopperList(int base, List<CopperInstruction> instructions) {
	private static final int STRIDE = 4;

	public CopperList {
		instructions = List.copyOf(instructions);
	}

	public static CopperList of(final int base, final int... words) {
		if (words.length % 2 != 0) {
			throw new IllegalArgumentException("Copper instructions are pairs of words, not " + words.length);
		}

		return new CopperList(base, IntStream.range(0, words.length / 2)
			.mapToObj(i -> new CopperInstruction(base + i * STRIDE, words[2 * i], words[2 * i + 1])).toList());
	}
}
