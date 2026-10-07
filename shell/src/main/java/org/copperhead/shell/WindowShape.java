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

package org.copperhead.shell;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.util.Optional;

/**
 * The outline of the main window: rounded corners while it floats and square corners while it is
 * maximised, the way native windows behave.
 */
public final class WindowShape {
	private static final int RADIUS = 12;
	private static final int ARC = RADIUS * 2;

	private WindowShape() {
	}

	public static Optional<Shape> of(final int extendedState, final Dimension size) {
		if ((extendedState & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
			return Optional.empty();
		}

		return Optional.of(new RoundRectangle2D.Double(0, 0, size.width, size.height, ARC, ARC));
	}
}
