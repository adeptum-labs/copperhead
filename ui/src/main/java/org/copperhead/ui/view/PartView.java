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

package org.copperhead.ui.view;

/** A part on the timeline; its place reaches the stylesheet as {@code --left}, {@code --width} and {@code --hue}. */
public record PartView(int index, String name, String range, String meta, String file, String place, boolean loops,
	boolean selected) {

	public String label() {
		return loops ? "↻ " + name : name;
	}

	public String vals() {
		return Vals.of("index", index);
	}

	public String modifier() {
		return (selected ? "block--selected " : "") + (loops ? "block--loop" : "");
	}
}
