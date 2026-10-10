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
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.copperhead.core.project.Target;
import org.copperhead.ui.session.BuildService;
import org.copperhead.ui.session.Layout;
import org.copperhead.ui.session.Preferences;
import org.copperhead.ui.session.ProjectSession;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.session.ToolStatus;
import org.copperhead.ui.view.Modal;
import org.copperhead.ui.view.Region;
import org.copperhead.ui.view.Regions;
import org.copperhead.ui.view.Tool;
import org.copperhead.web.action.Action;
import org.copperhead.web.action.Form;

/**
 * The commands of the window itself: which tool is shown, which machine, the dialogs, and how the person likes
 * the window, which is kept with their preferences so the next launch opens it the same way.
 */
@ApplicationScoped
public class WorkbenchController {
	private static final List<Modal> TOLD = List.of(Modal.SHORTCUTS, Modal.ABOUT, Modal.SETTINGS);
	private static final String THEME = "theme";
	private static final String SPLIT = "split";
	private static final String BOTTOM = "bottom";

	private final ProjectSession session;
	private final Preferences preferences;
	private final BuildService builds;

	@Inject
	public WorkbenchController(final ProjectSession session, final Preferences preferences, final BuildService builds) {
		this.session = session;
		this.preferences = preferences;
		this.builds = builds;
	}

	/* The toolchain card says where each tool is found, which only the disk knows, so it is looked at anew. */
	@Action("tool")
	public Regions onClickTool(final Form form) {
		final Tool tool = Tool.valueOf(form.get("tool"));
		final List<ToolStatus> found = tool == Tool.BUILD ? builds.tools() : session.state().environment().tools();

		return Screens.regions(session.change(state -> state.withTool(tool)
			.withEnvironment(state.environment().withTools(found))), Region.MENUS, Region.RAIL, Region.TOOL);
	}

	@Action("target")
	public Regions onClickTarget(final Form form) {
		final Target target = Target.chipset(form.get("target"));

		return Screens.all(session.change(state -> Workspaces.saved(state.withTarget(target))));
	}

	@Action("modal")
	public Regions onClickModal(final Form form) {
		final Modal modal = Modal.valueOf(form.get("kind"));

		if (modal == Modal.PREFERENCES) {
			return Screens.dialog(session.change(state -> state.withModal(modal)),
				ModalViews.preferences(ModalViews.preferencesOf(preferences), ""));
		}

		if (!TOLD.contains(modal)) {
			throw new IllegalArgumentException("The dialog " + modal + " opens from its own action");
		}

		return Screens.regions(session.change(state -> told(state, modal)), Region.MODAL);
	}

	/* The settings are the project's, so with none open there is nothing to draw them from. */
	private static StudioState told(final StudioState state, final Modal modal) {
		if (modal == Modal.SETTINGS && state.workspace().isEmpty()) {
			throw new IllegalStateException("No project is open");
		}

		return state.withModal(modal);
	}

	/* A question the build waits on is answered no when its dialog is closed, or the build would wait forever. */
	@Action("modal-close")
	public Regions onClickCloseModal() {
		if (session.state().modal() == Modal.TRUST) {
			builds.refuse();
		}

		return Screens.regions(session.change(state -> state.withModal(Modal.NONE)), Region.MODAL);
	}

	@Action("timeline")
	public Regions onClickTimeline() {
		return Screens.regions(session.change(state -> state.withTimelineShown(!state.timelineShown())),
			Region.MENUS, Region.TIMELINE);
	}

	/* The status bar carries the theme, and the page takes it from there. */
	@Action("theme")
	public Regions onClickTheme(final Form form) {
		final Layout layout = preferences.layout().withTheme(form.get(THEME));

		preferences.layout(layout);
		return Screens.regions(session.change(state -> state.withLayout(layout)));
	}

	/* The page has laid itself out while the splitter was dragged; this only remembers where it was let go. */
	@Action("layout")
	public void onDragSplitter(final Form form) {
		final Layout saved = preferences.layout();
		final Layout layout = saved.withTheme(form.has(THEME) ? form.get(THEME) : saved.theme())
			.withSplit(form.has(SPLIT) ? Integer.parseInt(form.get(SPLIT)) : saved.split())
			.withBottom(form.has(BOTTOM) ? Integer.parseInt(form.get(BOTTOM)) : saved.bottom());

		preferences.layout(layout);
		session.update(state -> state.withLayout(layout));
	}

	@Action("preferences-save")
	public Regions onClickSavePreferences(final Form form) {
		final Map<String, String> values = Map.of(ModalViews.THEME, form.get(ModalViews.THEME),
			ModalViews.VASM, form.get(ModalViews.VASM).strip(), ModalViews.SHRINKLER,
			form.get(ModalViews.SHRINKLER).strip());
		final Optional<String> refused = refused(values);

		if (refused.isPresent()) {
			return Screens.dialog(session.state(), ModalViews.preferences(values, refused.get()));
		}

		final Layout layout = preferences.layout().withTheme(values.get(ModalViews.THEME));

		preferences.layout(layout);
		preferences.tool(ModalViews.VASM, tool(values.get(ModalViews.VASM)));
		preferences.tool(ModalViews.SHRINKLER, tool(values.get(ModalViews.SHRINKLER)));

		final List<ToolStatus> found = builds.tools();

		return Screens.all(session.change(state -> state.withLayout(layout).withModal(Modal.NONE)
			.withEnvironment(state.environment().withTools(found)).withNote("Saved preferences")));
	}

	/*
	 * A tool is a program that runs, named by where it is, not by a path relative to wherever Copperhead started.
	 * What is typed here is later executed by the build, so it is checked before it is kept.
	 */
	private static Optional<String> refused(final Map<String, String> values) {
		if (!List.of(Layout.DARK, Layout.LIGHT).contains(values.get(ModalViews.THEME))) {
			return Optional.of(ModalViews.THEME + ": " + values.get(ModalViews.THEME) + " is not a theme");
		}

		return Stream.of(ModalViews.VASM, ModalViews.SHRINKLER).filter(name -> unrunnable(values.get(name)))
			.findFirst().map(name -> name + ": " + values.get(name) + " is not a program Copperhead can run");
	}

	private static boolean unrunnable(final String typed) {
		try {
			return tool(typed).filter(path -> !path.isAbsolute() || !path.equals(path.normalize())
				|| !Files.isRegularFile(path) || !Files.isExecutable(path)).isPresent();
		} catch (InvalidPathException e) {
			return true;
		}
	}

	private static Optional<Path> tool(final String path) {
		return path.isEmpty() ? Optional.empty() : Optional.of(Path.of(path));
	}
}
