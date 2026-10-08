/*
    Copperhead
    Copyright © 2026 Adam Waldenberg

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
import java.util.Locale;
import org.copperhead.core.project.Project;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.ModalView;
import org.copperhead.ui.view.PairView;

final class ModalViews {
	private static final List<PairView> SHORTCUTS = List.of(
		new PairView("Play / pause", "F5"),
		new PairView("Step / step over", "F8 · F10"),
		new PairView("Toggle breakpoint on cursor line", "F9"),
		new PairView("Build ADF / build & run", "Ctrl+B · Ctrl+Shift+B"),
		new PairView("Switch tool", "Ctrl+1 – Ctrl+5"),
		new PairView("Show / hide timeline", "Ctrl+J"),
		new PairView("Light / dark mode", Menus.LIGHT_DARK),
		new PairView("Open menu", "click · hover to switch"),
		new PairView("Close menu / dialog", "Esc"));

	private ModalViews() {
	}

	static ModalView of(final StudioState state) {
		final Project project = state.project();
		final String kind = project.kind().name().toLowerCase(Locale.ROOT);

		return switch (state.modal()) {
			case NONE -> new ModalView(false, "", List.of(), List.of());
			case SHORTCUTS -> new ModalView(true, "Keyboard shortcuts", SHORTCUTS, List.of());
			case ABOUT -> new ModalView(true, "About Copperhead", List.of(), List.of(
				"Copperhead · Amiga demo & game studio",
				"vasm 1.9h · Shrinkler 4.7 · FS-UAE 3.1 core · OCS/ECS/AGA",
				"Project: " + project.file() + " · " + project.parts().size() + " parts · "
					+ project.sources().size() + " sources",
				"Copyright © 2026 Adam Waldenberg · GNU General Public License, version 3"));
			case SETTINGS -> new ModalView(true, "Project settings", List.of(
				new PairView("Project", project.file() + " (" + kind + ")"),
				new PairView("Target", state.target().label()),
				new PairView("Video", "PAL · 50 Hz · 320×256"),
				new PairView("Music", project.module()),
				new PairView("Output", project.adf() + " · 880K DD")), List.of());
		};
	}
}
