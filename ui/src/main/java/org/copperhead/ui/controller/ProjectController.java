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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.copperhead.core.build.LoadAddress;
import org.copperhead.core.project.ProjectFile;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.ProjectLayout;
import org.copperhead.core.project.Target;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.ui.controller.ProjectDialogs.Draft;
import org.copperhead.ui.session.Preferences;
import org.copperhead.ui.session.ProjectSession;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.Modal;
import org.copperhead.ui.view.ModalView;
import org.copperhead.ui.view.Regions;
import org.copperhead.web.action.Action;
import org.copperhead.web.action.Form;

/**
 * Making, opening and setting up projects. Every folder the dialogs reach and every project they make stays inside
 * the person's home folder; a switch with unsaved buffers asks first whether to save them, drop them or stay.
 */
@ApplicationScoped
public class ProjectController {
	private static final String NAME_RULE = "A name starts with a letter or digit and holds only letters, digits, "
		+ "- and _, up to 64 of them.";
	private static final String SOURCE = ".s";
	private static final String BUILD_RUNNING = "A build is running; wait until it ends";

	private final ProjectSession session;
	private final Preferences preferences;
	private final Path home;

	@Inject
	public ProjectController(final ProjectSession session, final Preferences preferences) {
		this(session, preferences, Path.of(System.getProperty("user.home")));
	}

	public ProjectController(final ProjectSession session, final Preferences preferences, final Path home) {
		this.session = session;
		this.preferences = preferences;
		this.home = home.toAbsolutePath().normalize();
	}

	/** The dialog starts beside the open project, so a new one lands next to it; with none open, at home. */
	@Action("project-new")
	public Regions onClickNew() {
		final StudioState state = session.change(changed -> changed.withModal(Modal.NEW_PROJECT));
		final Draft draft = new Draft("", ProjectKind.DEMO, Target.A500_OCS, near(state));

		return Screens.dialog(state, ProjectDialogs.create(home, draft, "", state.workspace().isPresent()));
	}

	@Action("project-open")
	public Regions onClickOpen(final Form form) {
		if (!form.has(ProjectDialogs.PATH)) {
			final StudioState state = session.change(changed -> changed.withModal(Modal.OPEN_PROJECT));

			return Screens.dialog(state, ProjectDialogs.open(home, near(state), "", editing()));
		}

		final Path chp = inside(form.require(ProjectDialogs.PATH));

		return switchTo(form, "project-open", Map.of(ProjectDialogs.PATH, chp.toString()),
			() -> ProjectSession.open(chp),
			error -> ProjectDialogs.open(home, folderOf(chp), error, editing()));
	}

	@Action("project-recent")
	public Regions onClickRecent(final Form form) {
		final Path chp = Path.of(form.require(ProjectDialogs.PATH)).toAbsolutePath().normalize();

		if (!preferences.recent().contains(chp)) {
			throw new IllegalArgumentException(chp + " is not a project opened lately");
		}

		return switchTo(form, "project-recent", Map.of(ProjectDialogs.PATH, chp.toString()),
			() -> ProjectSession.open(chp),
			error -> ProjectDialogs.open(home, home, error, editing()));
	}

	@Action("project-create")
	public Regions onClickCreate(final Form form) {
		final Draft draft = new Draft(form.require(ProjectDialogs.NAME).strip(),
			ProjectKind.valueOf(form.require(ProjectDialogs.KIND)),
			Target.chipset(form.require(ProjectDialogs.TARGET)), inside(form.require(ProjectDialogs.PARENT)));
		final Function<String, ModalView> refused = error -> ProjectDialogs.create(home, draft, error, editing());

		if (!ProjectLayout.validName(draft.name())) {
			return Screens.dialog(session.change(state -> state.withModal(Modal.NEW_PROJECT)),
				refused.apply(NAME_RULE));
		}

		final Map<String, String> pending = new LinkedHashMap<>();

		pending.put(ProjectDialogs.NAME, draft.name());
		pending.put(ProjectDialogs.KIND, draft.kind().name());
		pending.put(ProjectDialogs.TARGET, draft.target().chipset());
		pending.put(ProjectDialogs.PARENT, draft.parent().toString());

		return switchTo(form, "project-create", pending,
			() -> ProjectDirectory.create(draft.parent(), draft.name(), draft.kind(), draft.target()), refused);
	}

	/* Each listing is read from the disk as it is asked for; the folder is the one the request names. */
	@Action("browse")
	public Regions onBrowse(final Form form) {
		final Path folder = inside(form.require(ProjectDialogs.PATH));
		final StudioState state = session.state();
		final boolean editing = state.workspace().isPresent();

		if (!Files.isDirectory(folder)) {
			throw new IllegalArgumentException(folder + " is not a folder");
		}

		return switch (state.modal()) {
			case NEW_PROJECT -> Screens.dialog(state, ProjectDialogs.create(home,
				new Draft(form.require(ProjectDialogs.NAME),
					ProjectKind.valueOf(form.require(ProjectDialogs.KIND)),
					Target.chipset(form.require(ProjectDialogs.TARGET)), folder), "", editing));
			case OPEN_PROJECT -> Screens.dialog(state, ProjectDialogs.open(home, folder, "", editing));
			default -> throw new IllegalStateException("No dialog is browsing folders");
		};
	}

