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

package org.copperhead.ui.view;

import org.copperhead.web.action.View;

/** The machine's screen and what it reports while it runs; the meters reach the stylesheet as {@code --fill}. */
public record EmulatorView(String machine, boolean running, String title, String info, long frame, int raster,
	String chip, int chipUsed, int blitter) implements View {

	public String rasterFill() {
		return fill(raster);
	}

	public String chipFill() {
		return fill(chipUsed);
	}

	public String blitterFill() {
		return fill(blitter);
	}

	private static String fill(final int percent) {
		return "--fill:" + percent + "%";
	}

	@Override
	public String template() {
		return "fragments/emulator :: emulator";
	}
}
