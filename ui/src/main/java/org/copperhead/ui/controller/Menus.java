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

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.copperhead.core.build.BuildOption;
import org.copperhead.core.project.ProjectLayout;
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
	/* The page works out the theme to switch to, since it is the page that wears it. */
	static final String NEXT_THEME = "js:{theme: document.documentElement.dataset.theme === 'light' ? 'dark' : 'light'}";
	/* The editor's unsaved buffers travel along with every command that must not lose them. */
	static final String BUFFERS = Vals.spread("copperhead.editor.buffers({})");
	private static final String BUFFER = Vals.spread("copperhead.editor.buffer()");

	private static final String NO_KEYS = "";
	private static final String NO_VALS = "";
	private static final String TOOL = "tool";
	private static final String MODAL = "modal";
	private static final String BUILD_OPTION = "build-option";
	private static final String BREAKPOINT = "breakpoint-cursor";
	private static final String AT_CURSOR = Vals.spread("copperhead.editor.cursor()");
	private static final String KIND = "kind";

	private Menus() {
	}

	static MenusView of(final StudioState state) {
		final String chp = state.workspace().map(directory -> ProjectLayout.chp(directory.file().name()))
			.orElse("no project");

		return new MenusView(chp, List.of(file(state), edit(state), view(state), build(state), debug(state),
			help()));
	}

	private static MenuView file(final StudioState state) {
		final boolean idle = !state.run().running();

		return new MenuView("File", Stream.of(
			Stream.of(
				when(idle, action("New project…", "Ctrl+N", "project-new", NO_VALS)),
				when(idle, action("Open project…", "Ctrl+O", "project-open", NO_VALS))),
			state.environment().recent().stream().map(chp -> when(idle, recent(state, chp))),
			Stream.of(
				project(state, action("Save", "Ctrl+S", "file-save", BUFFER))
					.withSeparated(true),
				project(state, action("Save all", "Ctrl+Shift+S", "file-save-all", BUFFERS)),
				later("Import asset…", "Ctrl+I").withSeparated(true),
				building(state, action("Export ADF…", "Ctrl+Shift+E", "build", BUFFERS)),
				project(state, action("Project settings…", NO_KEYS, MODAL, Vals.of(KIND, Modal.SETTINGS)))
					.withSeparated(true)))
			.flatMap(items -> items).toList());
	}

	private static MenuView edit(final StudioState state) {
		return new MenuView("Edit", List.of(
			project(state, editor("Undo", "Ctrl+Z", "undo")),
			project(state, editor("Redo", "Ctrl+Shift+Z", "redo")),
			later("Cut", "Ctrl+X").withSeparated(true),
			later("Copy", "Ctrl+C"),
			later("Paste", "Ctrl+V"),
			project(state, editor("Find…", "Ctrl+F", "find")).withSeparated(true),
			project(state, editor("Go to line…", "Ctrl+G", "goto-line")),
			project(state, action("Toggle breakpoint", "F9", BREAKPOINT, AT_CURSOR)).withSeparated(true),
			action("Preferences…", "Ctrl+,", MODAL, Vals.of(KIND, Modal.PREFERENCES)).withSeparated(true)));
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
			new MenuItemView("Light mode", LIGHT_DARK, "theme", NEXT_THEME, false, true, ItemKind.THEME)));
	}

	private static MenuView build(final StudioState state) {
		return new MenuView("Build", List.of(
			building(state, action("Build ADF", "Ctrl+B", "build", BUFFERS)),
			building(state, action("Build & run", "Ctrl+Shift+B", "build-run", BUFFERS)),
			when(state.run().running(), action("Cancel build", NO_KEYS, "build-cancel", NO_VALS)),
			later("Clean", NO_KEYS),
			target(state, Target.A500_OCS).withSeparated(true),
			target(state, Target.A1200_AGA),
			option(state, BuildOption.SHRINKLER).withSeparated(true),
			option(state, BuildOption.SYMBOLS)));
	}

	private static MenuView debug(final StudioState state) {
		return new MenuView("Debug", List.of(
			action("Run / Pause", "F5", "run", NO_VALS),
			action("Step", "F8", "step", NO_VALS),
			action("Step over", "F10", "step-over", NO_VALS),
			action("Reset machine", "Shift+F5", "reset", NO_VALS),
			project(state, action("Toggle breakpoint", NO_KEYS, BREAKPOINT, AT_CURSOR)).withSeparated(true),
			project(state, action("Clear all breakpoints", NO_KEYS, "breakpoints-clear", NO_VALS)),
			action("Registers & memory", NO_KEYS, TOOL, Vals.of(TOOL, Tool.DEBUGGER)).withSeparated(true),
			action("Copper list", NO_KEYS, TOOL, Vals.of(TOOL, Tool.DEBUGGER))));
	}

	private static MenuView help() {
		return new MenuView("Help", List.of(
			later("Hardware reference · OCS/ECS/AGA", NO_KEYS),
			later("68000 instruction set", NO_KEYS),
			later("Copper cookbook", NO_KEYS),
			action("Keyboard shortcuts", "Ctrl+/", MODAL, Vals.of(KIND, Modal.SHORTCUTS)).withSeparated(true),
			action("About Copperhead", NO_KEYS, MODAL, Vals.of(KIND, Modal.ABOUT)).withSeparated(true)));
	}

	/* A project switch carries the unsaved buffers, so the studio can ask before they are left behind. */
	private static MenuItemView recent(final StudioState state, final Path chp) {
		final String vals = Vals.of(ProjectDialogs.PATH, chp);

		return action("Open recent · " + chp.getFileName() + " · " + chp.getParent(), NO_KEYS, "project-recent",
			state.workspace().isPresent() ? Vals.spread("copperhead.editor.buffers(" + vals + ")") : vals);
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

	private static MenuItemView project(final StudioState state, final MenuItemView item) {
		return when(state.workspace().isPresent(), item);
	}

	private static MenuItemView building(final StudioState state, final MenuItemView item) {
		return when(BuildViews.buildable(state), item);
	}

	private static MenuItemView when(final boolean available, final MenuItemView item) {
		return available ? item : item.withKind(ItemKind.OFF);
	}

	private static MenuItemView action(final String label, final String keys, final String action, final String vals) {
		return new MenuItemView(label, keys, action, vals, false, false, ItemKind.ACTION);
	}

	private static MenuItemView editor(final String label, final String keys, final String command) {
		return new MenuItemView(label, keys, command, NO_VALS, false, false, ItemKind.EDITOR);
	}

	private static MenuItemView later(final String label, final String keys) {
		return new MenuItemView(label, keys, NO_VALS, NO_VALS, false, false, ItemKind.LATER);
	}
}
