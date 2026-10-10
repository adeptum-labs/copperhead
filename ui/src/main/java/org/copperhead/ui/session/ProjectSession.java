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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.copperhead.core.project.ProjectLayout;
import org.copperhead.core.project.Target;
import org.copperhead.core.sample.SampleProjects;
import org.copperhead.toolchain.ProjectDirectory;

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

	/* A launch takes up the most recent project that still opens; with none, the studio waits on its welcome. */
	private static StudioState launch(final Preferences preferences) {
		final StudioState empty = StudioState.open(SampleProjects.nightfall(), Target.A500_OCS)
			.withLayout(preferences.layout()).withEnvironment(Environment.of(preferences.recent()));
		final StudioState launched = preferences.recent().stream().map(ProjectSession::reopen)
			.flatMap(Optional::stream).findFirst().map(empty::enter).orElse(empty).withNote("");

		return preferences.note().map(launched::withNote).orElse(launched);
	}

	private static Optional<ProjectDirectory> reopen(final Path chp) {
		try {
			return Optional.of(open(chp));
		} catch (IOException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	/**
	 * Opens a project file. One called something other than the name it holds is refused: the studio saves a project
	 * under its name, so that would write, and on a rename delete, another file than the one opened. So is one whose
	 * sources cannot be listed, a src folder that is a link for one: the editor could never be drawn for it.
	 */
	public static ProjectDirectory open(final Path chp) throws IOException {
		if (!Files.isRegularFile(chp)) {
			throw new IOException(chp + " is not a project file");
		}

		final ProjectDirectory directory = ProjectDirectory.open(chp);
		final String expected = ProjectLayout.chp(directory.file().name());

		if (!chp.getFileName().toString().equals(expected)) {
			throw new IOException(chp.getFileName() + " holds the project " + directory.file().name()
				+ ", so the file has to be called " + expected);
		}

		directory.sources();
		return directory;
	}

	public synchronized StudioState state() {
		return settle();
	}

	/**
	 * Applies a change. The note in the status bar tells what the last action did, so every change starts
	 * without one. A change that throws leaves the studio as it was, and the page keeps what it shows.
	 */
	public StudioState change(final UnaryOperator<StudioState> change) {
		return update(current -> change.apply(current.withNote("")));
	}

	/**
	 * Applies a change that no answer draws, so the note the page has not yet been shown stays: a build ending while
	 * a caret report is on its way would otherwise lose its result. A change that throws leaves the studio as it was.
	 */
	public synchronized StudioState update(final UnaryOperator<StudioState> change) {
		state = change.apply(settle());
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
