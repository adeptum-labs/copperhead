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

import java.util.Locale;

/** A row of the folder browser: the folder above, a folder inside, or a project file to open. */
public record EntryView(String label, Kind kind, String post, String vals) {
	public enum Kind {
		UP,
		FOLDER,
		PROJECT
	}

	public String modifier() {
		return "modal__entry--" + kind.name().toLowerCase(Locale.ROOT);
	}
}
