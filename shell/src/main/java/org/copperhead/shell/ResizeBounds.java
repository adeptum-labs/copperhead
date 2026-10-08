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

package org.copperhead.shell;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import org.copperhead.web.WindowControl.Edge;

/** Where a window ends up once one of its edges has travelled with the pointer. */
public final class ResizeBounds {
	private ResizeBounds() {
	}

	public static Rectangle resized(final Edge edge, final Rectangle origin, final Point travel,
		final Dimension minimum) {

		final int wider = Math.max(minimum.width, origin.width + travel.x);
		final int taller = Math.max(minimum.height, origin.height + travel.y);

		return switch (edge) {
			case LEFT -> {
				final int width = Math.max(minimum.width, origin.width - travel.x);

				yield new Rectangle(origin.x + origin.width - width, origin.y, width, origin.height);
			}
			case RIGHT -> new Rectangle(origin.x, origin.y, wider, origin.height);
			case BOTTOM -> new Rectangle(origin.x, origin.y, origin.width, taller);
			case BOTTOM_RIGHT -> new Rectangle(origin.x, origin.y, wider, taller);
		};
	}
}
