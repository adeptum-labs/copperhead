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

package org.copperhead.ui.controller;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.copperhead.core.graphics.AmigaColor;
import org.copperhead.core.sample.SampleMachine;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.CellView;
import org.copperhead.ui.view.SpriteView;
import org.copperhead.ui.view.SwatchView;
import org.copperhead.ui.view.ToolView;

final class SpriteViews {
	private static final List<String> NAMES = List.of("Base", "Highlight", "Shade");

	private SpriteViews() {
	}

	static ToolView tool(final StudioState state) {
		final List<AmigaColor> colours = SampleMachine.spriteColours();
		final List<Integer> pixels = state.sprite().pixels();
		final List<CellView> cells = IntStream.range(0, pixels.size())
			.mapToObj(cell -> new CellView(cell, pixels.get(cell))).toList();
		final List<SwatchView> swatches = IntStream.range(0, colours.size() + 1)
			.mapToObj(index -> new SwatchView(index, label(index, colours), index == state.spriteColour()))
			.toList();
		final String properties = IntStream.range(0, colours.size())
			.mapToObj(index -> "--c" + (index + 1) + ":" + colours.get(index).css())
			.collect(Collectors.joining(";"));

		return new ToolView(List.of(ToolViews.title("ball.spr")), "16×16 · 4 colours · sprite 0",
			new SpriteView(cells, swatches, properties));
	}

	private static String label(final int index, final List<AmigaColor> colours) {
		return index == 0 ? "Transparent" : NAMES.get(index - 1) + " · " + colours.get(index - 1).shortHex();
	}
}
