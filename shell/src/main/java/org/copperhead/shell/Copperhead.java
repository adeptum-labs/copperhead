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

package org.copperhead.shell;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.util.Optional;
import javax.swing.JOptionPane;
import org.copperhead.ui.controller.Screens;
import org.copperhead.ui.session.ProjectSession;
import org.copperhead.web.Routes;
import org.copperhead.web.SessionToken;
import org.copperhead.web.Templates;
import org.copperhead.web.UiServer;
import org.copperhead.web.action.ActionExtension;
import org.copperhead.web.action.Actions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

public final class Copperhead {
	private static final Logger LOG = LoggerFactory.getLogger(Copperhead.class);
	private static final int USAGE_ERROR = 2;

	private Copperhead() {
	}

	public static void main(final String[] args) throws Exception {
		try {
			final Optional<LaunchOptions> options = LaunchOptions.of(args);

			if (options.isPresent()) {
				start(options.get());
			}
		} catch (CommandLine.ParameterException e) {
			System.err.println(e.getMessage());
			e.getCommandLine().usage(System.err);
			System.exit(USAGE_ERROR);
		}
	}

	private static void start(final LaunchOptions options) throws Exception {
		final SeContainer container = SeContainerInitializer.newInstance().initialize();
		final Actions actions = Actions.discovered(
			container.getBeanManager().getExtension(ActionExtension.class)
		);

		final BrowserWindow window = new BrowserWindow(options.windowSystem());
		final SessionToken token = SessionToken.random();
		final ProjectSession session = container.select(ProjectSession.class).get();
		final UiServer server = new UiServer(Routes.create(new Templates(true), token, window.control(), actions,
			() -> Screens.index(session.state())
		));
		final URI uri = server.start();

		LOG.info("Copperhead is serving its interface at {}", uri);

		/* The token rides on the first navigation only; the server swaps it for a cookie and
		   redirects, so it does not linger in the address. */
		try {
			window.open(uri.resolve("/?" + SessionToken.PARAMETER + "=" + token.value()));
		} catch (Exception | LinkageError e) {
			giveUp(server, e);
		}
	}

	/* The server's threads would keep the process alive with no window to reach it, and a launcher from
	   a menu has no console to show the error in. */
	private static void giveUp(final UiServer server, final Throwable cause) throws Exception {
		LOG.error("Copperhead could not open its window", cause);
		server.stop();

		if (!GraphicsEnvironment.isHeadless()) {
			JOptionPane.showMessageDialog(null, StartFailure.message(new CefLocation(), cause), Screens.TITLE,
				JOptionPane.ERROR_MESSAGE);
		}

		System.exit(1);
	}
}
