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

package org.copperhead.web;

import java.util.function.Supplier;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.copperhead.web.action.Actions;
import org.copperhead.web.action.View;

public final class Routes {
	private static final String ASSETS = "static/";
	private static final String ASSET_PATH = "/static";
	private static final String WINDOW_PATH = "/window";
	private static final String ACTION_PATH = "/action";

	private Routes() {
	}

	public static Handler create(final Templates templates, final SessionToken token, final View index) {
		return create(templates, token, WindowControl.NONE, Actions.of(), index);
	}

	public static Handler create(final Templates templates, final SessionToken token,
		final WindowControl window, final Actions actions, final View index) {

		return create(templates, token, window, actions, () -> index);
	}

	public static Handler create(final Templates templates, final SessionToken token,
		final WindowControl window, final Actions actions, final Supplier<? extends View> index) {

		final ResourceHandler assets = new ResourceHandler();

		assets.setBaseResource(ResourceFactory.of(assets).newClassLoaderResource(ASSETS));
		assets.setDirAllowed(false);

		/* Anything the asset handler does not recognise falls through to the pages,
		   so an unknown path renders the interface rather than a Jetty error page. */
		return new Guard(new Handler.Sequence(
			new ContextHandler(assets, ASSET_PATH),
			new ContextHandler(new PostOnly(new WindowHandler(window)), WINDOW_PATH),
			new ContextHandler(new PostOnly(new ActionHandler(actions, templates)), ACTION_PATH),
			new UiHandler(templates, index)
		), token);
	}
}
