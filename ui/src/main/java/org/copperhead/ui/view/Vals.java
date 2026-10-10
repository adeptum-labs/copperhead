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

package org.copperhead.ui.view;

import java.util.Map;
import java.util.stream.Collectors;

/** The value a control sends with its action, written as the JSON htmx reads from {@code hx-vals}. */
public final class Vals {
	private static final char LINE_SEPARATOR = 0x2028;
	private static final char PARAGRAPH_SEPARATOR = 0x2029;

	private Vals() {
	}

	public static String of(final String name, final Object value) {
		return of(Map.of(name, value));
	}

	/**
	 * Values a script of the page works out as the control is sent. htmx reads an {@code hx-vals} of {@code js:}
	 * as an object literal, so the script's object is spread into one.
	 */
	public static String spread(final String expression) {
		return "js:{..." + expression + "}";
	}

	public static String of(final Map<String, ?> values) {
		return values.entrySet().stream()
			.map(entry -> quoted(entry.getKey()) + ":" + quoted(String.valueOf(entry.getValue())))
			.collect(Collectors.joining(",", "{", "}"));
	}

	/* A folder may be named with any character, and JSON allows no raw control character in a string. */
	private static String quoted(final String value) {
		final StringBuilder quoted = new StringBuilder("\"");

		value.chars().forEach(character -> quoted.append(escaped((char) character)));
		return quoted.append('"').toString();
	}

	private static String escaped(final char character) {
		return switch (character) {
			case '"' -> "\\\"";
			case '\\' -> "\\\\";
			case '\n' -> "\\n";
			case '\r' -> "\\r";
			case '\t' -> "\\t";
			default -> character < ' ' || character == LINE_SEPARATOR || character == PARAGRAPH_SEPARATOR
				? "\\u%04x".formatted((int) character) : String.valueOf(character);
		};
	}
}
