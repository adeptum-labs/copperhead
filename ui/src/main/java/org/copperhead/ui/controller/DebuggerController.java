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
import org.copperhead.ui.view.Regions;
import org.copperhead.web.action.Action;

@ApplicationScoped
public class DebuggerController {
	private final ProjectSession session;

	@Inject
	public DebuggerController(final ProjectSession session) {
		this.session = session;
	}

	@Action("step")
	public Regions onClickStep() {
		return Screens.playback(session.change(StudioState::step));
	}

	/* The canned run calls no subroutine it could step over, so stepping over is one step until the emulator
	   runs real code. */
	@Action("step-over")
	public Regions onClickStepOver() {
		return Screens.playback(session.change(StudioState::step));
	}
}
