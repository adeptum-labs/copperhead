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

import java.util.function.UnaryOperator;
import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProjectSessionTest {
	private final ProjectSession session = new ProjectSession();

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
}
