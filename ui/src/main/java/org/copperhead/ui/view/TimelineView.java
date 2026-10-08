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

import java.util.List;
import java.util.Locale;
import org.copperhead.web.action.View;

/**
 * The run laid out in time. The page moves the playhead itself while the machine runs, starting from the
 * position and length carried here; {@code --at} places it as a share of the run. Where the playing part ends,
 * at {@code next}, the page asks for the run to be drawn anew, since the part shows in more places than this one.
 */
public record TimelineView(boolean shown, boolean running, long length, long position, long next, int bpm,
	String time, String total, String pattern, PartView selected, List<PartView> parts, List<TickView> ticks,
	String music, String copper, List<EventView> events, LoopView loop) implements View {

	public TimelineView {
		parts = List.copyOf(parts);
		ticks = List.copyOf(ticks);
		events = List.copyOf(events);
	}

	public String at() {
		return String.format(Locale.ROOT, "--at:%.4f", (double) position / length);
	}

	@Override
	public String template() {
		return "fragments/timeline :: timeline";
	}
}
