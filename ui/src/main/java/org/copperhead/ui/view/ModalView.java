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
import java.util.Locale;
import org.copperhead.web.action.View;

/**
 * The dialog over the studio, if one is open: a title, rows of name and value, the fields of a form, entries to
 * pick from, lines of text and the buttons that answer it. Each kind lays its rows out its own way: shortcuts name
 * the keys at the far side, settings give values beside names. The whole card is the form its buttons send.
 */
public record ModalView(Modal kind, String title, List<PairView> rows, List<String> lines, List<EntryView> entries,
	List<FieldView> fields, List<ButtonView> buttons) implements View {

	public ModalView {
		rows = List.copyOf(rows);
		lines = List.copyOf(lines);
		entries = List.copyOf(entries);
		fields = List.copyOf(fields);
		buttons = List.copyOf(buttons);
	}

	/** A dialog that only tells: rows and lines, closed with its close button. */
	public ModalView(final Modal kind, final String title, final List<PairView> rows, final List<String> lines) {
		this(kind, title, rows, lines, List.of(), List.of(), List.of());
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
