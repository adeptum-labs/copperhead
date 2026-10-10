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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.copperhead.core.build.LoadAddress;
import org.copperhead.core.project.ProjectFile;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.ProjectLayout;
import org.copperhead.core.project.Target;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.ButtonView;
import org.copperhead.ui.view.ChoiceView;
import org.copperhead.ui.view.EntryView;
import org.copperhead.ui.view.FieldView;
import org.copperhead.ui.view.Modal;
import org.copperhead.ui.view.ModalView;
import org.copperhead.ui.view.PairView;
import org.copperhead.ui.view.Vals;

/**
 * The dialogs of the project: making one, opening one, leaving unsaved changes behind and its settings. All but the
 * settings are drawn from what their request brought, the folder looked in and what was typed so far.
 */
final class ProjectDialogs {
	static final String NAME = "name";
	static final String KIND = "kind";
	static final String TARGET = "target";
	static final String PARENT = "parent";
	static final String PATH = "path";
	static final String LOAD = "loadAddress";
	static final String ENTRY = "entry";
	static final String SAVE = "save";
	static final String DISCARD = "discard";
	static final String YES = "true";

	private static final String CHP = ".chp";
	private static final String HIDDEN = ".";
	private static final String HOME = "~";
	private static final String BROWSE = "/action/browse";
	private static final String CLOSE = "/action/modal-close";
	private static final String FIELDS = "copperhead.fields(this)";
	private static final String SRC = ProjectLayout.SRC + "/";
	private static final String SOURCE = ".s";

	private ProjectDialogs() {
	}

	/** What the New project dialog holds: the name typed, the kind and machine picked, the folder to make it in. */
	record Draft(String name, ProjectKind kind, Target target, Path parent) {
	}

	static ModalView create(final Path home, final Draft draft, final String error, final boolean editing) {
		final String kind = draft.kind().name();

		return new ModalView(Modal.NEW_PROJECT, "New project", List.of(),
			List.of(error.isEmpty() ? "Makes a folder of that name with src, gfx and build in it, and a first "
				+ "source that assembles and boots." : error),
			folders(home, draft.parent(), folder -> Vals.spread(FIELDS + ", ..." + Vals.of(PATH, folder))),
			List.of(FieldView.text(NAME, "Name", draft.name()),
				new FieldView(KIND, "Kind", kind, List.of(
					choice(ProjectKind.DEMO.name(), "Demo · copper bars until reset", kind),
					choice(ProjectKind.GAME.name(), "Game · a sprite the joystick moves", kind))),
				new FieldView(TARGET, "Machine", draft.target().chipset(), targets(draft.target())),
				FieldView.text(PARENT, "Folder", draft.parent().toString())),
			List.of(new ButtonView("Create", "/action/project-create",
				Vals.spread(editing ? "copperhead.editor.buffers(" + FIELDS + ")" : FIELDS), true),
				cancel()));
	}

	static ModalView open(final Path home, final Path folder, final String error, final boolean editing) {
		final Function<Path, String> opening = chp -> editing
			? Vals.spread("copperhead.editor.buffers(" + Vals.of(PATH, chp) + ")") : Vals.of(PATH, chp);
		final Stream<EntryView> projects = list(home, folder)
			.filter(path -> path.getFileName().toString().endsWith(CHP) && Files.isRegularFile(path))
			.map(chp -> new EntryView(chp.getFileName().toString(), EntryView.Kind.PROJECT,
				"/action/project-open", opening.apply(chp)));

		return new ModalView(Modal.OPEN_PROJECT, "Open project",
			List.of(new PairView("Folder", shown(home, folder))),
			List.of(error.isEmpty() ? "Pick a project file, or a folder to look in." : error),
			Stream.concat(folders(home, folder, path -> Vals.of(PATH, path)).stream(), projects).toList(),
			List.of(), List.of(cancel()));
	}

