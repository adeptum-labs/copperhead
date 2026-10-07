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

import java.util.Locale;

/**
 * A part of the window that an action can replace. Each is drawn with its id and swapped in out of band,
 * so an action answers with the regions it changed and the page needs no target of its own.
 */
public enum Region {
	MENUS,
	MACHINE,
	RAIL,
	TOOL,
	EMULATOR,
	TIMELINE,
	STATUS,
	MODAL;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}
