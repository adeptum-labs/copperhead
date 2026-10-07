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

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.copperhead.web.Client;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * The page as the window holds it, changed the way htmx changes it. No control in the studio swaps anything
 * itself: the answer names the regions it replaces. Only that much of htmx is played out, and a control asking
 * for more is refused, so a test can never pass on behaviour the real page would not have.
 */
public final class Screen {
	private static final Set<String> UNDERSTOOD = Set.of("hx-post", "hx-swap", "hx-vals", "hx-swap-oob");
	private static final String NONE = "none";
	private static final String OUT_OF_BAND = "true";
	private static final String SCRIPTED = "js:";
	private static final int OK = 200;
	private static final Jsonb JSON = JsonbBuilder.create();

	private final Client client;
	private final Document document;
	private int status;

	private Screen(final Client client, final HttpResponse<String> response) {
		this.client = client;
		this.document = Jsoup.parse(response.body());
		this.status = response.statusCode();
	}

	/** The page the shell opens, entered with the token as the window enters it. */
	public static Screen open(final WorkbenchRig rig) throws Exception {
		final Client client = new Client(rig.base());

		return new Screen(client, client.get(rig.entrance().toString()));
	}

	public Client client() {
		return client;
	}

	public Document document() {
		return document;
	}

	/** The status of the last answer; anything but a 200 leaves the page as it was. */
	public int status() {
		return status;
	}

	public Element find(final String css) {
		final Element element = document.selectFirst(css);

		if (element == null) {
			throw new AssertionError("Nothing on screen matches " + css + " in\n" + document.body());
		}

		return element;
	}

	public Screen click(final String css) throws Exception {
		final Element element = find(css);

		if (element.hasAttr("disabled")) {
			throw new AssertionError(css + " is disabled and cannot be clicked");
		}

		if (element.attr("hx-vals").startsWith(SCRIPTED)) {
			throw new UnsupportedOperationException(css + " works out values of its own; trigger it with them");
		}

		return send(element, Map.of());
	}

	/** Sends a control with the values its script would have worked out, since no script runs here. */
	public Screen trigger(final String css, final Map<String, String> values) throws Exception {
		return send(find(css), values);
	}

	/** Presses a shortcut the way the page does, by clicking the control that carries it as data-key. */
	public Screen press(final String key) throws Exception {
		return click("[data-key=\"" + key + "\"]");
	}

	private Screen send(final Element element, final Map<String, String> values) throws Exception {
		refuseWhatIsNotPlayedOut(element);

		final HttpResponse<String> response = client.submit(element.attr("hx-post"), encode(sent(element, values)));

		status = response.statusCode();

		if (status == OK) {
			for (final Element arrived : Jsoup.parseBodyFragment(response.body()).body().children()) {
				swap(arrived);
			}
		}

		return this;
	}

	private void swap(final Element arrived) {
		if (!OUT_OF_BAND.equals(arrived.attr("hx-swap-oob"))) {
			throw new UnsupportedOperationException("Only regions swapped out of band are played out, not "
				+ arrived.cssSelector());
		}

		final Element shown = document.getElementById(arrived.id());

		if (shown == null) {
			throw new AssertionError("Nothing on screen is #" + arrived.id());
		}

		shown.replaceWith(arrived.clone());
	}

	private static Map<String, String> sent(final Element element, final Map<String, String> values) {
		final Map<String, String> sent = new LinkedHashMap<>();

		if (element.hasAttr("hx-vals") && !element.attr("hx-vals").startsWith(SCRIPTED)) {
			JSON.fromJson(element.attr("hx-vals"), Map.class)
				.forEach((key, value) -> sent.put(String.valueOf(key), String.valueOf(value)));
		}

		sent.putAll(values);
		return sent;
	}

	private static void refuseWhatIsNotPlayedOut(final Element element) {
		if (!element.hasAttr("hx-post")) {
			throw new AssertionError("Nothing is sent by " + element.cssSelector());
		}

		final Element carrier = element.closest("[hx-swap]");

		if (carrier == null || !NONE.equals(carrier.attr("hx-swap"))) {
			throw new UnsupportedOperationException("Only hx-swap=\"none\" is played out");
		}

		for (Element holder = element; holder != null; holder = holder.parent()) {
			for (final Attribute attribute : holder.attributes()) {
				if (attribute.getKey().startsWith("hx-") && !UNDERSTOOD.contains(attribute.getKey())) {
					throw new UnsupportedOperationException(attribute.getKey() + " is not played out");
				}
			}
		}
	}

	private static String encode(final Map<String, String> values) {
		return values.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
			+ "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)
		).collect(Collectors.joining("&"));
	}
}
