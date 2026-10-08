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

import java.net.URI;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.eclipse.jetty.server.Handler;

/** Runs the interface on a real port for the length of one test. */
public abstract class ServedTest {
	private final SessionToken token = SessionToken.random();
	private final Templates templates = new Templates(false);

	private UiServer server;
	private URI base;

	protected Handler routes() {
		return Routes.create(templates, token, new Page("Copperhead"));
	}

	@BeforeTry
	public void serve() throws Exception {
		server = new UiServer(routes());
		base = server.start();
	}

	@AfterTry
	public void stop() throws Exception {
		server.stop();
	}

	protected SessionToken token() {
		return token;
	}

	protected Templates templates() {
		return templates;
	}

	protected URI base() {
		return base;
	}

	/** A caller that has not proven anything yet. */
	protected Client anonymous() {
		return new Client(base);
	}

	/** A caller holding the session cookie, as the interface itself does. */
	protected Client admitted() throws Exception {
		final Client client = anonymous();

		client.get("/?" + SessionToken.PARAMETER + "=" + token.value());
		return client;
	}
}
