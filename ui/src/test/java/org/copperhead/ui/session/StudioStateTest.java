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

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.Example;
import org.copperhead.core.build.BuildReport;
import org.copperhead.core.build.BuildWarning;
import org.copperhead.core.debug.Breakpoint;
import org.copperhead.core.project.Target;
import org.copperhead.core.sample.SampleProjects;
import org.copperhead.ui.view.Modal;
import org.copperhead.ui.view.Tool;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StudioStateTest {
	private static final BuildReport WARNED = new BuildReport("nightfall.adf", List.of(),
		List.of(new BuildWarning("plasma.s", 24, "scaled index needs 68020")), List.of(), 2911, Duration.ZERO);

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
	public void shouldOpenOnTheCodeWithNothingBuilt() {
		assertEquals(Tool.CODE, opened.tool());
		assertEquals(Modal.NONE, opened.modal());
		assertTrue(opened.build().isEmpty());
	}

	@Example
	public void shouldBuildNothingOfASampleButStillRunIt() {
		final StudioState built = opened.withRunning(false).build(true);

		assertTrue(built.build().isEmpty());
		assertTrue(built.running());
		assertEquals("Nothing to build: open or create a project on disk first", built.note());
	}

	@Example
	public void shouldSwitchToAnotherProjectInTheSameLayoutWithNothingBuilt() {
		final StudioState game = opened.withTarget(Target.A1200_AGA).withTool(Tool.SPRITES).withTimelineShown(false)
			.switchTo(SampleProjects.blitzball());

		assertEquals("blitzball.chp", game.project().file());
		assertEquals(Target.A1200_AGA, game.target());
		assertEquals(Tool.SPRITES, game.tool());
		assertFalse(game.timelineShown());
		assertTrue(game.build().isEmpty());
		assertEquals("Opened blitzball.chp (game)", game.note());
	}

	@Example
	public void shouldSetABreakpointThatHasNotBeenHitYet() {
		assertTrue(opened.toggleBreakpoint(20).breakpoints().contains(new Breakpoint("plasma.s", 20, 0)));
	}

	@Example
	public void shouldTakeAwayTheBreakpointOnALineThatHasOne() {
		assertFalse(opened.toggleBreakpoint(13).breakpointAt("plasma.s", 13));
	}

	@Example
	public void shouldRefuseALineTheOpenFileDoesNotHave() {
		assertThrows(IllegalArgumentException.class, () -> opened.toggleBreakpoint(34));
		assertThrows(IllegalArgumentException.class, () -> opened.moveCursor(0));
	}

	@Example
	public void shouldOpenAnotherFileAtItsFirstLine() {
		final StudioState intro = opened.openFile("intro.s");

		assertEquals("intro.s", intro.file());
		assertEquals(1, intro.cursor());
		assertThrows(IllegalArgumentException.class, () -> opened.openFile("nowhere.s"));
	}

	@Example
	public void shouldShowTheFirstWarningOfTheBuildInTheCode() {
		final StudioState shown = opened.withBuild(Optional.of(WARNED)).withTool(Tool.SPRITES).openFile("intro.s")
			.showWarning();

		assertEquals(Tool.CODE, shown.tool());
		assertEquals("plasma.s", shown.file());
		assertEquals(24, shown.cursor());
	}

	@Example
	public void shouldStayWhereItIsWhenTheBuildWarnedOfNothing() {
		final StudioState game = StudioState.open(SampleProjects.blitzball(), Target.A500_OCS);

		assertSame(game, game.showWarning());
	}

	@Example
	public void shouldStopAndMoveToTheNextLineOnAStep() {
		final StudioState stepped = opened.step();

		assertFalse(stepped.running());
		assertEquals(14, stepped.pc());
		assertEquals(0x18, stepped.d0());
	}

	@Example
	public void shouldWrapTheCounterRegisterWithinAByteOnAStep() {
		assertEquals(0, opened.withD0(0xFF).step().d0());
	}

	@Example
	public void shouldLeaveEveryHitCountAloneWhenAStepReachesNoBreakpoint() {
		assertEquals(opened.breakpoints(), opened.step().breakpoints());
	}

	@Example
	public void shouldNotCountAHitForALineOfAnotherFile() {
		final StudioState marked = opened.openFile("intro.s").toggleBreakpoint(14);

		assertEquals(0, marked.step().breakpoints().stream().filter(mark -> mark.file().equals("intro.s"))
			.findFirst().orElseThrow().hits());
	}

	@Example
	public void shouldCountAHitWhenAStepReachesABreakpoint() {
		final StudioState round = opened.step().step().step().step();

		assertEquals(13, round.pc());
		assertEquals(1171, round.breakpoints().getFirst().hits());
	}
}
