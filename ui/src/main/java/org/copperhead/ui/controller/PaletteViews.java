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

package org.copperhead.ui.controller;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.copperhead.core.graphics.AmigaColor;
import org.copperhead.core.graphics.Palette;
import org.copperhead.core.project.Target;
import org.copperhead.core.sample.SampleMachine;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.ColourView;
import org.copperhead.ui.view.PaletteView;
import org.copperhead.ui.view.ToolView;

final class PaletteViews {
	private PaletteViews() {
	}

	static ToolView tool(final StudioState state) {
		final Palette palette = SampleMachine.palette();
		final boolean aga = state.target() == Target.A1200_AGA;
		final List<ColourView> colours = IntStream.range(0, palette.size())
			.mapToObj(index -> colour(palette.color(index), index, index == state.paletteIndex())).toList();

		return new ToolView(List.of(ToolViews.title("palette.pal")),
			palette.size() + " colours · " + (aga ? "24-bit" : "12-bit"),
			new PaletteView(colours, colours.get(state.paletteIndex()),
				(aga ? "24-bit AGA" : "12-bit OCS") + " · 4 bits per channel shown",
				palette.colors().stream().map(AmigaColor::css)
					.collect(Collectors.joining(",", "--bar:linear-gradient(to bottom,", ")"))));
	}

	private static ColourView colour(final AmigaColor color, final int index, final boolean selected) {
		return new ColourView(index, color.hex(), color.css(), color.red(), color.green(), color.blue(), selected);
	}
}
