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

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.Example;
import org.copperhead.core.build.BuildOption;
import org.copperhead.core.build.BuildOptions;
import org.copperhead.core.build.BuildReport;
import org.copperhead.core.build.BuildWarning;
import org.copperhead.core.debug.Breakpoint;
import org.copperhead.core.project.ProjectFile;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.Target;
import org.copperhead.core.sample.SampleProjects;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.ui.view.Modal;
import org.copperhead.ui.view.Tool;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StudioStateTest {
	private static final ProjectDirectory BLITZ = new ProjectDirectory(Path.of("/projects/blitz"),
		ProjectFile.create("blitz", ProjectKind.GAME, Target.A1200_AGA)
			.withBreakpoints(List.of(new Breakpoint("main.s", 7, 0))));

	private final StudioState opened = StudioState.open(SampleProjects.nightfall(), Target.A500_OCS);
	private final StudioState game = opened.enter(BLITZ);

	private static BuildReport report(final List<BuildWarning> warnings, final List<BuildWarning> errors) {
		return new BuildReport(errors.isEmpty() ? "blitz.adf" : "", List.of(), warnings, errors, 0, Duration.ZERO);
	}

	@Example
	public void shouldOpenWithNoProjectAndNothingBuilt() {
		assertEquals(Optional.empty(), opened.workspace());
		assertEquals(List.of(), opened.breakpoints());
		assertEquals(Optional.empty(), opened.build());
		assertEquals(BuildRun.idle(), opened.run());
		assertEquals(Layout.defaults(), opened.layout());
	}

	@Example
	public void shouldOpenPlayingInsideThePlasma() {
		assertTrue(opened.running());
		assertEquals(Duration.ofMillis(23_400), opened.position());
		assertEquals(2, opened.project().partAt(opened.position()));
	}

	@Example
	public void shouldOpenOnTheCode() {
		assertEquals(Tool.CODE, opened.tool());
		assertEquals(Modal.NONE, opened.modal());
	}

	@Example
	public void shouldEnterAProjectAtItsEntryWithItsMachineOptionsAndBreakpoints() {
		assertEquals(Optional.of(BLITZ), game.workspace());
		assertEquals("main.s", game.file());
		assertEquals(1, game.cursor());
		assertEquals(Target.A1200_AGA, game.target());
		assertEquals(new BuildOptions(true, false), game.options());
		assertEquals(List.of(new Breakpoint("main.s", 7, 0)), game.breakpoints());
		assertEquals("Opened blitz.chp (game)", game.note());
	}

	@Example
	public void shouldLetTheSampleOfItsKindStandInForTheRun() {
		assertEquals(SampleProjects.blitzball(), game.project());
	}

	@Example
	public void shouldEnterAnotherProjectInTheSameLayoutWithNothingBuilt() {
		final StudioState entered = game.withTool(Tool.SPRITES).withTimelineShown(false)
			.withLayout(new Layout("light", 40, 300)).withBuild(Optional.of(report(List.of(), List.of())))
			.enter(BLITZ);

		assertEquals(Tool.SPRITES, entered.tool());
		assertFalse(entered.timelineShown());
		assertEquals(new Layout("light", 40, 300), entered.layout());
		assertEquals(Optional.empty(), entered.build());
	}

	@Example
	public void shouldSetABreakpointThatHasNotBeenHitYet() {
		assertTrue(game.toggleBreakpoint(20).breakpoints().contains(new Breakpoint("main.s", 20, 0)));
	}

	@Example
	public void shouldTakeAwayTheBreakpointOnALineThatHasOne() {
		assertFalse(game.toggleBreakpoint(7).breakpointAt("main.s", 7));
	}

	@Example
	public void shouldRefuseALineBeforeTheFirst() {
		assertThrows(IllegalArgumentException.class, () -> game.toggleBreakpoint(0));
		assertThrows(IllegalArgumentException.class, () -> game.moveCursor(0));
	}

	@Example
	public void shouldRefuseTheEditorWithNoProjectOpen() {
		assertThrows(IllegalStateException.class, () -> opened.toggleBreakpoint(3));
		assertThrows(IllegalStateException.class, () -> opened.openFile("main.s"));
	}

	@Example
	public void shouldOpenAnotherFileAtItsFirstLine() {
		final StudioState sprite = game.moveCursor(9).openFile("sprite.i");

		assertEquals("sprite.i", sprite.file());
		assertEquals(1, sprite.cursor());
	}

	@Example
	public void shouldStayWhereItIsWhenTheBuildWarnedOfNothing() {
		final StudioState built = game.withBuild(Optional.of(report(List.of(), List.of())));

		assertSame(built, built.showWarning());
	}

	@Example
	public void shouldNameASourceByTheLastPartOfItsPath() {
		assertEquals("main.s", StudioState.sourceName("src/main.s"));
		assertEquals("main.s", StudioState.sourceName("main.s"));
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
		assertEquals(game.breakpoints(), game.step().breakpoints());
	}

	@Example
	public void shouldCountAHitWhenAStepReachesABreakpointOfTheTrace() {
		final StudioState round = opened.withBreakpoints(List.of(new Breakpoint("plasma.s", 13, 0)))
			.step().step().step().step();

		assertEquals(13, round.pc());
		assertEquals(1, round.breakpoints().getFirst().hits());
	}

	@Example
	public void shouldHoldTheProjectFileTheWayTheStudioChangedIt() {
		final ProjectFile file = game.withTarget(Target.A500_OCS).toggleOption(BuildOption.SHRINKLER)
			.toggleBreakpoint(3).projectFile().orElseThrow();

		assertEquals(Target.A500_OCS, file.target());
		assertFalse(file.crunch());
		assertEquals(List.of(new Breakpoint("main.s", 7, 0), new Breakpoint("main.s", 3, 0)), file.breakpoints());
	}
}
