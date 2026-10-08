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

import java.util.List;
import java.util.Locale;
import org.copperhead.web.action.View;

/**
 * The dialog over the studio, if one is open: a title and either rows of name and value or lines of text. Each
 * kind lays its rows out its own way: shortcuts name the keys at the far side, settings give values beside names.
 */
public record ModalView(Modal kind, String title, List<PairView> rows, List<String> lines) implements View {
	public ModalView {
		rows = List.copyOf(rows);
		lines = List.copyOf(lines);
	}

	public boolean open() {
		return kind != Modal.NONE;
	}

	public String modifier() {
		return "modal__rows--" + kind.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public String template() {
		return "fragments/modal :: modal";
	}
}
