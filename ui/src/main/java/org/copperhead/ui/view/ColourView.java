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

/** A colour register and its value. The channels reach the stylesheet as percentages of the full four bits. */
public record ColourView(int index, String hex, String css, int red, int green, int blue, boolean selected) {
	private static final int FULL = 15;
	private static final int PERCENT = 100;

	public String name() {
		return "COLOR%02d".formatted(index);
	}

	public String number() {
		return "%02d".formatted(index);
	}

	public String vals() {
		return Vals.of("index", index);
	}

	public String swatch() {
		return "--swatch:" + css;
	}

	public String channels() {
		return "--red:%d%%;--green:%d%%;--blue:%d%%".formatted(red * PERCENT / FULL, green * PERCENT / FULL,
			blue * PERCENT / FULL);
	}
}
