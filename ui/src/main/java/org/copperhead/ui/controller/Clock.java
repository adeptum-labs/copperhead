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

/** Moments of the run, written the way the transport, the parts and the status bar show them. */
final class Clock {
	/* A PAL Amiga draws fifty frames a second. */
	private static final long FRAME_MILLIS = 20;
	private static final long CENTIS = 10;
	private static final long MINUTE_CENTIS = 6000;
	private static final long SECOND_CENTIS = 100;
	private static final long MINUTE = 60;

	private Clock() {
	}

	static long frames(final Duration at) {
		return at.toMillis() / FRAME_MILLIS;
	}

	static String frame(final Duration at) {
		return "frame " + frames(at);
	}

	static String time(final Duration at) {
		final long centis = at.toMillis() / CENTIS;

		return "%d:%02d.%02d".formatted(centis / MINUTE_CENTIS, centis / SECOND_CENTIS % MINUTE,
			centis % SECOND_CENTIS);
	}

	static String span(final Duration from, final Duration to) {
		return minutes(from) + "–" + minutes(to);
	}

	static String minutes(final Duration at) {
		return "%d:%02d".formatted(at.toMinutes(), at.toSecondsPart());
	}
}
