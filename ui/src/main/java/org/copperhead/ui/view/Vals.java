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

/** The value a control sends with its action, written as the JSON htmx reads from {@code hx-vals}. */
public final class Vals {
	private Vals() {
	}

	public static String of(final String name, final Object value) {
		return "{\"" + name + "\":\"" + escape(String.valueOf(value)) + "\"}";
	}

	private static String escape(final String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
