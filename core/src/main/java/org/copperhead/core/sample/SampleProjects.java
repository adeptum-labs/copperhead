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

package org.copperhead.core.sample;

import java.time.Duration;
import java.util.List;
import org.copperhead.core.debug.PcTrace;
import org.copperhead.core.project.Exit;
import org.copperhead.core.project.Part;
import org.copperhead.core.project.Project;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.TimelineEvent;

/** The two projects the studio opens until projects are read from disk: a demo and a game. */
public final class SampleProjects {
	private static final String NIGHTFALL = "nightfall";
	private static final String BLITZBALL = "blitzball";
	private static final String GAME_LOOP = "main.s";
	private static final Exit GAME_OVER = new Exit("Game over", "Title");

	private SampleProjects() {
	}

	public static Project named(final String name) {
		return switch (name) {
			case NIGHTFALL -> nightfall();
			case BLITZBALL -> blitzball();
			default -> throw new IllegalArgumentException("No sample project is named " + name);
		};
	}

	public static Project nightfall() {
		return new Project("nightfall.chp", NIGHTFALL, ProjectKind.DEMO, seconds(108),
			"nightfall.mod · 31 patterns · 4ch", 125,
			List.of(SampleSources.plasma(), SampleSources.intro(), SampleSources.copper()),
			new PcTrace("plasma.s", List.of(11, 12, 13, 14)), 13,
			List.of(
				part("Loader", 0, 6, "1 bpl · trackloader", "loader.s", 250),
				part("Intro", 6, 18, "logo · 4 bpl · 16 col", "intro.s", 30),
				part("Plasma", 18, 42, "1 bpl + copper · sintab", "plasma.s", 150),
				part("Vector balls", 42, 68, "blitter · 16 sprites", "balls.s", 300),
				part("Greetings", 68, 92, "scroller · 8px font", "greets.s", 200),
				part("End", 92, 108, "fade · loop", "end.s", 60)),
			List.of(event(6, "logo in"), event(18, "plasma"), event(42, "balls"), event(92, "fade")));
	}

	public static Project blitzball() {
		return new Project("blitzball.chp", BLITZBALL, ProjectKind.GAME, seconds(90),
			"blitzball.mod · 12 patterns · 4ch", 140,
			List.of(SampleSources.main(), SampleSources.player(), SampleSources.copper()),
			new PcTrace(GAME_LOOP, List.of(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)), 11,
			List.of(
				part("Title", 0, 8, "logo · press fire", "title.s", 250),
				looping("Level 1", 8, 38, "game loop · 50 fps · 16 sprites", 150, "Level 2"),
				looping("Level 2", 38, 68, "game loop · faster ball", 30, "Ending"),
				part("Ending", 68, 90, "score · credits → title", "ending.s", 300)),
			List.of());
	}

	private static Part part(final String name, final int start, final int end, final String meta, final String file,
		final int hue) {

		return new Part(name, seconds(start), seconds(end), meta, file, hue, List.of());
	}

	private static Part looping(final String name, final int start, final int end, final String meta, final int hue,
		final String cleared) {

		return new Part(name, seconds(start), seconds(end), meta, GAME_LOOP, hue,
			List.of(GAME_OVER, new Exit("Level clear", cleared)));
	}

	private static TimelineEvent event(final int at, final String label) {
		return new TimelineEvent(seconds(at), label);
	}

	private static Duration seconds(final int seconds) {
		return Duration.ofSeconds(seconds);
	}
}
