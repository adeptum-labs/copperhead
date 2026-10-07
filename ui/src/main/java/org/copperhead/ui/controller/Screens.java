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

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.IndexView;
import org.copperhead.ui.view.Region;
import org.copperhead.ui.view.Regions;
import org.copperhead.web.action.View;

/** Draws the regions of the window from the state of the studio. */
public final class Screens {
	public static final String TITLE = "Copperhead";

	private Screens() {
	}

	public static IndexView index(final StudioState state) {
		final Map<Region, View> regions = new EnumMap<>(Region.class);

		Arrays.stream(Region.values()).forEach(region -> regions.put(region, render(state, region)));
		return new IndexView(TITLE, regions);
	}

	/* The status bar always comes along, because every change clears its note and a bar left alone would go on
	   showing one that no longer holds. */
	public static Regions regions(final StudioState state, final Region... regions) {
		return new Regions(Stream.concat(Arrays.stream(regions), Stream.of(Region.STATUS)).distinct()
			.map(region -> render(state, region)).toList());
	}

	public static Regions all(final StudioState state) {
		return regions(state, Region.values());
	}

	/** The regions a change to the running machine reaches: the controls, the tool, the screen, time and status. */
	public static Regions playback(final StudioState state) {
		return regions(state, Region.MACHINE, Region.TOOL, Region.EMULATOR, Region.TIMELINE, Region.STATUS);
	}

	private static View render(final StudioState state, final Region region) {
		return switch (region) {
			case MENUS -> Menus.of(state);
			case MACHINE -> ChromeViews.machine(state);
			case RAIL -> ChromeViews.rail(state);
			case TOOL -> ToolViews.of(state);
			case STATUS -> StatusViews.of(state);
			case EMULATOR -> EmulatorViews.of(state);
			case TIMELINE -> TimelineViews.of(state);
			case MODAL -> ModalViews.of(state);
		};
	}
}
