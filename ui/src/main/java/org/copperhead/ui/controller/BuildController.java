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
import org.copperhead.core.build.BuildOption;
import org.copperhead.ui.session.BuildRun.Phase;
import org.copperhead.ui.session.BuildService;
import org.copperhead.ui.session.ProjectSession;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.Region;
import org.copperhead.ui.view.Regions;
import org.copperhead.ui.view.Tool;
import org.copperhead.web.action.Action;
import org.copperhead.web.action.Form;

/**
 * Builds and the toolchain they need. A build runs on while the page polls the status bar's control for how it
 * does; when it ends, the controls held back while it ran come back and the editor learns what it said.
 */
@ApplicationScoped
public class BuildController {
	private static final Region[] HELD_BACK = {Region.MENUS, Region.MACHINE, Region.TOOL};

	private final ProjectSession session;
	private final BuildService builds;

	@Inject
	public BuildController(final ProjectSession session, final BuildService builds) {
		this.session = session;
		this.builds = builds;
	}

	@Action("build-option")
	public Regions onClickOption(final Form form) {
		final BuildOption option = BuildOption.valueOf(form.get("option"));

		return Screens.regions(session.change(state -> Workspaces.saved(state.toggleOption(option))), Region.MENUS,
			Region.TOOL);
	}

	/* What the editor has not saved yet goes to disk first, since the assembler reads the files. */
	@Action("build")
	public Regions onClickBuild(final Form form) {
		Buffers.save(Workspaces.open(session.state()), form);
		builds.start(false);
		return Screens.regions(session.state(), HELD_BACK);
	}

	@Action("build-run")
	public Regions onClickBuildAndRun(final Form form) {
		Buffers.save(Workspaces.open(session.state()), form);
		builds.start(true);
		return Screens.all(session.state());
	}

	/*
	 * Asked every half second while a run goes on. The build tool shows the log as it grows; the regions the run
	 * held back are drawn again once the phase the poll last saw has changed.
	 */
	@Action("build-progress")
	public Regions onPoll(final Form form) {
		final StudioState state = session.state();
		final Phase seen = Phase.valueOf(form.get("phase"));
		final Phase now = state.run().phase();

		if (seen != now) {
			return Screens.regions(state, HELD_BACK);
		}

		return state.tool() == Tool.BUILD ? Screens.regions(state, Region.TOOL) : Screens.regions(state);
	}

	@Action("build-cancel")
	public Regions onClickCancel() {
		builds.cancel();
		return Screens.regions(session.state(), HELD_BACK);
	}
}
