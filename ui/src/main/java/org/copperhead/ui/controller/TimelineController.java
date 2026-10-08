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
import java.time.Duration;
import org.copperhead.ui.session.ProjectSession;
import org.copperhead.ui.view.Region;
import org.copperhead.ui.view.Regions;
import org.copperhead.web.action.Action;
import org.copperhead.web.action.Form;

@ApplicationScoped
public class TimelineController {
	private static final Region[] MOVED = {Region.TIMELINE, Region.STATUS, Region.EMULATOR};

	private final ProjectSession session;

	@Inject
	public TimelineController(final ProjectSession session) {
		this.session = session;
	}

	@Action("seek")
	public Regions onClickRuler(final Form form) {
		final Duration at = Duration.ofMillis(Long.parseLong(form.get("at")));

		return Screens.regions(session.change(state -> state.seek(at)), MOVED);
	}

	@Action("part")
	public Regions onClickPart(final Form form) {
		final int index = Integer.parseInt(form.get("index"));

		return Screens.regions(session.change(state -> state.playPart(index)), MOVED);
	}

	@Action("exit")
	public Regions onClickExit(final Form form) {
		return Screens.regions(session.change(state -> state.takeExit(form.get("to"))), MOVED);
	}

	/* The page has played into the next part. Only the time has moved, and the session already keeps it, so nothing
	   is changed: the run is drawn where it now is, leaving the tool pane and its scroll alone. */
	@Action("catch-up")
	public Regions onPlayIntoNextPart() {
		return Screens.regions(session.state(), MOVED);
	}
}