	@Action("project-settings")
	public Regions onClickSaveSettings(final Form form) {
		final StudioState state = session.state();
		final ProjectDirectory directory = Workspaces.open(state);
		final Map<String, String> values = Map.of(ProjectDialogs.NAME, form.require(ProjectDialogs.NAME).strip(),
			ProjectDialogs.TARGET, form.require(ProjectDialogs.TARGET), ProjectDialogs.LOAD,
			form.require(ProjectDialogs.LOAD).strip(), ProjectDialogs.ENTRY, form.require(ProjectDialogs.ENTRY));
		final Optional<String> refused = state.run().running() ? Optional.of(BUILD_RUNNING)
			: refused(directory, values);

		if (refused.isPresent()) {
			return Screens.dialog(state, ProjectDialogs.settings(state, values, refused.get()));
		}

		final ProjectFile file = directory.file();
		final Target target = Target.chipset(values.get(ProjectDialogs.TARGET));
		final ProjectFile settled = new ProjectFile(file.format(), values.get(ProjectDialogs.NAME), file.kind(),
			target, LoadAddress.parse(values.get(ProjectDialogs.LOAD)), values.get(ProjectDialogs.ENTRY),
			file.crunch(), file.symbols(), file.breakpoints(), file.extras());
		final ProjectDirectory saved = Workspaces.read(() -> directory.save(settled));

		if (!saved.chp().equals(directory.chp())) {
			Workspaces.write(() -> Files.delete(directory.chp()));
		}

		preferences.used(saved.chp());

		final List<Path> recent = preferences.recent();

		return Screens.all(session.change(changed -> idle(changed).withWorkspace(Optional.of(saved))
			.withTarget(target).withModal(Modal.NONE).withEnvironment(changed.environment().withRecent(recent))
			.withNote("Saved the project settings")));
	}

	@FunctionalInterface
	private interface Opening {
		ProjectDirectory open() throws IOException;
	}

	/*
	 * The page sends its unsaved buffers along with every switch. With some, the switch waits on the person: saved,
	 * the buffers go into the project being left; dropped, they are gone. A project that will not open or cannot
	 * be made says why in its own dialog, and the studio stays where it was.
	 */
	private Regions switchTo(final Form form, final String action, final Map<String, String> pending,
		final Opening opening, final Function<String, ModalView> failed) {

		final List<String> unsaved = Buffers.names(form);
		List<String> saved = List.of();

		idle(session.state());

		if (!unsaved.isEmpty()) {
			Buffers.owned(Workspaces.open(session.state()), form);
		}

		if (!unsaved.isEmpty() && !form.has(ProjectDialogs.SAVE) && !form.has(ProjectDialogs.DISCARD)) {
			return Screens.dialog(session.change(state -> state.withModal(Modal.DISCARD)),
				ProjectDialogs.discard(action, pending, unsaved, ""));
		}

		if (form.has(ProjectDialogs.SAVE)) {
			final ProjectDirectory leaving = Workspaces.open(session.state());
			final Marks marks = Marks.of(leaving, form, form::get);

			try {
				saved = Buffers.save(leaving, form);
			} catch (Buffers.Failed e) {
				final StudioState asking = session.change(state -> state.withModal(Modal.DISCARD));

				return Screens.confirming(Screens.dialog(asking,
					ProjectDialogs.discard(action, pending, unsaved, e.getMessage())), e.saved());
			}

			session.update(state -> Workspaces.saved(marks.placed(state)));
		}

		final ProjectDirectory opened;

		try {
			opened = opening.open();
		} catch (IOException | IllegalArgumentException e) {
			final ModalView dialog = failed.apply(Workspaces.reason(e));

			return Screens.confirming(Screens.dialog(session.change(state -> state.withModal(dialog.kind())),
				dialog), saved);
		}

		preferences.used(opened.chp());

		final List<Path> recent = preferences.recent();

		return Screens.confirming(Screens.all(session.change(state -> idle(state).enter(opened)
			.withModal(Modal.NONE).withEnvironment(state.environment().withRecent(recent)))), saved);
	}

	/* Checked again inside the change: a build that starts in between must not find its project swapped away. */
	private static StudioState idle(final StudioState state) {
		if (state.run().running()) {
			throw new IllegalStateException(BUILD_RUNNING);
		}

		return state;
	}

	/* The project's own folder is where the next one most likely belongs; without one, or out of reach, home. */
	private Path near(final StudioState state) {
		return state.workspace().map(directory -> directory.dir().getParent())
			.filter(folder -> folder.startsWith(home)).orElse(home);
	}

	/* The folder an error dialog looks in: the one holding the file asked for, never one above the home folder. */
	private Path folderOf(final Path chp) {
		final Path folder = Files.isDirectory(chp) ? chp : chp.getParent();

		return folder.startsWith(home) ? folder : home;
	}

	private boolean editing() {
		return session.state().workspace().isPresent();
	}

	private Path inside(final String path) {
		final Path resolved = home.resolve(path).normalize();

		if (!resolved.startsWith(home) || !Workspaces.confined(home, resolved)) {
			throw new IllegalArgumentException(path + " is outside the home folder");
		}

		return resolved;
	}

	private static Optional<String> refused(final ProjectDirectory directory, final Map<String, String> values) {
		final String name = values.get(ProjectDialogs.NAME);
		final String entry = values.get(ProjectDialogs.ENTRY);
		final boolean renamed = !name.equals(directory.file().name());

		if (!ProjectLayout.validName(name)) {
			return Optional.of(NAME_RULE);
		}

		if (renamed && Files.exists(directory.dir().resolve(ProjectLayout.chp(name)))) {
			return Optional.of("The folder already holds " + ProjectLayout.chp(name));
		}

		try {
			LoadAddress.parse(values.get(ProjectDialogs.LOAD));
		} catch (IllegalArgumentException e) {
			return Optional.of("Load address: " + e.getMessage());
		}

		final String prefix = ProjectLayout.SRC + "/";

		if (!entry.startsWith(prefix) || !entry.endsWith(SOURCE)
			|| !Workspaces.sources(directory).contains(entry.substring(prefix.length()))) {

			return Optional.of("Entry file: " + entry + " is not a source of the project");
		}

		return Optional.empty();
	}
}
