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

package org.copperhead.web;

import java.util.Optional;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.copperhead.web.WindowControl.Edge;

public class WindowHandler extends Handler.Abstract {
	private static final String RESIZE_START = "resize/start/";

	private final WindowControl window;

	public WindowHandler(final WindowControl window) {
		this.window = window;
	}

	@Override
	public boolean handle(final Request request, final Response response, final Callback callback) {
		if (!perform(Request.getPathInContext(request).substring(1))) {
			return false;
		}

		response.setStatus(HttpStatus.NO_CONTENT_204);
		callback.succeeded();
		return true;
	}

	private boolean perform(final String command) {
		if (command.startsWith(RESIZE_START)) {
			return beginResize(command.substring(RESIZE_START.length()));
		}

		switch (command) {
			case "minimise" -> window.minimise();
			case "maximise" -> window.toggleMaximise();
			case "close" -> window.close();
			case "move/start" -> window.beginMove();
			case "move/end" -> window.endMove();
			case "resize/end" -> window.endResize();
			default -> {
				return false;
			}
		}

		return true;
	}

	private boolean beginResize(final String name) {
		final Optional<Edge> edge = Edge.named(name);

		edge.ifPresent(window::beginResize);
		return edge.isPresent();
	}
}
