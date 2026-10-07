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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.copperhead.ui.session.ProjectSession;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.Region;
import org.copperhead.ui.view.Regions;
import org.copperhead.web.action.Action;
import org.copperhead.web.action.Form;

@ApplicationScoped
public class CodeController {
	private final ProjectSession session;

	@Inject
	public CodeController(final ProjectSession session) {
		this.session = session;
	}

	@Action("file")
	public Regions onClickFile(final Form form) {
		return Screens.regions(session.change(state -> state.openFile(form.get("name"))), Region.TOOL);
	}

	@Action("cursor")
	public Regions onClickLine(final Form form) {
		final int line = Integer.parseInt(form.get("line"));

		return Screens.regions(session.change(state -> state.moveCursor(line)), Region.TOOL);
	}

	@Action("breakpoint")
	public Regions onClickLineNumber(final Form form) {
		final int line = Integer.parseInt(form.get("line"));

		return Screens.regions(session.change(state -> state.toggleBreakpoint(line)), Region.TOOL);
	}

	@Action("breakpoint-cursor")
	public Regions onToggleBreakpoint() {
		return Screens.regions(session.change(state -> state.toggleBreakpoint(state.cursor())), Region.TOOL);
	}

	@Action("breakpoints-clear")
	public Regions onClickClearBreakpoints() {
		return Screens.regions(session.change(StudioState::clearBreakpoints), Region.TOOL);
	}

	@Action("goto-warning")
	public Regions onClickWarning() {
		return Screens.regions(session.change(StudioState::showWarning), Region.MENUS, Region.RAIL, Region.TOOL,
			Region.STATUS);
	}
}
