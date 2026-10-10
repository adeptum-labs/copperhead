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

package org.copperhead.ui.session;

import lombok.With;

/**
 * How the person left the window: dark or light, the share of its width the tool pane takes, in percent, and the
 * height of the timeline in pixels. A size the window cannot show is pulled back to the nearest one it can.
 */
@With
public record Layout(String theme, int split, int bottom) {
	public static final String DARK = "dark";
	public static final String LIGHT = "light";

	private static final int NARROWEST = 36;
	private static final int WIDEST = 78;
	private static final int SPLIT = 56;
	private static final int LOWEST = 120;
	private static final int HIGHEST = 460;
	private static final int BOTTOM = 250;

	public Layout {
		if (!DARK.equals(theme) && !LIGHT.equals(theme)) {
			throw new IllegalArgumentException("No theme is called " + theme);
		}

		split = Math.clamp(split, NARROWEST, WIDEST);
		bottom = Math.clamp(bottom, LOWEST, HIGHEST);
	}

	public static Layout defaults() {
		return new Layout(DARK, SPLIT, BOTTOM);
	}
}
