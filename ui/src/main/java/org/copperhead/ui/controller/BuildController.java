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
import java.util.function.Function;
import org.copperhead.core.build.BuildOption;
import org.copperhead.toolchain.ProjectDirectory;
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
	private static final Region[] ASKED = {Region.MENUS, Region.MACHINE, Region.TOOL, Region.MODAL};
	/* A build that ends may start the machine, so what shows it running is drawn again as well. */
	private static final Region[] ENDED = {Region.MENUS, Region.MACHINE, Region.TOOL, Region.EMULATOR,
		Region.TIMELINE};
	private static final String SHA256 = "sha256";

	private final ProjectSession session;
	private final BuildService builds;

	@Inject
	public BuildController(final ProjectSession session, final BuildService builds) {
		this.session = session;
		this.builds = builds;
	}

	@Action("build-option")
	public Regions onClickOption(final Form form) {
		final BuildOption option = BuildOption.valueOf(form.require("option"));

		return Screens.regions(session.change(state -> Workspaces.saved(state.toggleOption(option))), Region.MENUS,
			Region.TOOL);
	}

	/* What the editor has not saved yet goes to disk first, since the assembler reads the files. */
	@Action("build")
	public Regions onClickBuild(final Form form) {
		return build(form, false, state -> Screens.regions(state, HELD_BACK));
	}

	@Action("build-run")
	public Regions onClickBuildAndRun(final Form form) {
		return build(form, true, Screens::all);
	}

	/* A build that is refused writes nothing: the one running is reading these very files. A source the disk refused
	   would be assembled as it was before, so the build does not start. The breakpoints the page holds are kept with
	   the text saved for the build. */
	private Regions build(final Form form, final boolean andRun, final Function<StudioState, Regions> drawn) {
		final StudioState state = session.state();

		if (state.run().running()) {
			throw new IllegalStateException("A build is already running");
		}

		final ProjectDirectory directory = Workspaces.open(state);
		final Marks marks = Marks.of(directory, form, form::get);
		final List<String> saved;

		try {
			saved = Buffers.save(directory, form);
		} catch (Buffers.Failed e) {
			return e.answer(session);
		}

		session.update(changed -> Workspaces.saved(marks.placed(changed)));
		builds.start(andRun);
		return Screens.confirming(drawn.apply(session.state()), saved);
	}

	/*
	 * Asked every half second while a run goes on. The build tool shows the log as it grows; the other regions wait
	 * for the phase the poll last saw to change, so a question is drawn once when it comes up and taken down once
	 * when it is answered, and any other dialog the person has open stays.
	 */
	@Action("build-progress")
	public Regions onPoll(final Form form) {
		final StudioState state = session.state();
		final Phase seen = Phase.valueOf(form.require("phase"));
		final Phase now = state.run().phase();

		if (seen != now) {
			return Screens.regions(state, seen == Phase.WAITING || now == Phase.WAITING ? ASKED : ENDED);
		}

		return state.tool() == Tool.BUILD ? Screens.regions(state, Region.TOOL) : Screens.regions(state);
	}

	@Action("build-cancel")
	public Regions onClickCancel() {
		builds.cancel();
		return Screens.regions(session.state(), HELD_BACK);
	}

	@Action("trust-accept")
	public Regions onClickAccept(final Form form) {
		builds.answer(true, form.require(SHA256));
		return Screens.regions(session.state(), Region.MODAL, Region.TOOL);
	}

	@Action("trust-refuse")
	public Regions onClickRefuse(final Form form) {
		builds.answer(false, form.require(SHA256));
		return Screens.regions(session.state(), Region.MODAL, Region.TOOL);
	}

	@Action("toolchain-fetch")
	public Regions onClickFetch() {
		builds.fetch();
		return Screens.regions(session.state(), HELD_BACK);
	}
}
