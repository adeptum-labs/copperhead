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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.function.UnaryOperator;
import org.copperhead.core.project.Target;
import org.copperhead.core.sample.SampleProjects;

/**
 * The project open in the studio and everything shown of it. Controllers change it; regions are drawn from it.
 * The session keeps time for the run, so the page never has to say where its playhead got to: what it draws
 * from the next answer is where the machine is.
 */
@ApplicationScoped
public class ProjectSession {
	private final InstantSource clock;
	private StudioState state;
	private Instant since;

	@Inject
	public ProjectSession(final Preferences preferences) {
		this(preferences, steady());
	}

	public ProjectSession(final Preferences preferences, final InstantSource clock) {
		this.clock = clock;
		this.since = clock.instant();
		this.state = launch(preferences);
	}

	/* The wall clock can be set back or jump ahead while the machine runs; the run should only ever see time pass. */
	private static InstantSource steady() {
		final long origin = System.nanoTime();

		return () -> Instant.EPOCH.plusNanos(System.nanoTime() - origin);
	}

	/* A launch lays the window out as the person left it, and says so when their preferences could not be read. */
	private static StudioState launch(final Preferences preferences) {
		final StudioState opened = StudioState.open(SampleProjects.nightfall(), Target.A500_OCS)
			.withLayout(preferences.layout());

		return preferences.note().map(opened::withNote).orElse(opened);
	}

	public synchronized StudioState state() {
		return settle();
	}

	/**
	 * Applies a change. The note in the status bar tells what the last action did, so every change starts
	 * without one. A change that throws leaves the studio as it was, and the page keeps what it shows.
	 */
	public synchronized StudioState change(final UnaryOperator<StudioState> change) {
		state = change.apply(settle().withNote(""));
		return state;
	}

	private StudioState settle() {
		final Instant now = clock.instant();

		if (state.running()) {
			state = state.advance(Duration.between(since, now));
		}

		since = now;
		return state;
	}
}
