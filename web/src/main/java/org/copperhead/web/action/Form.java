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

package org.copperhead.web.action;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** The values a form carried, so that an action reads what it was given rather than a request. */
public final class Form {
	private static final String PAIRS = "&";
	private static final String ASSIGN = "=";

	private final Map<String, String> values;

	private Form(final Map<String, String> values) {
		this.values = values;
	}

	public static Form parse(final String body) {
		final Map<String, String> values = new LinkedHashMap<>();

		if (body != null && !body.isBlank()) {
			for (final String pair : body.split(PAIRS)) {
				final int at = pair.indexOf(ASSIGN);

				if (at > 0) {
					values.put(decode(pair.substring(0, at)), decode(pair.substring(at + 1)));
				}
			}
		}

		return new Form(values);
	}

	public String get(final String name) {
		return values.get(name);
	}

	public boolean has(final String name) {
		final String value = values.get(name);

		return value != null && !value.isEmpty();
	}

	private static String decode(final String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}
}
