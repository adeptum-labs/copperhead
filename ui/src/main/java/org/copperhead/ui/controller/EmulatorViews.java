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

import java.util.Locale;
import org.copperhead.core.project.Part;
import org.copperhead.core.project.Target;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.EmulatorView;

/** The emulator pane. Until the emulator runs, the meters show what the sample demo is known to use. */
final class EmulatorViews {
	private static final int RASTER_OCS = 60;
	private static final int RASTER_AGA = 38;
	private static final int CHIP_OCS = 79;
	private static final int CHIP_AGA = 40;
	private static final int BLITTER = 34;

	private EmulatorViews() {
	}

	static EmulatorView of(final StudioState state) {
		final boolean aga = state.target() == Target.A1200_AGA;
		final Part part = state.project().parts().get(state.project().partAt(state.position()));

		return new EmulatorView(state.target().label() + " · PAL 50Hz", state.running(),
			(state.project().name() + " · " + part.name()).toUpperCase(Locale.ROOT),
			"320×256 · 1 bpl + copper · " + (aga ? "24-bit AGA" : "12-bit OCS"), Clock.frames(state.position()),
			aga ? RASTER_AGA : RASTER_OCS, state.target().chipUsed(), aga ? CHIP_AGA : CHIP_OCS, BLITTER);
	}
}
