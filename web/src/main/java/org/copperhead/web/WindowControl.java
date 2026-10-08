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

package org.copperhead.web;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The operations a page cannot perform on its own window. Implemented by whatever hosts the
 * interface, so that the pages stay unaware of whether they are in a frame or a browser tab.
 */
public interface WindowControl {
	WindowControl NONE = new WindowControl() { };

	/** The edges a window can be resized from. The top is left to the title bar. */
	enum Edge {
		LEFT, RIGHT, BOTTOM, BOTTOM_RIGHT;

		/** The edge a page names in a path, as in {@code bottom-right}. */
		public static Optional<Edge> named(final String name) {
			return Arrays.stream(values()).filter(edge -> edge.pathName().equals(name)).findFirst();
		}

		public String pathName() {
			return name().toLowerCase(Locale.ROOT).replace('_', '-');
		}
	}

	default void minimise() {
	}

	default void toggleMaximise() {
	}

	default void close() {
	}

	/**
	 * Begins moving the window with the pointer. Only the start and the end are reported, because
	 * a request per pointer movement would make dragging as slow as the round trip.
	 */
	default void beginMove() {
	}

	default void endMove() {
	}

	/** Begins resizing the window from an edge with the pointer, reported like a move. */
	default void beginResize(final Edge edge) {
	}

	default void endResize() {
	}
}
