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

@ApplicationScoped
public class SpriteController {
	private final ProjectSession session;

	@Inject
	public SpriteController(final ProjectSession session) {
		this.session = session;
	}

	@Action("sprite-paint")
	public Regions onClickCell(final Form form) {
		final int cell = Integer.parseInt(form.get("cell"));

		return Screens.regions(session.change(state -> state.paintSprite(cell)), Region.TOOL);
	}

	@Action("sprite-colour")
	public Regions onClickColour(final Form form) {
		final int colour = Integer.parseInt(form.get("colour"));

		return Screens.regions(session.change(state -> state.pickSpriteColour(colour)), Region.TOOL);
	}

	@Action("sprite-clear")
	public Regions onClickClear() {
		return Screens.regions(session.change(StudioState::clearSprite), Region.TOOL);
	}
}
