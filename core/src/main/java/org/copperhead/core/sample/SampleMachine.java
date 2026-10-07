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

package org.copperhead.core.sample;

import java.util.List;
import java.util.stream.IntStream;
import org.copperhead.core.graphics.AmigaColor;
import org.copperhead.core.graphics.CopperList;
import org.copperhead.core.graphics.Palette;
import org.copperhead.core.graphics.Sprite;

/** What the machine holds when a sample project opens: its palette, its sprite and their colours. */
public final class SampleMachine {
	private static final int COLOURS = 32;
	private static final double LAST = COLOURS - 1;
	private static final double HALF = 0.5;
	private static final double SATURATION = 0.85;
	private static final double BRIGHTEST = 0.92;
	private static final int CHANNEL = 15;
	private static final double CENTRE = 7.5;
	private static final double BALL = 7.4;
	private static final double RIM = 5.9;
	private static final int LIGHT_FROM = 5;
	private static final double LIGHT = 2.4;
	private static final int SHADED_FROM = 19;
	private static final int COPPER_BASE = 0x3F800;

	private SampleMachine() {
	}

	/** A ramp from deep blue through white to brown, the plasma's colours. */
	public static Palette palette() {
		return new Palette(IntStream.range(0, COLOURS).mapToObj(i -> ramp(i / LAST)).toList());
	}

	public static List<AmigaColor> spriteColours() {
		return List.of(AmigaColor.parse("$0D42"), AmigaColor.parse("$0FB7"), AmigaColor.parse("$0611"));
	}

	/** A ball lit from the top left: base colour, a highlight and a shaded rim. */
	public static Sprite ball() {
		return new Sprite(IntStream.range(0, Sprite.SIDE * Sprite.SIDE).mapToObj(SampleMachine::ballCell).toList());
	}

	/** The plasma's copper list: one bitplane, then a colour change on two lines. */
	public static CopperList copperList() {
		return CopperList.of(COPPER_BASE, 0x0100, 0x1200, 0x0180, 0x0000, 0x3001, 0xFFFE, 0x0180, 0x0A3F,
			0x3101, 0xFFFE, 0x0180, 0x0B4F, 0xFFFF, 0xFFFE);
	}

	private static int ballCell(final int cell) {
		final int x = cell % Sprite.SIDE;
		final int y = cell / Sprite.SIDE;
		final double distance = Math.hypot(x - CENTRE, y - CENTRE);

		if (distance > BALL) {
			return 0;
		}

		if (Math.hypot(x - LIGHT_FROM, y - LIGHT_FROM) < LIGHT) {
			return 2;
		}

		return distance > RIM || x + y > SHADED_FROM ? 3 : 1;
	}

	private static AmigaColor ramp(final double t) {
		final double hue = t < HALF ? 230 - t * 120 : 20 + (t - HALF) * 60;
		final double light = Math.min(BRIGHTEST, 0.12 + 0.75 * Math.sin(t * Math.PI));

		return AmigaColor.of(channel(hue, light, 0), channel(hue, light, 8), channel(hue, light, 4));
	}

	/* The CSS hsl() conversion, so the ramp matches what the design was drawn with. */
	private static int channel(final double hue, final double light, final int offset) {
		final double chroma = SATURATION * Math.min(light, 1 - light);
		final double k = (offset + hue / 30) % 12;

		return (int) Math.round((light - chroma * Math.max(-1, Math.min(Math.min(k - 3, 9 - k), 1))) * CHANNEL);
	}
}
