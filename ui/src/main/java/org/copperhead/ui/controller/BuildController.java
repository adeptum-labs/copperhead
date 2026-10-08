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
import org.copperhead.ui.session.ProjectSession;
import org.copperhead.ui.view.Region;
import org.copperhead.ui.view.Regions;
import org.copperhead.web.action.Action;
import org.copperhead.web.action.Form;

@ApplicationScoped
public class BuildController {
	private final ProjectSession session;

	@Inject
	public BuildController(final ProjectSession session) {
		this.session = session;
	}

	@Action("build-option")
	public Regions onClickOption(final Form form) {
		final BuildOption option = BuildOption.valueOf(form.get("option"));

		return Screens.regions(session.change(state -> state.toggleOption(option)), Region.MENUS, Region.TOOL);
	}

	@Action("build")
	public Regions onClickBuild() {
		return Screens.regions(session.change(state -> state.build(false)), Region.TOOL, Region.STATUS);
	}

	@Action("build-run")
	public Regions onClickBuildAndRun() {
		return Screens.playback(session.change(state -> state.build(true)));
	}
}
