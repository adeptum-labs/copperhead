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

package org.copperhead.ui.view;

/** A line of source as the editor shows it, with where the run and the cursor are. */
public record CodeLineView(int number, String label, String mnemonic, String operands, String comment,
	boolean breakpoint, boolean pc, boolean cursor) {

	public String vals() {
		return Vals.of("line", number);
	}

	/* The program counter outranks the cursor: where the run stopped matters more than where the eye is. */
	public String modifier() {
		if (pc) {
			return "code__line--pc";
		}

		return cursor ? "code__line--cursor" : "";
	}
}
