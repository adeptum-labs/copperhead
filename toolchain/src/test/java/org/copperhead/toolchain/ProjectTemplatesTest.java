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

package org.copperhead.toolchain;

import java.util.List;
import java.util.Map;
import net.jqwik.api.Example;
import org.copperhead.core.project.ProjectKind;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProjectTemplatesTest {
	@Example
	public void shouldStartADemoWithOneSourceThatRunsAtTheLoadAddress() {
		final Map<String, String> demo = ProjectTemplates.sources(ProjectKind.DEMO);

		assertEquals(List.of("main.s"), List.copyOf(demo.keySet()));
		assertTrue(demo.get("main.s").startsWith("; " + ProjectTemplates.NAME + " - copper bars\n"));
		assertTrue(demo.get("main.s").contains("\torg\tLOAD\n"));
	}

	@Example
	public void shouldStartAGameWithItsSpriteInASourceOfItsOwn() {
		final Map<String, String> game = ProjectTemplates.sources(ProjectKind.GAME);

		assertEquals(List.of("main.s", "sprite.i"), List.copyOf(game.keySet()));
		assertTrue(game.get("main.s").contains("\tinclude\t\"sprite.i\"\n"));
		assertTrue(game.get("sprite.i").contains("sprite:\n"));
	}
}
