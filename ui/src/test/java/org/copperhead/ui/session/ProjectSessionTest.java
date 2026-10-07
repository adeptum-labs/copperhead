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

package org.copperhead.ui.session;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProjectSessionTest {
	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
	private final ProjectSession session = new ProjectSession(now::get);

	@Example
	public void shouldForgetTheNoteOfTheLastChange() {
		session.change(state -> state.withNote("Built nightfall.adf · 2,911 bytes"));

		assertEquals("", session.change(UnaryOperator.identity()).note());
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
}
