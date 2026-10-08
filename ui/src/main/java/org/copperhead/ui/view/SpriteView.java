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

import java.util.List;
import org.copperhead.web.action.View;

/**
 * The sprite being painted. Its three colours reach the stylesheet as custom properties, {@code --c1} to
 * {@code --c3}, so a cell names only the colour it holds.
 */
public record SpriteView(List<CellView> cells, List<SwatchView> swatches, String colours) implements View {
	public SpriteView {
		cells = List.copyOf(cells);
		swatches = List.copyOf(swatches);
	}

	@Override
	public String template() {
		return "fragments/sprite :: sprite";
	}
}
