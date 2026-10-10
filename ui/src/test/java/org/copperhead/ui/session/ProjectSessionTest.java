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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.Target;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.ui.TempDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProjectSessionTest {
	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
	private TempDirectory home;
	private Preferences preferences;
	private ProjectSession session;

	@BeforeTry
	public void launch() throws Exception {
		home = new TempDirectory();
		preferences = new Preferences(home.path().resolve("preferences.json"));
		session = new ProjectSession(preferences, now::get);
	}

	@AfterTry
	public void remove() throws Exception {
		home.close();
	}

	private Path made(final String name) throws Exception {
		return ProjectDirectory.create(home.path(), name, ProjectKind.DEMO, Target.A500_OCS).chp();
	}

	@Example
	public void shouldForgetTheNoteOfTheLastChange() {
		session.change(state -> state.withNote("Built demo.adf · 1,031 bytes"));

		assertEquals("", session.change(UnaryOperator.identity()).note());
	}

	@Example
	public void shouldKeepTheNoteWhenAnUpdateIsNotDrawn() {
		session.change(state -> state.withNote("Built demo.adf · 1,031 bytes"));

		assertEquals("Built demo.adf · 1,031 bytes", session.update(state -> state.withRunning(false)).note());
		assertFalse(session.state().running());
	}

	@Example
	public void shouldLeaveTheStudioAsItWasWhenAnUpdateFails() {
		final StudioState before = session.state();

		assertThrows(IllegalArgumentException.class, () -> session.update(state -> {
			throw new IllegalArgumentException("refused");
		}));
		assertSame(before, session.state());
	}

	@Example
	public void shouldLeaveTheStudioAsItWasWhenAChangeFails() {
		final StudioState before = session.state();

		assertThrows(IllegalArgumentException.class, () -> session.change(state -> {
			throw new IllegalArgumentException("refused");
		}));
		assertSame(before, session.state());
	}

	@Example
	public void shouldMoveTheRunOnWhileTheMachineRuns() {
		now.set(Instant.EPOCH.plusSeconds(1));

		assertEquals(Duration.ofMillis(24_400), session.state().position());
	}

	@Example
	public void shouldHoldTheRunWhileTheMachineIsPaused() {
		session.change(state -> state.withRunning(false));
		now.set(Instant.EPOCH.plusSeconds(5));

		assertEquals(Duration.ofMillis(23_400), session.state().position());
	}

	@Example
	public void shouldGoRoundToTheStartAfterTheEnd() {
		now.set(Instant.EPOCH.plusSeconds(90));

		assertEquals(Duration.ofMillis(5_400), session.state().position());
	}

	@Example
	public void shouldLaunchOnTheWelcomeWhenNoProjectWasOpenedBefore() {
		assertEquals(Optional.empty(), session.state().workspace());
		assertEquals("", session.state().note());
	}

	@Example
	public void shouldLaunchOnTheMostRecentProjectThatStillOpens() throws Exception {
		final Path older = made("older");
		final Path newer = made("newer");

		preferences.used(older);
		preferences.used(newer);
		Files.writeString(newer, "{\"format\": 99, \"name\": \"newer\", \"kind\": \"DEMO\"}");

		final StudioState launched = new ProjectSession(preferences, now::get).state();

		assertEquals(older.getParent(), launched.workspace().orElseThrow().dir());
		assertEquals("main.s", launched.file());
		assertEquals("", launched.note());
	}

	@Example
	public void shouldLaunchInTheLayoutLeftLastTime() {
		preferences.layout(new Layout("light", 40, 300));

		assertEquals(new Layout("light", 40, 300), new ProjectSession(preferences, now::get).state().layout());
	}

	@Example
	public void shouldSayOnceThatThePreferencesCouldNotBeRead() throws Exception {
		final Path file = home.path().resolve("broken.json");

		Files.writeString(file, "not json");

		assertEquals("preferences were unreadable; moved to preferences.json.bak",
			new ProjectSession(new Preferences(file), now::get).state().note());
	}
}
