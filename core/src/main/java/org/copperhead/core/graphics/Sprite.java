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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A hardware sprite: sixteen by sixteen cells, each transparent or one of the sprite's three colours, read
 * left to right and top to bottom.
 */
public record Sprite(List<Integer> pixels) {
	public static final int SIDE = 16;
	public static final int COLOURS = 4;
	private static final int CELLS = SIDE * SIDE;

	public Sprite {
		if (pixels.size() != CELLS) {
			throw new IllegalArgumentException("A sprite is 16 by 16 cells, not " + pixels.size());
		}

		pixels.forEach(Sprite::requireColour);
		pixels = List.copyOf(pixels);
	}

	public static Sprite blank() {
		return new Sprite(Collections.nCopies(CELLS, 0));
	}

	public static int requireColour(final int colour) {
		if (colour < 0 || colour >= COLOURS) {
			throw new IllegalArgumentException("A sprite cell is clear or one of three colours: " + colour);
		}

		return colour;
	}

	public int pixel(final int x, final int y) {
		return pixels.get(y * SIDE + x);
	}

	public Sprite paint(final int cell, final int colour) {
		final List<Integer> painted = new ArrayList<>(pixels);

		painted.set(cell, requireColour(colour));
		return new Sprite(painted);
	}
}
