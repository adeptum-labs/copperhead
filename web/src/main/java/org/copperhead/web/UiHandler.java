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
import java.util.function.Supplier;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.copperhead.web.action.View;

public class UiHandler extends Handler.Abstract {
	private static final String HTML = "text/html;charset=utf-8";

	private final Templates templates;
	private final Supplier<? extends View> index;

	public UiHandler(final Templates templates, final Supplier<? extends View> index) {
		this.templates = templates;
		this.index = index;
	}

	@Override
	public boolean handle(final Request request, final Response response, final Callback callback) {
		final ByteBuffer body = ByteBuffer.wrap(templates.render(index.get()).getBytes(StandardCharsets.UTF_8));

		response.setStatus(HttpStatus.OK_200);
		response.getHeaders().put(HttpHeader.CONTENT_TYPE, HTML);
		response.write(true, body, callback);
		return true;
	}
}
