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

package org.copperhead.core.graphics;

/** A colour as the OCS chipset holds it: four bits each of red, green and blue in one word, written {@code $0RGB}. */
public record AmigaColor(int rgb) {
	private static final int LARGEST = 0xFFF;
	private static final int CHANNEL = 0xF;
	private static final int RED = 8;
	private static final int GREEN = 4;
	private static final int HEX = 16;
	private static final String DOLLAR = "$";

	public AmigaColor {
		if (rgb < 0 || rgb > LARGEST) {
			throw new IllegalArgumentException("Not a 12-bit colour: " + rgb);
		}
	}

	public static AmigaColor of(final int red, final int green, final int blue) {
		return new AmigaColor(channel(red) << RED | channel(green) << GREEN | channel(blue));
	}

	/** Reads {@code $0RGB}, the way assembler sources and the palette tool write a colour. */
	public static AmigaColor parse(final String hex) {
		return new AmigaColor(Integer.parseInt(hex.startsWith(DOLLAR) ? hex.substring(1) : hex, HEX));
	}

	public int red() {
		return rgb >> RED & CHANNEL;
	}

	public int green() {
		return rgb >> GREEN & CHANNEL;
	}

	public int blue() {
		return rgb & CHANNEL;
	}

	public String hex() {
		return "$%04X".formatted(rgb);
	}

	/** The colour on a modern screen, each four-bit channel repeated to fill eight bits. */
	public String css() {
		return "#%1$X%1$X%2$X%2$X%3$X%3$X".formatted(red(), green(), blue());
	}

	private static int channel(final int value) {
		if (value < 0 || value > CHANNEL) {
			throw new IllegalArgumentException("Not a 4-bit channel: " + value);
		}

		return value;
	}
}
