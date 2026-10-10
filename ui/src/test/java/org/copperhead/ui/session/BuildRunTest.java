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

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import net.jqwik.api.Example;
import org.copperhead.core.build.BuildLine;
import org.copperhead.core.build.Tone;
import org.copperhead.ui.session.BuildRun.Phase;
import org.copperhead.ui.session.BuildRun.TrustRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuildRunTest {
	private static final BuildLine FIRST = new BuildLine("vasm -quiet -m68000", Tone.PLAIN);
	private static final BuildLine SECOND = new BuildLine("crunched 512 bytes", Tone.PLAIN);
	private static final TrustRequest ASKED = new TrustRequest("vasm", "2.0g", "5f".repeat(32));

	@Example
	public void shouldStandIdleWithNothingWritten() {
		assertFalse(BuildRun.idle().running());
		assertEquals(List.of(), BuildRun.idle().log());
		assertEquals(Optional.empty(), BuildRun.idle().trust());
	}

	@Example
	public void shouldKeepEveryLineInTheOrderWritten() {
		final BuildRun run = BuildRun.started().add(FIRST).add(SECOND);

		assertTrue(run.running());
		assertEquals(List.of(FIRST, SECOND), run.log());
	}

	@Example
	public void shouldRunOnWhileItWaitsOnAQuestion() {
		final BuildRun waiting = BuildRun.started().add(FIRST).waiting(ASKED);

		assertEquals(Phase.WAITING, waiting.phase());
		assertTrue(waiting.running());
		assertEquals(Optional.of(ASKED), waiting.trust());
	}

	@Example
	public void shouldTakeTheQuestionDownOnceAnswered() {
		final BuildRun resumed = BuildRun.started().add(FIRST).waiting(ASKED).resumed();

		assertEquals(Phase.RUNNING, resumed.phase());
		assertEquals(Optional.empty(), resumed.trust());
		assertEquals(List.of(FIRST), resumed.log());
	}

	@Example
	public void shouldKeepWhatItWroteOnceEnded() {
		final BuildRun ended = BuildRun.started().add(FIRST).ended();

		assertFalse(ended.running());
		assertEquals(List.of(FIRST), ended.log());
	}

	@Example
	public void shouldKeepTheLastLinesOnlyAndSayHowManyWentBefore() {
		BuildRun run = BuildRun.started();

		for (int number = 1; number <= 5000; number++) {
			run = run.add(new BuildLine("line " + number, Tone.PLAIN));
		}

		assertEquals(BuildRun.LIMIT, run.log().size());
		assertEquals(3000, run.omitted());
		assertEquals(new BuildLine("... 3000 earlier lines left out", Tone.PLAIN), run.shown().getFirst());
		assertEquals("line 3001", run.shown().get(1).text());
		assertEquals("line 5000", run.shown().getLast().text());
		assertEquals(BuildRun.LIMIT + 1, run.shown().size());
	}

	@Example
	public void shouldShowAllLinesWhenNoneWereLeftOut() {
		assertEquals(List.of(FIRST, SECOND), BuildRun.started().add(FIRST).add(SECOND).shown());
	}

	@Example
	public void shouldLimitALogItIsGivenWhole() {
		final List<BuildLine> lines = IntStream.rangeClosed(1, BuildRun.LIMIT + 5)
			.mapToObj(number -> new BuildLine("line " + number, Tone.PLAIN)).toList();
		final List<BuildLine> shown = BuildRun.shown(lines, 0);

		assertEquals("... 5 earlier lines left out", shown.getFirst().text());
		assertEquals("line 6", shown.get(1).text());
		assertEquals(BuildRun.LIMIT + 1, shown.size());
	}
}
