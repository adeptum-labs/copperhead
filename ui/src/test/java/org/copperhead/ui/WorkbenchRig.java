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

package org.copperhead.ui;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.Target;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.toolchain.XdgDirs;
import org.copperhead.ui.controller.BuildController;
import org.copperhead.ui.controller.DebuggerController;
import org.copperhead.ui.controller.EditorController;
import org.copperhead.ui.controller.EmulatorController;
import org.copperhead.ui.controller.PaletteController;
import org.copperhead.ui.controller.ProjectController;
import org.copperhead.ui.controller.Screens;
import org.copperhead.ui.controller.SpriteController;
import org.copperhead.ui.controller.TimelineController;
import org.copperhead.ui.controller.WorkbenchController;
import org.copperhead.ui.session.Preferences;
import org.copperhead.ui.session.ProjectSession;
import org.copperhead.web.RecordingWindow;
import org.copperhead.web.Routes;
import org.copperhead.web.SessionToken;
import org.copperhead.web.Templates;
import org.copperhead.web.UiServer;
import org.copperhead.web.action.Actions;

/**
 * The studio served the way the shell serves it, with a window that only takes notes, in a home folder of its own
 * that goes when the rig closes. Unless asked otherwise a demo made from its template is open.
 */
public class WorkbenchRig implements AutoCloseable {
	public static final String PROJECTS = "projects";

	private static final String PREFERENCES = "preferences.json";

	private final SessionToken token = SessionToken.random();
	private final RecordingWindow window = new RecordingWindow();
	private final Path home;
	private final Preferences preferences;
	private final ProjectSession session;
	private final Actions actions;
	private final UiServer server;
	private final URI base;
	private boolean handedOver;

	/** The studio with its run timed by the clock given, a test's own or the real one for a browser. */
	public WorkbenchRig(final InstantSource clock) throws Exception {
		this(clock, prepared(Optional.of(ProjectKind.DEMO)));
	}

	private WorkbenchRig(final InstantSource clock, final Path home) throws Exception {
		this.home = home;
		preferences = new Preferences(XdgDirs.of(Map.of(), home).config().resolve(PREFERENCES));
		session = new ProjectSession(preferences, clock);
		actions = Actions.of(new WorkbenchController(session, preferences),
			new ProjectController(session, preferences, home), new EditorController(session),
			new SpriteController(session), new PaletteController(session), new DebuggerController(session),
			new BuildController(session), new EmulatorController(session), new TimelineController(session));
		server = new UiServer(Routes.create(new Templates(false), token, window, actions,
			() -> Screens.index(session.state())
		));
		base = server.start();
	}

	/** The studio with no project open, showing its welcome. */
	public static WorkbenchRig empty(final InstantSource clock) throws Exception {
		return new WorkbenchRig(clock, prepared(Optional.empty()));
	}

	/** The studio with a project of the kind given open, made from its template. */
	public static WorkbenchRig with(final InstantSource clock, final ProjectKind kind) throws Exception {
		return new WorkbenchRig(clock, prepared(Optional.of(kind)));
	}

	/* A home as a first launch finds it, with the project made from its template that was opened last. */
	private static Path prepared(final Optional<ProjectKind> kind) throws IOException {
		final Path home = Files.createTempDirectory("copperhead-home");
		final Preferences preferences = new Preferences(XdgDirs.of(Map.of(), home).config().resolve(PREFERENCES));

		if (kind.isPresent()) {
			preferences.used(ProjectDirectory.create(home.resolve(PROJECTS),
				kind.get().name().toLowerCase(Locale.ROOT), kind.get(), Target.A500_OCS).chp());
		}

		return home;
	}

	/** The next launch on the same home, as a restart finds it; this studio stops serving and hands the home on. */
	public WorkbenchRig restart(final InstantSource clock) throws Exception {
		stop();
		handedOver = true;
		return new WorkbenchRig(clock, home);
	}

	public URI base() {
		return base;
	}

	/** Where the shell first sends its window, carrying the token that is swapped for a cookie. */
	public URI entrance() {
		return base.resolve("/?" + SessionToken.PARAMETER + "=" + token.value());
	}

	public RecordingWindow window() {
		return window;
	}

	public Path home() {
		return home;
	}

	public ProjectDirectory project() {
		return session.state().workspace().orElseThrow();
	}

	public Preferences preferences() {
		return preferences;
	}

	public ProjectSession session() {
		return session;
	}

	public Actions actions() {
		return actions;
	}

	private void stop() throws Exception {
		server.stop();
	}

	@Override
	public void close() throws Exception {
		stop();

		if (!handedOver) {
			TempDirectory.delete(home);
		}
	}
}
