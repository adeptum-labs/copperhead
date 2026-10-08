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

package org.copperhead.core.sample;

import java.util.List;
import java.util.stream.IntStream;
import net.jqwik.api.Example;
import org.copperhead.core.graphics.Palette;
import org.copperhead.core.graphics.Sprite;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SampleMachineTest {
	@Example
	public void shouldRampThePaletteFromDeepBlueThroughWhiteToBrown() {
		final Palette palette = SampleMachine.palette();

		assertEquals(32, palette.size());
		assertEquals("$0013", palette.color(0).hex());
		assertEquals("$015C", palette.color(4).hex());
		assertEquals("$0BFF", palette.color(13).hex());
		assertEquals("$0330", palette.color(31).hex());
	}

	@Example
	public void shouldDrawTheBallWithAHighlightAndAShadedRim() {
		final Sprite ball = SampleMachine.ball();
		final List<Long> counts = IntStream.range(0, Sprite.COLOURS)
			.mapToObj(colour -> ball.pixels().stream().filter(pixel -> pixel == colour).count()).toList();

		assertEquals(List.of(84L, 71L, 21L, 80L), counts);
		assertEquals(0, ball.pixel(0, 0));
		assertEquals(2, ball.pixel(5, 5));
		assertEquals(1, ball.pixel(7, 7));
	}

	@Example
	public void shouldColourTheBallInCopperTones() {
		assertEquals(List.of("$0D42", "$0FB7", "$0611"),
			SampleMachine.spriteColours().stream().map(color -> color.hex()).toList());
	}
}
