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

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

/**
 * Lets only a POST reach what acts. Every control of the interface posts, while a GET can be had from a mere link,
 * and a top-level navigation carries no Origin for the guard to check.
 */
final class PostOnly extends Handler.Wrapper {
	PostOnly(final Handler handler) {
		super(handler);
	}

	@Override
	public boolean handle(final Request request, final Response response, final Callback callback)
		throws Exception {

		if (HttpMethod.POST.is(request.getMethod())) {
			return super.handle(request, response, callback);
		}

		response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
		response.getHeaders().put(HttpHeader.ALLOW, HttpMethod.POST.asString());
		callback.succeeded();
		return true;
	}
}
