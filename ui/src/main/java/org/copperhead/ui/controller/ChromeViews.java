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

import org.copperhead.core.project.Target;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.MachineView;
import org.copperhead.ui.view.RailView;

/** The frame around the tools: the machine controls in the title bar and the tool rail. */
final class ChromeViews {
	private ChromeViews() {
	}

	static MachineView machine(final StudioState state) {
		return new MachineView(state.target() == Target.A1200_AGA, state.running());
	}

	static RailView rail(final StudioState state) {
		return new RailView(state.tool(), state.target().chipFree());
	}
}
