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
import org.copperhead.core.project.Project;
import org.copperhead.core.project.Target;
import org.copperhead.core.sample.SampleProjects;
import org.copperhead.ui.session.ProjectSession;
import org.copperhead.ui.view.Modal;
import org.copperhead.ui.view.Region;
import org.copperhead.ui.view.Regions;
import org.copperhead.ui.view.Tool;
import org.copperhead.web.action.Action;
import org.copperhead.web.action.Form;

/** The commands of the window itself: which tool is shown, which project and machine, and the dialogs. */
@ApplicationScoped
public class WorkbenchController {
	private final ProjectSession session;

	@Inject
	public WorkbenchController(final ProjectSession session) {
		this.session = session;
	}

	@Action("tool")
	public Regions onClickTool(final Form form) {
		final Tool tool = Tool.valueOf(form.get("tool"));

		return Screens.regions(session.change(state -> state.withTool(tool)), Region.MENUS, Region.RAIL,
			Region.TOOL);
	}

	@Action("project")
	public Regions onClickProject(final Form form) {
		final Project project = SampleProjects.named(form.get("name"));

		return Screens.all(session.change(state -> state.switchTo(project)));
	}

	@Action("target")
	public Regions onClickTarget(final Form form) {
		final Target target = Target.chipset(form.get("target"));

		return Screens.all(session.change(state -> state.withTarget(target)));
	}

	@Action("modal")
	public Regions onClickModal(final Form form) {
		final Modal modal = Modal.valueOf(form.get("kind"));

		return Screens.regions(session.change(state -> state.withModal(modal)), Region.MODAL);
	}

	@Action("modal-close")
	public Regions onClickCloseModal() {
		return Screens.regions(session.change(state -> state.withModal(Modal.NONE)), Region.MODAL);
	}

	@Action("timeline")
	public Regions onClickTimeline() {
		return Screens.regions(session.change(state -> state.withTimelineShown(!state.timelineShown())),
			Region.MENUS, Region.TIMELINE);
	}
}
