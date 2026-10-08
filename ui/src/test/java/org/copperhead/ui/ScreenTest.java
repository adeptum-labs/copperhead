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
import net.jqwik.api.Example;
import org.copperhead.web.Client;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Each refusal happens before anything is sent, so no server needs to answer. */
public class ScreenTest {
	private static final String SEND = "<button hx-post=\"/action/x\">Send</button>";

	private final Client client = new Client(URI.create("http://localhost:1"));

	private Screen over(final String body) {
		return Screen.over(client, "<html><body hx-swap=\"none\">" + body + "</body></html>");
	}

	@Example
	public void shouldRefuseValuesInheritedFromAnAncestor() {
		final Screen screen = over("<div hx-vals='{\"a\":\"b\"}'>" + SEND + "</div>");

		assertThrows(UnsupportedOperationException.class, () -> screen.click("button"));
	}

	@Example
	public void shouldRefuseAControlInsideAForm() {
		final Screen screen = over("<form>" + SEND + "</form>");

		assertThrows(UnsupportedOperationException.class, () -> screen.click("button"));
	}

	@Example
	public void shouldRefuseAControlThatNamesAValue() {
		final Screen screen = over("<button hx-post=\"/action/x\" name=\"a\" value=\"b\">Send</button>");

		assertThrows(UnsupportedOperationException.class, () -> screen.click("button"));
	}

	@Example
	public void shouldRefuseAFieldThatSendsItsOwnValue() {
		final Screen screen = over("<input hx-post=\"/action/x\">");

		assertThrows(UnsupportedOperationException.class, () -> screen.click("input"));
	}

	@Example
	public void shouldRefuseWhatHtmxIsToldInTheDataSpelling() {
		final Screen screen = over("<button hx-post=\"/action/x\" data-hx-target=\"#tool\">Send</button>");

		assertThrows(UnsupportedOperationException.class, () -> screen.click("button"));
	}

	@Example
	public void shouldRefuseASwapThatIsNotNone() {
		final Screen screen = Screen.over(client, "<html><body hx-swap=\"innerHTML\">" + SEND + "</body></html>");

		assertThrows(UnsupportedOperationException.class, () -> screen.click("button"));
	}

	@Example
	public void shouldRefuseAnySyncButQueuingEveryRequestOnTheBody() {
		final Screen dropping = Screen.over(client,
			"<html><body hx-swap=\"none\" hx-sync=\"body:drop\">" + SEND + "</body></html>");
		final Screen elsewhere = over("<div hx-sync=\"body:queue all\">" + SEND + "</div>");

		assertThrows(UnsupportedOperationException.class, () -> dropping.click("button"));
		assertThrows(UnsupportedOperationException.class, () -> elsewhere.click("button"));
	}
}