	/**
	 * Asks before the unsaved buffers are left behind, offering to save them, to drop them, or to stay. After a save
	 * the disk refused, the reason leads the dialog.
	 */
	static ModalView discard(final String action, final Map<String, String> pending, final List<String> dirty,
		final String failure) {

		final Map<String, String> saving = new LinkedHashMap<>(pending);
		final Map<String, String> dropping = new LinkedHashMap<>(pending);
		final String unsaved = listed(dirty) + (dirty.size() == 1 ? " has" : " have")
			+ " changes that are not saved.";

		saving.put(SAVE, YES);
		dropping.put(DISCARD, YES);

		return new ModalView(Modal.DISCARD, "Unsaved changes", List.of(),
			failure.isEmpty() ? List.of(unsaved) : List.of(failure, unsaved),
			List.of(), List.of(), List.of(
				new ButtonView("Save all", "/action/" + action,
					Vals.spread("copperhead.editor.buffers(" + Vals.of(saving) + ")"), true),
				new ButtonView("Discard", "/action/" + action, Vals.of(dropping), false), cancel()));
	}

	/** The settings of the open project, as saved or as typed when they were refused. */
	static ModalView settings(final StudioState state, final Map<String, String> values, final String error) {
		final ProjectDirectory directory = Workspaces.open(state);
		final ProjectFile file = directory.file();
		final String output = ProjectLayout.BUILD + "/" + ProjectLayout.adf(file.name()) + " · 880K DD";
		final String target = values.get(TARGET);

		return new ModalView(Modal.SETTINGS, "Project settings", List.of(
				new PairView("Kind", file.kind().name().toLowerCase(Locale.ROOT)),
				new PairView("Output", output)),
			error.isEmpty() ? List.of() : List.of(error), List.of(),
			List.of(FieldView.text(NAME, "Name", values.get(NAME)),
				new FieldView(TARGET, "Machine", target, targets(Target.chipset(target))),
				FieldView.text(LOAD, "Load address", values.get(LOAD)),
				new FieldView(ENTRY, "Entry file", values.get(ENTRY), Workspaces.sources(directory).stream()
					.filter(name -> name.endsWith(SOURCE))
					.map(name -> choice(SRC + name, SRC + name, values.get(ENTRY))).toList())),
			List.of(new ButtonView("Save", "/action/project-settings", Vals.spread(FIELDS), true), cancel()));
	}

	static Map<String, String> settingsOf(final ProjectFile file) {
		return Map.of(NAME, file.name(), TARGET, file.target().chipset(),
			LOAD, LoadAddress.format(file.loadAddress()), ENTRY, file.entry());
	}

	private static List<ChoiceView> targets(final Target picked) {
		return Stream.of(Target.values())
			.map(target -> choice(target.chipset(), target.label(), picked.chipset())).toList();
	}

	private static ChoiceView choice(final String value, final String label, final String picked) {
		return new ChoiceView(value, label, value.equals(picked));
	}

	private static ButtonView cancel() {
		return new ButtonView("Cancel", CLOSE, null, false);
	}

	/* The folder above, while there is one inside the home folder, then the folders inside, hidden ones left out. */
	private static List<EntryView> folders(final Path home, final Path folder, final Function<Path, String> vals) {
		final Stream<EntryView> up = folder.equals(home) ? Stream.empty()
			: Stream.of(new EntryView("..", EntryView.Kind.UP, BROWSE, vals.apply(folder.getParent())));

		return Stream.concat(up, list(home, folder).filter(Files::isDirectory)
			.filter(path -> !path.getFileName().toString().startsWith(HIDDEN))
			.map(path -> new EntryView(path.getFileName() + "/", EntryView.Kind.FOLDER, BROWSE,
				vals.apply(path)))).toList();
	}

	/*
	 * A folder that cannot be read lists as empty: the dialog still shows where it is and the way back up. An entry
	 * that is a link out of the home folder is not listed.
	 */
	private static Stream<Path> list(final Path home, final Path folder) {
		try (Stream<Path> inside = Files.list(folder)) {
			return inside.filter(path -> Workspaces.confined(home, path))
				.sorted(Comparator.comparing(ProjectDialogs::sortName)).toList().stream();
		} catch (IOException e) {
			return Stream.empty();
		}
	}

	private static String sortName(final Path path) {
		return path.getFileName().toString().toLowerCase(Locale.ROOT);
	}

	private static String shown(final Path home, final Path folder) {
		return folder.equals(home) ? HOME : HOME + "/" + home.relativize(folder);
	}

	private static String listed(final List<String> names) {
		return names.size() == 1 ? names.getFirst()
			: String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
	}
}
