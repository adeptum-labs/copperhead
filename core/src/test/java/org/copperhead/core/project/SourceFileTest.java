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

package org.copperhead.core.project;

import java.util.List;
import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SourceFileTest {
	private final SourceFile file = new SourceFile("player.s", List.of(
		new SourceLine(1, "", "", "", "; paddle"), new SourceLine(2, "move_player:", "", "", "")
	));

	@Example
	public void shouldFindALineByItsNumber() {
		assertEquals("move_player:", file.line(2).label());
		assertEquals(2, file.length());
	}

	@Example
	public void shouldRefuseALineItDoesNotHave() {
		assertThrows(IllegalArgumentException.class, () -> file.line(0));
		assertThrows(IllegalArgumentException.class, () -> file.line(3));
	}
}
