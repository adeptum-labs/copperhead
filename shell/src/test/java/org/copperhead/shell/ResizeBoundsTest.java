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
import java.awt.Point;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;
import org.copperhead.web.WindowControl.Edge;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResizeBoundsTest {
	private static final Rectangle ORIGIN = new Rectangle(100, 100, 1000, 700);
	private static final Dimension MINIMUM = new Dimension(900, 640);

	@Test
	public void shouldGrowToTheRightWhenTheRightEdgeIsPulledOut() {
		assertEquals(
			new Rectangle(100, 100, 1050, 700),
			ResizeBounds.resized(Edge.RIGHT, ORIGIN, new Point(50, 999), MINIMUM)
		);
	}

	@Test
	public void shouldKeepTheRightEdgeStillWhenTheLeftEdgeIsPulledOut() {
		assertEquals(
			new Rectangle(60, 100, 1040, 700),
			ResizeBounds.resized(Edge.LEFT, ORIGIN, new Point(-40, 999), MINIMUM)
		);
	}

	@Test
	public void shouldGrowDownwardsWhenTheBottomEdgeIsPulledOut() {
		assertEquals(
			new Rectangle(100, 100, 1000, 730),
			ResizeBounds.resized(Edge.BOTTOM, ORIGIN, new Point(999, 30), MINIMUM)
		);
	}

	@Test
	public void shouldGrowBothWaysFromTheCorner() {
		assertEquals(
			new Rectangle(100, 100, 1050, 730),
			ResizeBounds.resized(Edge.BOTTOM_RIGHT, ORIGIN, new Point(50, 30), MINIMUM)
		);
	}

	@Test
	public void shouldStopAtTheMinimumWithTheRightEdgeStillWhenPushingTheLeftEdgeIn() {
		assertEquals(
			new Rectangle(200, 100, 900, 700),
			ResizeBounds.resized(Edge.LEFT, ORIGIN, new Point(500, 0), MINIMUM)
		);
	}

	@Test
	public void shouldStopAtTheMinimumWhenPushingTheCornerIn() {
		assertEquals(
			new Rectangle(100, 100, 900, 640),
			ResizeBounds.resized(Edge.BOTTOM_RIGHT, ORIGIN, new Point(-500, -500), MINIMUM)
		);
	}
}
