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

import java.util.List;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AmigaColorTest {
	@Property
	public void shouldReadBackWhatItWrites(@ForAll @IntRange(max = 0xFFF) final int rgb) {
		assertEquals(new AmigaColor(rgb), AmigaColor.parse(new AmigaColor(rgb).hex()));
	}

	@Property
	public void shouldSplitIntoTheChannelsItWasMadeOf(@ForAll @IntRange(max = 15) final int red,
		@ForAll @IntRange(max = 15) final int green, @ForAll @IntRange(max = 15) final int blue) {

		final AmigaColor color = AmigaColor.of(red, green, blue);

		assertEquals(List.of(red, green, blue), List.of(color.red(), color.green(), color.blue()));
	}

	@Example
	public void shouldWriteTheWordWithItsLeadingZero() {
		assertEquals("$0D42", AmigaColor.of(0xD, 0x4, 0x2).hex());
	}

	@Example
	public void shouldWriteTheShortFormWithOneDigitAChannel() {
		assertEquals("$D42", AmigaColor.of(0xD, 0x4, 0x2).shortHex());
		assertEquals("$015", AmigaColor.of(0x0, 0x1, 0x5).shortHex());
	}

	@Example
	public void shouldRepeatEachChannelToFillAModernScreen() {
		assertEquals("#DD4422", AmigaColor.parse("$0D42").css());
	}

	@Example
	public void shouldRefuseWhatTwelveBitsCannotHold() {
		assertThrows(IllegalArgumentException.class, () -> new AmigaColor(0x1000));
		assertThrows(IllegalArgumentException.class, () -> new AmigaColor(-1));
		assertThrows(IllegalArgumentException.class, () -> AmigaColor.of(16, 0, 0));
	}
}
