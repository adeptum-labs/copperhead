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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.Map;
import org.copperhead.toolchain.XdgDirs;
import org.copperhead.ui.controller.BuildController;
import org.copperhead.ui.controller.CodeController;
import org.copperhead.ui.controller.DebuggerController;
import org.copperhead.ui.controller.EmulatorController;
import org.copperhead.ui.controller.PaletteController;
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
 * that goes when the rig closes, so the preferences one test leaves never reach another.
 */
public class WorkbenchRig implements AutoCloseable {
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
		this(clock, Files.createTempDirectory("copperhead-home"));
	}

	private WorkbenchRig(final InstantSource clock, final Path home) throws Exception {
		this.home = home;
		preferences = new Preferences(XdgDirs.of(Map.of(), home).config().resolve(PREFERENCES));
		session = new ProjectSession(preferences, clock);
		actions = Actions.of(new WorkbenchController(session, preferences), new CodeController(session),
			new SpriteController(session), new PaletteController(session), new DebuggerController(session),
			new BuildController(session), new EmulatorController(session), new TimelineController(session));
		server = new UiServer(Routes.create(new Templates(false), token, window, actions,
			() -> Screens.index(session.state())
		));
		base = server.start();
	}

	/** The next launch on the same home, as a restart finds it; this studio stops serving and hands the home on. */
	public WorkbenchRig restart(final InstantSource clock) throws Exception {
		server.stop();
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

	public Preferences preferences() {
		return preferences;
	}

	public ProjectSession session() {
		return session;
	}

	public Actions actions() {
		return actions;
	}

	@Override
	public void close() throws Exception {
		server.stop();

		if (!handedOver) {
			TempDirectory.delete(home);
		}
	}
}
