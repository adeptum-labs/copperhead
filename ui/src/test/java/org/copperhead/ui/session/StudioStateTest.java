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
import java.util.List;
import net.jqwik.api.Example;
import org.copperhead.core.debug.Breakpoint;
import org.copperhead.core.project.Target;
import org.copperhead.core.sample.SampleProjects;
import org.copperhead.ui.view.Modal;
import org.copperhead.ui.view.Tool;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StudioStateTest {
	private final StudioState opened = StudioState.open(SampleProjects.nightfall(), Target.A500_OCS);

	@Example
	public void shouldOpenAtTheBreakpointInTheTracedFile() {
		assertEquals("plasma.s", opened.file());
		assertEquals(13, opened.cursor());
		assertEquals(13, opened.pc());
		assertEquals(List.of(new Breakpoint("plasma.s", 13, 1170)), opened.breakpoints());
	}

	@Example
	public void shouldOpenPlayingInsideThePlasma() {
		assertTrue(opened.running());
		assertEquals(Duration.ofMillis(23_400), opened.position());
		assertEquals(2, opened.project().partAt(opened.position()));
	}

	@Example
	public void shouldOpenOnTheCodeWithTheLastBuildShown() {
		assertEquals(Tool.CODE, opened.tool());
		assertEquals(Modal.NONE, opened.modal());
		assertEquals("nightfall.adf", opened.build().adf());
	}
}
