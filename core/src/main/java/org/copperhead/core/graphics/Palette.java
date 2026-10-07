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

/** The colour registers a picture is shown through, COLOR00 first. */
public record Palette(List<AmigaColor> colors) {
	public Palette {
		colors = List.copyOf(colors);
	}

	public AmigaColor color(final int index) {
		return colors.get(index);
	}

	public int size() {
		return colors.size();
	}
}
