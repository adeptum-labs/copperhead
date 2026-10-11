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
import java.util.List;
import org.copperhead.core.project.ProjectLayout;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.ui.session.ProjectSession;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.Region;
import org.copperhead.ui.view.Regions;
import org.copperhead.web.action.Action;
import org.copperhead.web.action.Form;

/**
 * The editor's side on the server: which source is open, saving what the page edited, and the breakpoints, which
 * live in the project file. The page keeps the text being edited; what it is sent back is what is on disk.
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
		final String name = Workspaces.source(Workspaces.open(session.state()), form.require(NAME));

		return Screens.regions(session.change(state -> state.openFile(name)), Region.TOOL);
	}

	@Action("file-save")
	public Regions onSave(final Form form) {
		final ProjectDirectory directory = Workspaces.open(session.state());
		final String name = form.require(NAME);
		final String text = form.require("text");

		Buffers.owned(directory, form);

		final List<String> saved;

		try {
			saved = Buffers.save(directory, List.of(name), buffer -> text);
		} catch (Buffers.Failed e) {
			return e.answer(session);
		}

		return Screens.confirming(Screens.regions(session.change(state -> state.withNote("Saved " + name)),
			Region.TOOL), saved);
	}

	@Action("file-save-all")
	public Regions onSaveAll(final Form form) {
		final List<String> saved;

		try {
			saved = Buffers.save(Workspaces.open(session.state()), form);
		} catch (Buffers.Failed e) {
			return e.answer(session);
		}

		return Screens.confirming(Screens.regions(session.change(state -> state.withNote(saved.isEmpty()
			? "Nothing to save" : "Saved " + String.join(", ", saved))), Region.TOOL), saved);
	}

	/* The editor moves its own cursor; the studio only remembers where, for F9 and for the next time it is drawn. */
	@Action("cursor")
	public void onMoveCursor(final Form form) {
		final int line = Integer.parseInt(form.require(LINE));

		session.update(state -> state.moveCursor(line));
	}

	@Action("breakpoint")
	public Regions onClickGutter(final Form form) {
		final int line = Integer.parseInt(form.require(LINE));

		return Screens.regions(session.change(state -> Workspaces.saved(state.toggleBreakpoint(line))), Region.TOOL);
	}

	@Action("breakpoint-cursor")
	public Regions onToggleBreakpoint(final Form form) {
		final StudioState state = session.state();
		final int line = form.has(LINE) ? Integer.parseInt(form.get(LINE)) : state.cursor();

		return Screens.regions(session.change(changed -> Workspaces.saved(changed.moveCursor(line)
			.toggleBreakpoint(line))), Region.TOOL);
	}

	@Action("breakpoints-clear")
	public Regions onClickClearBreakpoints() {
		return Screens.regions(session.change(state -> Workspaces.saved(state.clearBreakpoints())), Region.TOOL);
	}

	@Action("goto-warning")
	public Regions onClickWarning() {
		return Screens.regions(session.change(StudioState::showWarning), Region.MENUS, Region.RAIL, Region.TOOL);
	}

	/* Nothing changed on disk since the page loaded the source, so nothing is drawn anew and the answer is empty.
	   A source that has since left the project is drawn anew too, without its tab. */
	@Action("source-check")
	public Regions onShowSource(final Form form) {
		final ProjectDirectory directory = Workspaces.open(session.state());
		final String name = form.require(NAME);

		if (!ProjectLayout.validSource(name)) {
			throw new IllegalArgumentException("The project holds no source " + name);
		}

		if (Workspaces.sources(directory).contains(name)
			&& Workspaces.read(() -> directory.modified(name)) == Long.parseLong(form.require("modified"))) {
			return null;
		}

		return Screens.regions(session.state(), Region.TOOL);
	}
}
