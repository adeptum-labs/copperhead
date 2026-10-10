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

import java.util.List;
import org.copperhead.toolchain.PinnedSource;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.Modal;
import org.copperhead.ui.view.ModalView;
import org.copperhead.ui.view.PairView;

final class ModalViews {
	private static final ModalView CLOSED = new ModalView(Modal.NONE, "", List.of(), List.of());
	private static final List<PairView> SHORTCUTS = List.of(
		new PairView("Play / pause", "F5"),
		new PairView("Step / step over", "F8 · F10"),
		new PairView("Toggle breakpoint on cursor line", "F9"),
		new PairView("Save / save all", "Ctrl+S · Ctrl+Shift+S"),
		new PairView("Build ADF / build & run", "Ctrl+B · Ctrl+Shift+B"),
		new PairView("Switch tool", "Ctrl+1 – Ctrl+5"),
		new PairView("Show / hide timeline", "Ctrl+J"),
		new PairView("Light / dark mode", Menus.LIGHT_DARK),
		new PairView("Open menu", "click · hover to switch"),
		new PairView("Close menu / dialog", "Esc"));

	private ModalViews() {
	}

	/* The dialogs drawn from what their request brought close when drawn from the state alone, on a fresh page. */
	static ModalView of(final StudioState state) {
		return switch (state.modal()) {
			case NONE, NEW_PROJECT, OPEN_PROJECT, PREFERENCES, DISCARD, TRUST -> CLOSED;
			case SHORTCUTS -> new ModalView(Modal.SHORTCUTS, "Keyboard shortcuts", SHORTCUTS, List.of());
			case ABOUT -> new ModalView(Modal.ABOUT, "About Copperhead", List.of(), List.of(
				"Copperhead · Amiga demo & game studio",
				String.join(" · ", tool(PinnedSource.VASM), tool(PinnedSource.SHRINKLER), "FS-UAE 3.1 core",
					"OCS/ECS/AGA"),
				state.workspace().map(directory -> "Project: " + directory.chp()).orElse("No project open"),
				"Copyright © 2026 Adam Waldenberg and Adeptum AB · GNU General Public License, version 3"));
			case SETTINGS -> state.workspace().map(directory -> ProjectDialogs.settings(state,
				ProjectDialogs.settingsOf(directory.file()), "")).orElse(CLOSED);
		};
	}

	private static String tool(final PinnedSource source) {
		return source.tool() + " " + source.version();
	}
}
