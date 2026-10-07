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
import java.util.Locale;
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
	private static final String DATA = "data-";
	private static final String HTMX = "hx-";
	private static final Set<String> FIELDS = Set.of("input", "select", "textarea");
	private static final int FIRST_SUCCESS = 200;
	private static final int FIRST_REDIRECT = 300;
	private static final Jsonb JSON = JsonbBuilder.create();

	private final Client client;
	private final Document document;
	private int status;

	private Screen(final Client client, final String html, final int status) {
		this.client = client;
		this.document = Jsoup.parse(html);
		this.status = status;
	}

	/** The page the shell opens, entered with the token as the window enters it. */
	public static Screen open(final WorkbenchRig rig) throws Exception {
		final Client client = new Client(rig.base());
		final HttpResponse<String> response = client.get(rig.entrance().toString());

		return new Screen(client, response.body(), response.statusCode());
	}

	/** A page made by hand, so a test can show what is refused without a server to answer. */
	static Screen over(final Client client, final String html) {
		return new Screen(client, html, 200);
	}

	public Client client() {
		return client;
	}

	public Document document() {
		return document;
	}

	/** The status of the last answer; anything but a 2xx leaves the page as it was. */
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

		if (value(element, "hx-vals").startsWith(SCRIPTED)) {
			throw new UnsupportedOperationException(css + " works out values of its own; trigger it with them");
		}

		return send(element, Map.of());
	}

	/** Sends a control with the values its script would have worked out, since no script runs here. */
	public Screen trigger(final String css, final Map<String, String> values) throws Exception {
		return send(find(css), values);
	}

	/** Clicks the control that carries the key as data-key, which is how keys.js presses it. */
	public Screen press(final String key) throws Exception {
		return click("[data-key=\"" + key + "\"]");
	}

	private Screen send(final Element element, final Map<String, String> values) throws Exception {
		refuseWhatIsNotPlayedOut(element);

		final String form = encode(sent(element, values));
		final HttpResponse<String> response = client.submit(value(element, "hx-post"), form);

		refuseHeadersThatDirectHtmx(response);
		status = response.statusCode();

		if (status >= FIRST_SUCCESS && status < FIRST_REDIRECT) {
			for (final Element arrived : Jsoup.parseBodyFragment(response.body()).body().children()) {
				swap(arrived);
			}
		}

		return this;
	}

	private static void refuseHeadersThatDirectHtmx(final HttpResponse<String> response) {
		response.headers().map().keySet().stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(HTMX))
			.findFirst().ifPresent(name -> {
				throw new UnsupportedOperationException(name + " is not played out");
			});
	}

	private void swap(final Element arrived) {
		if (!OUT_OF_BAND.equals(value(arrived, "hx-swap-oob"))) {
			throw new UnsupportedOperationException("Only regions swapped out of band are played out, not "
				+ arrived.cssSelector());
		}

		if (!arrived.children().select("[hx-swap-oob], [data-hx-swap-oob], script").isEmpty()) {
			throw new UnsupportedOperationException("Neither a nested swap nor a script is played out in #"
				+ arrived.id());
		}

		final Element shown = document.getElementById(arrived.id());

		if (shown == null) {
			throw new AssertionError("Nothing on screen is #" + arrived.id());
		}

		shown.replaceWith(arrived.clone());
	}

	private static Map<String, String> sent(final Element element, final Map<String, String> values) {
		final Map<String, String> sent = new LinkedHashMap<>();

		if (has(element, "hx-vals") && !value(element, "hx-vals").startsWith(SCRIPTED)) {
			JSON.fromJson(value(element, "hx-vals"), Map.class)
				.forEach((key, value) -> sent.put(String.valueOf(key), String.valueOf(value)));
		}

		sent.putAll(values);
		return sent;
	}

	private static void refuseWhatIsNotPlayedOut(final Element element) {
		if (!has(element, "hx-post")) {
			throw new AssertionError("Nothing is sent by " + element.cssSelector());
		}

		final Element carrier = element.closest("[hx-swap], [data-hx-swap]");

		if (carrier == null || !NONE.equals(value(carrier, "hx-swap"))) {
			throw new UnsupportedOperationException("Only hx-swap=\"none\" is played out");
		}

		refuseFieldsHtmxWouldAdd(element);

		for (Element holder = element; holder != null; holder = holder.parent()) {
			for (final Attribute attribute : holder.attributes()) {
				refuseWhatIsNotUnderstood(element, holder, withoutData(attribute.getKey()));
			}
		}
	}

	/* htmx sends the enclosing form's fields and the control's own value along with the action, and this screen
	   sends neither. */
	private static void refuseFieldsHtmxWouldAdd(final Element element) {
		if (element.closest("form") != null || element.hasAttr("name") || FIELDS.contains(element.normalName())) {
			throw new UnsupportedOperationException("Fields sent along with " + element.cssSelector()
				+ " are not played out");
		}
	}

	private static void refuseWhatIsNotUnderstood(final Element element, final Element holder, final String name) {
		final boolean inherited = holder != element;

		if (name.startsWith(HTMX) && (!UNDERSTOOD.contains(name) || inherited && "hx-vals".equals(name))) {
			throw new UnsupportedOperationException(name + (inherited ? " from an ancestor" : "")
				+ " is not played out");
		}
	}

	private static String withoutData(final String name) {
		return name.startsWith(DATA + HTMX) ? name.substring(DATA.length()) : name;
	}

	private static boolean has(final Element element, final String name) {
		return element.hasAttr(name) || element.hasAttr(DATA + name);
	}

	private static String value(final Element element, final String name) {
		return element.hasAttr(name) ? element.attr(name) : element.attr(DATA + name);
	}

	private static String encode(final Map<String, String> values) {
		return values.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
			+ "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)
		).collect(Collectors.joining("&"));
	}
}
