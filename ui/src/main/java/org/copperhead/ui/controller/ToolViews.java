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

package org.copperhead.ui.controller;

import java.util.List;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.PendingView;
import org.copperhead.ui.view.TabView;
import org.copperhead.ui.view.ToolView;

/** The tool pane: its tabs, what it says of itself and the tool chosen on the rail. */
final class ToolViews {
	private ToolViews() {
	}

	static ToolView of(final StudioState state) {
		return switch (state.tool()) {
			default -> new ToolView(List.of(title(state.tool().label())), "",
				new PendingView(state.tool().label()));
		};
	}

	/** The one tab of a tool that shows a single thing, such as a sprite or the debugger. */
	static TabView title(final String label) {
		return new TabView(label, null, null, true, false);
	}
}
