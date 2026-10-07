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

import java.net.HttpCookie;
import java.util.List;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

/**
 * Refuses anything that cannot prove it is the interface this process opened. A loopback port is
 * reachable by every other program on the machine, and this one drives the toolchain on it.
 */
public class Guard extends Handler.Wrapper {
	private final SessionToken token;

	public Guard(final Handler handler, final SessionToken token) {
		super(handler);
		this.token = token;
	}

	@Override
	public boolean handle(final Request request, final Response response, final Callback callback)
		throws Exception {

		if (foreignOrigin(request)) {
			return refuse(response, callback);
		}

		if (token.matches(cookie(request))) {
			return super.handle(request, response, callback);
		}

		/* The very first navigation carries the token in the query, because there is nowhere
		   else to put it before a cookie exists. It is exchanged for a cookie immediately so
		   that it does not stay in the address or in referrers. */
		if (token.matches(Request.extractQueryParameters(request).getValue(SessionToken.PARAMETER))) {
			return adopt(request, response, callback);
		}

		return refuse(response, callback);
	}

	private boolean adopt(final Request request, final Response response, final Callback callback) {
		response.getHeaders().put(HttpHeader.SET_COOKIE,
			SessionToken.COOKIE + "=" + token.value() + "; Path=/; HttpOnly; SameSite=Strict"
		);

		Response.sendRedirect(request, response, callback, Request.getPathInContext(request));
		return true;
	}

	private boolean refuse(final Response response, final Callback callback) {
		response.setStatus(HttpStatus.FORBIDDEN_403);
		callback.succeeded();
		return true;
	}

	private String cookie(final Request request) {
		for (final String header : request.getHeaders().getValuesList(HttpHeader.COOKIE)) {
			for (final HttpCookie cookie : parse(header)) {
				if (SessionToken.COOKIE.equals(cookie.getName())) {
					return cookie.getValue();
				}
			}
		}

		return null;
	}

	private List<HttpCookie> parse(final String header) {
		try {
			return HttpCookie.parse(header);
		} catch (IllegalArgumentException _) {
			return List.of();
		}
	}

	/* A page in the user's own browser can reach this port. It cannot read the responses, but
	   without this it could still fire requests that act. */
	private boolean foreignOrigin(final Request request) {
		final String origin = request.getHeaders().get(HttpHeader.ORIGIN);

		return origin != null && !origin.equals(expectedOrigin(request));
	}

	private String expectedOrigin(final Request request) {
		return "http://" + request.getHeaders().get(HttpHeader.HOST);
	}
}
