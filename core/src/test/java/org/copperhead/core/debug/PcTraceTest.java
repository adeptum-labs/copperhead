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

package org.copperhead.core.debug;

import java.util.List;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PcTraceTest {
	private final PcTrace trace = new PcTrace("plasma.s", List.of(11, 12, 13, 14));

	@Example
	public void shouldMoveToTheNextLineRun() {
		assertEquals(12, trace.after(11));
	}

	@Example
	public void shouldGoRoundTheLoopAfterTheLastLine() {
		assertEquals(11, trace.after(14));
	}

	@Example
	public void shouldStartOverFromALineTheRunNeverReaches() {
		assertEquals(11, trace.after(99));
	}

	@Property
	public void shouldComeBackWhereItStartedAfterOneRound(@ForAll @IntRange(min = 0, max = 3) final int start) {
		int line = trace.lines().get(start);

		for (int i = 0; i < trace.lines().size(); i++) {
			line = trace.after(line);
		}

		assertEquals(trace.lines().get(start), line);
	}

	@Example
	public void shouldRefuseARunWithoutLines() {
		assertThrows(IllegalArgumentException.class, () -> new PcTrace("plasma.s", List.of()));
	}
}
