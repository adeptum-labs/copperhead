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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.copperhead.web.action.ActionFailed;
import org.copperhead.web.action.Actions;
import org.copperhead.web.action.Form;
import org.copperhead.web.action.MissingValue;
import org.copperhead.web.action.View;

public class ActionHandler extends Handler.Abstract {
	private static final String HTML = "text/html;charset=utf-8";

	private final Actions actions;
	private final Templates templates;

	public ActionHandler(final Actions actions, final Templates templates) {
		this.actions = actions;
		this.templates = templates;
	}

	@Override
	public boolean handle(final Request request, final Response response, final Callback callback)
		throws Exception {

		final String name = Request.getPathInContext(request).substring(1);

		/* Unknown actions are refused rather than passed on. A page falling back to the whole
		   interface when a swap was expected hides the mistake instead of showing it. */
		if (!actions.knows(name)) {
			response.setStatus(HttpStatus.NOT_FOUND_404);
			callback.succeeded();
			return true;
		}

		final Optional<View> shown;

		try {
			shown = actions.invoke(name, Form.parse(Content.Source.asString(request)));
		} catch (ActionFailed e) {
			/* A request that left out what the action needs is the caller's mistake, not a failure here. */
			if (e.getCause() instanceof MissingValue refusal) {
				return refuse(response, callback, refusal);
			}

			throw e;
		}

		if (shown.isEmpty()) {
			response.setStatus(HttpStatus.NO_CONTENT_204);
			callback.succeeded();
			return true;
		}

		final byte[] body = templates.render(shown.get()).getBytes(StandardCharsets.UTF_8);

		response.setStatus(HttpStatus.OK_200);
		response.getHeaders().put(HttpHeader.CONTENT_TYPE, HTML);
		response.write(true, ByteBuffer.wrap(body), callback);
		return true;
	}

	private static boolean refuse(final Response response, final Callback callback,
		final MissingValue refusal) {

		final String reason = String.valueOf(refusal.getMessage());

		response.setStatus(HttpStatus.BAD_REQUEST_400);
		response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
		response.write(true, ByteBuffer.wrap(reason.getBytes(StandardCharsets.UTF_8)), callback);
		return true;
	}
}
