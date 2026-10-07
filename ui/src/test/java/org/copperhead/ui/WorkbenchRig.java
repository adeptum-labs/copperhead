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

package org.copperhead.ui;

import java.net.URI;
import org.copperhead.ui.controller.CodeController;
import org.copperhead.ui.controller.PaletteController;
import org.copperhead.ui.controller.Screens;
import org.copperhead.ui.controller.SpriteController;
import org.copperhead.ui.controller.WorkbenchController;
import org.copperhead.ui.session.ProjectSession;
import org.copperhead.web.RecordingWindow;
import org.copperhead.web.Routes;
import org.copperhead.web.SessionToken;
import org.copperhead.web.Templates;
import org.copperhead.web.UiServer;
import org.copperhead.web.action.Actions;

/** The studio served the way the shell serves it, with a window that only takes notes. */
public class WorkbenchRig implements AutoCloseable {
	private final SessionToken token = SessionToken.random();
	private final RecordingWindow window = new RecordingWindow();
	private final ProjectSession session = new ProjectSession();
	private final Actions actions = Actions.of(controllers(session));
	private final UiServer server;
	private final URI base;

	public WorkbenchRig() throws Exception {
		server = new UiServer(Routes.create(new Templates(false), token, window, actions,
			() -> Screens.index(session.state())
		));
		base = server.start();
	}

	/** Every controller of the studio, sharing the one session the way the container shares it. */
	static Object[] controllers(final ProjectSession session) {
		return new Object[] {new WorkbenchController(session), new CodeController(session),
			new SpriteController(session), new PaletteController(session)};
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

	public ProjectSession session() {
		return session;
	}

	public Actions actions() {
		return actions;
	}

	@Override
	public void close() throws Exception {
		server.stop();
	}
}
