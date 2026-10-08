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

package org.copperhead.core.graphics;

import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpriteTest {
	@Property
	public void shouldShowTheColourPaintedIntoACell(@ForAll @IntRange(max = 255) final int cell,
		@ForAll @IntRange(max = 3) final int colour) {

		final Sprite painted = Sprite.blank().paint(cell, colour);

		assertEquals(colour, painted.pixel(cell % Sprite.SIDE, cell / Sprite.SIDE));
	}

	@Example
	public void shouldLeaveTheSpriteItPaintedFromAsItWas() {
		final Sprite blank = Sprite.blank();

		blank.paint(0, 1);
		assertTrue(blank.pixels().stream().allMatch(pixel -> pixel == 0));
	}

	@Example
	public void shouldRefuseAColourAHardwareSpriteCannotShow() {
		assertThrows(IllegalArgumentException.class, () -> Sprite.blank().paint(0, 4));
		assertThrows(IllegalArgumentException.class, () -> Sprite.blank().paint(0, -1));
	}

	@Example
	public void shouldRefuseACellOutsideTheSprite() {
		assertThrows(IndexOutOfBoundsException.class, () -> Sprite.blank().paint(256, 1));
	}
}
