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

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.copperhead.core.project.Part;
import org.copperhead.core.project.Project;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.EventView;
import org.copperhead.ui.view.ExitView;
import org.copperhead.ui.view.LoopView;
import org.copperhead.ui.view.PartView;
import org.copperhead.ui.view.TickView;
import org.copperhead.ui.view.TimelineView;
import org.copperhead.ui.view.Vals;

final class TimelineViews {
	private static final int TICKS = 6;
	/* A ProTracker module at speed 6 plays a row every 15 / BPM seconds. */
	private static final long ROW_MILLIS_AT_ONE_BPM = 15_000;
	private static final long PATTERN_ROWS = 64;
	private static final String COPPER = "copper.i · 212 WAITs";
	private static final double PERCENT = 100.0;

	private TimelineViews() {
	}

	static TimelineView of(final StudioState state) {
		final Project project = state.project();
		final Duration length = project.length();
		final int at = project.partAt(state.position());
		final List<PartView> parts = IntStream.range(0, project.parts().size())
			.mapToObj(index -> part(project.parts().get(index), index, index == at, length)).toList();
		final List<TickView> ticks = IntStream.range(0, TICKS)
			.mapToObj(index -> length.multipliedBy(index).dividedBy(TICKS - 1))
			.map(tick -> new TickView(Clock.minutes(tick), "--left:" + percent(tick, length))).toList();
		final List<EventView> events = project.events().stream()
			.map(event -> new EventView(event.label(), "--left:" + percent(event.at(), length))).toList();

		return new TimelineView(state.timelineShown(), state.running(), length.toMillis(),
			state.position().toMillis(), Clock.time(state.position()), Clock.time(length),
			tempo(project, state.position()), parts.get(at), parts, ticks, project.module(), COPPER, events,
			loop(project.parts().get(at)));
	}

	private static PartView part(final Part part, final int index, final boolean selected, final Duration length) {
		final String place = "--left:" + percent(part.start(), length)
			+ ";--width:" + percent(part.end().minus(part.start()), length) + ";--hue:" + part.hue();

		return new PartView(index, part.name(), Clock.span(part.start(), part.end()), part.meta(), part.file(),
			place, part.loops(), selected);
	}

	private static LoopView loop(final Part part) {
		final String pass = Clock.minutes(part.end().minus(part.start()));

		return new LoopView(part.loops(), "↻ " + part.name() + " · " + pass + " per pass · exits:",
			part.exits().stream()
			.map(exit -> new ExitView(exit.on() + " → " + exit.to(), Vals.of("to", exit.to()))).toList());
	}

	private static String tempo(final Project project, final Duration at) {
		final long rows = at.toMillis() * project.bpm() / ROW_MILLIS_AT_ONE_BPM;

		return "%d BPM · pos %02X row %02X".formatted(project.bpm(), rows / PATTERN_ROWS, rows % PATTERN_ROWS);
	}

	private static String percent(final Duration at, final Duration length) {
		return String.format(Locale.ROOT, "%.2f%%", at.toMillis() * PERCENT / length.toMillis());
	}
}
