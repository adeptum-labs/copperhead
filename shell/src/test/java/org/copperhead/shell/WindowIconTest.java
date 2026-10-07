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

import java.awt.Image;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class WindowIconTest {
	@Test
	public void shouldProvideTheLogoAtEveryStandardIconSize() {
		assertEquals(
			List.of(16, 24, 32, 48, 64, 128, 256),
			WindowIcon.images().stream().map(image -> image.getWidth(null)).toList()
		);
	}

	@Test
	public void shouldDrawEverySizeSquare() {
		for (Image image : WindowIcon.images()) {
			assertEquals(image.getWidth(null), image.getHeight(null));
		}
	}
}
