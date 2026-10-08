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
import org.copperhead.core.build.BuildOption;
import org.copperhead.core.project.Target;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.ItemKind;
import org.copperhead.ui.view.MenuItemView;
import org.copperhead.ui.view.MenuView;
import org.copperhead.ui.view.MenusView;
import org.copperhead.ui.view.Modal;
import org.copperhead.ui.view.Tool;
import org.copperhead.ui.view.Vals;

/** The menu bar: every command of the studio, with its shortcut and whether it is in effect. */
final class Menus {
	/* Linux desktops take Ctrl+Alt+T to open a terminal before the page ever sees it. */
	static final String LIGHT_DARK = "Ctrl+Shift+L";

	private static final String NO_KEYS = "";
	private static final String NO_VALS = "";
	private static final String TOOL = "tool";
	private static final String MODAL = "modal";
	private static final String BUILD_OPTION = "build-option";
	private static final String BREAKPOINT = "breakpoint-cursor";

	private Menus() {
	}

	static MenusView of(final StudioState state) {
		return new MenusView(state.project().file(), List.of(file(), edit(), view(state), build(state), debug(),
			help()));
	}

	private static MenuView file() {
		return new MenuView("File", List.of(
			later("New project…", "Ctrl+N"),
			later("Open project…", "Ctrl+O"),
			action("Open recent · nightfall.chp (demo)", NO_KEYS, "project", Vals.of("name", "nightfall")),
			action("Open recent · blitzball.chp (game)", NO_KEYS, "project", Vals.of("name", "blitzball")),
			later("Save", "Ctrl+S").withSeparated(true),
			later("Save all", "Ctrl+Shift+S"),
			later("Import asset…", "Ctrl+I").withSeparated(true),
			action("Export ADF…", "Ctrl+Shift+E", "build", NO_VALS),
			action("Project settings…", "Ctrl+,", MODAL, Vals.of("kind", Modal.SETTINGS)).withSeparated(true)));
	}

	private static MenuView edit() {
		return new MenuView("Edit", List.of(
			later("Undo", "Ctrl+Z"),
			later("Redo", "Ctrl+Shift+Z"),
			later("Cut", "Ctrl+X").withSeparated(true),
			later("Copy", "Ctrl+C"),
			later("Paste", "Ctrl+V"),
			later("Find…", "Ctrl+F").withSeparated(true),
			later("Go to line…", "Ctrl+L"),
			action("Toggle breakpoint", "F9", BREAKPOINT, NO_VALS).withSeparated(true)));
	}

	private static MenuView view(final StudioState state) {
		return new MenuView("View", List.of(
			tool(state, Tool.CODE, "Ctrl+1"),
			tool(state, Tool.SPRITES, "Ctrl+2"),
			tool(state, Tool.PALETTE, "Ctrl+3"),
			tool(state, Tool.DEBUGGER, "Ctrl+4"),
			tool(state, Tool.BUILD, "Ctrl+5"),
			action("Show timeline", "Ctrl+J", "timeline", NO_VALS).withChecked(state.timelineShown())
				.withSeparated(true),
			later("Focus emulator", "Ctrl+E"),
			new MenuItemView("Light mode", LIGHT_DARK, NO_VALS, NO_VALS, false, true, ItemKind.THEME)));
	}

	private static MenuView build(final StudioState state) {
		return new MenuView("Build", List.of(
			action("Build ADF", "Ctrl+B", "build", NO_VALS),
			action("Build & run", "Ctrl+Shift+B", "build-run", NO_VALS),
			later("Clean", NO_KEYS),
			target(state, Target.A500_OCS).withSeparated(true),
			target(state, Target.A1200_AGA),
			option(state, BuildOption.SHRINKLER).withSeparated(true),
			option(state, BuildOption.BOOTBLOCK),
			option(state, BuildOption.SYMBOLS)));
	}

	private static MenuView debug() {
		return new MenuView("Debug", List.of(
			action("Run / Pause", "F5", "run", NO_VALS),
			action("Step", "F8", "step", NO_VALS),
			action("Step over", "F10", "step-over", NO_VALS),
			action("Reset machine", "Shift+F5", "reset", NO_VALS),
			action("Toggle breakpoint", NO_KEYS, BREAKPOINT, NO_VALS).withSeparated(true),
			action("Clear all breakpoints", NO_KEYS, "breakpoints-clear", NO_VALS),
			action("Registers & memory", NO_KEYS, TOOL, Vals.of(TOOL, Tool.DEBUGGER)).withSeparated(true),
			action("Copper list", NO_KEYS, TOOL, Vals.of(TOOL, Tool.DEBUGGER))));
	}

	private static MenuView help() {
		return new MenuView("Help", List.of(
			later("Hardware reference · OCS/ECS/AGA", NO_KEYS),
			later("68000 instruction set", NO_KEYS),
			later("Copper cookbook", NO_KEYS),
			action("Keyboard shortcuts", "Ctrl+/", MODAL, Vals.of("kind", Modal.SHORTCUTS)).withSeparated(true),
			action("About Copperhead", NO_KEYS, MODAL, Vals.of("kind", Modal.ABOUT)).withSeparated(true)));
	}

	private static MenuItemView tool(final StudioState state, final Tool tool, final String keys) {
		return action(tool.label(), keys, TOOL, Vals.of(TOOL, tool)).withChecked(state.tool() == tool);
	}

	private static MenuItemView target(final StudioState state, final Target target) {
		return action("Target " + target.label(), NO_KEYS, "target", Vals.of("target", target.chipset()))
			.withChecked(state.target() == target);
	}

	private static MenuItemView option(final StudioState state, final BuildOption option) {
		return action(BuildViews.menuLabel(option), NO_KEYS, BUILD_OPTION, Vals.of("option", option))
			.withChecked(state.options().on(option));
	}

	private static MenuItemView action(final String label, final String keys, final String action, final String vals) {
		return new MenuItemView(label, keys, action, vals, false, false, ItemKind.ACTION);
	}

	private static MenuItemView later(final String label, final String keys) {
		return new MenuItemView(label, keys, NO_VALS, NO_VALS, false, false, ItemKind.LATER);
	}
}
