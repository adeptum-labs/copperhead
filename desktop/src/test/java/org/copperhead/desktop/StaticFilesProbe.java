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

package org.copperhead.desktop;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.Supplier;
import org.copperhead.web.Routes;
import org.copperhead.web.SessionToken;
import org.copperhead.web.Templates;
import org.copperhead.web.UiServer;
import org.copperhead.web.WindowControl;
import org.copperhead.web.action.Actions;
import org.copperhead.web.action.View;

/**
 * Serves the interface from whatever Java runs it, asks for the files the pages are drawn with, and prints the
 * status and type of each answer. The installer tests run it on the runtime that is packaged with Copperhead,
 * which has only the JDK modules the build chose to include.
 */
public final class StaticFilesProbe {
	static final List<String> ASSETS = List.of(
		"/static/css/copperhead.css", "/static/js/htmx.min.js", "/static/fonts/ibm-plex-sans-latin.woff2"
	);

	private StaticFilesProbe() {
	}

	public static void main(final String[] arguments) throws Exception {
		final Supplier<View> noPage = () -> {
			throw new IllegalStateException("The request reached the page instead of being served as a file");
		};
		final SessionToken token = SessionToken.random();
		final URI base = new UiServer(Routes.create(new Templates(false), token, WindowControl.NONE, Actions.of(),
			noPage)).start();
		final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager())
			.followRedirects(HttpClient.Redirect.NORMAL).build();

		for (final String asset : ASSETS) {
			final URI address = base.resolve(asset + "?" + SessionToken.PARAMETER + "=" + token.value());
			final HttpResponse<Void> response = http.send(HttpRequest.newBuilder(address).build(),
				HttpResponse.BodyHandlers.discarding());

			System.out.println(asset + " " + response.statusCode() + " "
				+ response.headers().firstValue("content-type").orElse("-"));
		}

		System.exit(0);
	}
}
