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

package org.copperhead.ui.controller;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.copperhead.ui.session.ProjectSession;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.Region;
import org.copperhead.ui.view.Regions;
import org.copperhead.web.action.Action;
import org.copperhead.web.action.Form;

/**
 * The editor's side on the server: which source is open, where its cursor is, and the breakpoints. The page keeps
 * the text being edited; what it is sent is what is on disk.
 */
@ApplicationScoped
public class EditorController {
	private static final String NAME = "name";
	private static final String LINE = "line";

	private final ProjectSession session;

	@Inject
	public EditorController(final ProjectSession session) {
		this.session = session;
	}

	@Action("file-open")
	public Regions onClickTab(final Form form) {
		final String name = Workspaces.source(Workspaces.open(session.state()), form.get(NAME));

		return Screens.regions(session.change(state -> state.openFile(name)), Region.TOOL);
	}

	/* The editor moves its own cursor; the studio only remembers where, for F9 and for the next time it is drawn. */
	@Action("cursor")
	public void onMoveCursor(final Form form) {
		final int line = Integer.parseInt(form.get(LINE));

		session.change(state -> state.moveCursor(line));
	}

	@Action("breakpoint")
	public Regions onClickGutter(final Form form) {
		final int line = Integer.parseInt(form.get(LINE));

		return Screens.regions(session.change(state -> state.toggleBreakpoint(line)), Region.TOOL);
	}

	@Action("breakpoint-cursor")
	public Regions onToggleBreakpoint(final Form form) {
		final StudioState state = session.state();
		final int line = form.has(LINE) ? Integer.parseInt(form.get(LINE)) : state.cursor();

		return Screens.regions(session.change(changed -> changed.moveCursor(line).toggleBreakpoint(line)),
			Region.TOOL);
	}

	@Action("breakpoints-clear")
	public Regions onClickClearBreakpoints() {
		return Screens.regions(session.change(StudioState::clearBreakpoints), Region.TOOL);
	}

	@Action("goto-warning")
	public Regions onClickWarning() {
		return Screens.regions(session.change(StudioState::showWarning), Region.MENUS, Region.RAIL, Region.TOOL);
	}
}
