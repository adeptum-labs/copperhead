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
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.With;
import org.copperhead.core.build.BuildOption;
import org.copperhead.core.build.BuildOptions;
import org.copperhead.core.build.BuildReport;
import org.copperhead.core.debug.Breakpoint;
import org.copperhead.core.graphics.Sprite;
import org.copperhead.core.project.Project;
import org.copperhead.core.project.ProjectFile;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.ProjectLayout;
import org.copperhead.core.project.Target;
import org.copperhead.core.sample.SampleMachine;
import org.copperhead.core.sample.SampleProjects;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.ui.view.Modal;
import org.copperhead.ui.view.Tool;

/**
 * Everything the studio shows, in one value. A change makes a new one, so what a region draws can never be
 * half of one action and half of another.
 *
 * <p>The project on disk is the workspace; the file, cursor, breakpoints, options and build are its. The sample
 * project stands in for what is not real yet: the machine's run, the debugger's trace, the timeline and the screen.
 */
@With
public record StudioState(Project project, Target target, Tool tool, Modal modal, boolean running,
	boolean timelineShown, String note, String file, int cursor, List<Breakpoint> breakpoints, int pc, int d0,
	Sprite sprite, int spriteColour, int paletteIndex, BuildOptions options, Optional<BuildReport> build,
	Duration position, Optional<ProjectDirectory> workspace, Layout layout, BuildRun run, Environment environment) {

	private static final int BYTE = 0xFF;
	private static final int FIRST_D0 = 0x17;
	private static final int TOP = 1;
	private static final int BASE_COLOUR = 1;
	private static final int FIRST_PICK = 4;
	private static final Duration FIRST_POSITION = Duration.ofMillis(23_400);
	private static final char SEPARATOR = '/';
	private static final String NOTHING_TO_BUILD = "Nothing to build: open or create a project on disk first";

	public StudioState {
		breakpoints = List.copyOf(breakpoints);
	}

	/** The studio with no project open, the sample running from inside its third part, nothing built. */
	public static StudioState open(final Project project, final Target target) {
		return new StudioState(project, target, Tool.CODE, Modal.NONE, true, true, "", "", TOP, List.of(),
			project.breakpoint(), FIRST_D0, SampleMachine.ball(), BASE_COLOUR, FIRST_PICK,
			BuildOptions.defaults(), Optional.empty(), FIRST_POSITION, Optional.empty(), Layout.defaults(),
			BuildRun.idle(), Environment.of(List.of()));
	}

	/**
	 * The project in a directory, opened at its entry file and not built yet, with the sample of its kind standing
	 * in for its run. The layout, the tool shown and what was found around the studio stay: they are the person's.
	 */
	public StudioState enter(final ProjectDirectory directory) {
		final ProjectFile opened = directory.file();
		final Project standIn = opened.kind() == ProjectKind.GAME ? SampleProjects.blitzball()
			: SampleProjects.nightfall();

		return open(standIn, opened.target()).withTool(tool).withTimelineShown(timelineShown).withLayout(layout)
			.withEnvironment(environment).withWorkspace(Optional.of(directory))
			.withFile(sourceName(opened.entry())).withBreakpoints(opened.breakpoints())
			.withOptions(opened.options())
			.withNote("Opened %s (%s)".formatted(ProjectLayout.chp(opened.name()),
				opened.kind().name().toLowerCase(Locale.ROOT)));
	}

	/** The name the editor knows a source by, from the path a project file or the assembler gives it. */
	public static String sourceName(final String path) {
		return path.substring(path.lastIndexOf(SEPARATOR) + 1);
	}

	public boolean breakpointAt(final String in, final int line) {
		return breakpoints.stream().anyMatch(mark -> mark.file().equals(in) && mark.line() == line);
	}

	/** Sets a breakpoint on a line of the open file, or takes away the one already there. */
	public StudioState toggleBreakpoint(final int line) {
		requireLine(line);

		if (breakpointAt(file, line)) {
			return withBreakpoints(breakpoints.stream()
				.filter(mark -> !mark.file().equals(file) || mark.line() != line).toList());
		}

		return withBreakpoints(Stream.concat(breakpoints.stream(), Stream.of(new Breakpoint(file, line, 0)))
			.toList());
	}

	/** Opens a source of the project at its top; the caller has made sure the project holds it. */
	public StudioState openFile(final String name) {
		requireProject();
		return withFile(name).withCursor(TOP);
	}

	public StudioState moveCursor(final int line) {
		requireLine(line);
		return withCursor(line);
	}

	public StudioState clearBreakpoints() {
		return withBreakpoints(List.of());
	}

	/** Shows the line the first warning of the last build points at. */
	public StudioState showWarning() {
		return build.stream().flatMap(report -> report.warnings().stream()).findFirst()
			.map(warning -> withTool(Tool.CODE).withFile(warning.file()).withCursor(warning.line()))
			.orElse(this);
	}

	/* The run stops for a step, the way a debugger halts the machine before moving it on by one instruction. */
	public StudioState step() {
		final String traced = project.trace().file();
		final int next = project.trace().after(pc);

		return withRunning(false).withPc(next).withD0((d0 + 1) & BYTE).withBreakpoints(breakpoints.stream()
			.map(mark -> mark.file().equals(traced) && mark.line() == next ? mark.hit() : mark).toList());
	}

	/** The run moved on by the time given; past the end it starts over, as a demo on a loop does. */
	public StudioState advance(final Duration elapsed) {
		if (elapsed.isZero()) {
			return this;
		}

		return withPosition(Duration.ofMillis(Math.floorMod(position.plus(elapsed).toMillis(),
			project.length().toMillis())));
	}

	/** Moves the run to a moment, held inside it: before the start is the start, past the end is the end. */
	public StudioState seek(final Duration at) {
		if (at.isNegative()) {
			return withPosition(Duration.ZERO);
		}

		return withPosition(at.compareTo(project.length()) > 0 ? project.length() : at);
	}

	public StudioState playPart(final int index) {
		return seek(project.parts().get(index).start());
	}

	public StudioState takeExit(final String part) {
		return playPart(project.part(part));
	}

	public StudioState reset() {
		return withRunning(false).withPosition(Duration.ZERO).withPc(project.trace().first()).withD0(0);
	}

	public StudioState build(final boolean andRun) {
		return withRunning(running || andRun).withNote(NOTHING_TO_BUILD);
	}

	public StudioState toggleOption(final BuildOption option) {
		return withOptions(options.toggle(option));
	}

	public StudioState paintSprite(final int cell) {
		return withSprite(sprite.paint(cell, spriteColour));
	}

	public StudioState pickSpriteColour(final int colour) {
		return withSpriteColour(Sprite.requireColour(colour));
	}

	public StudioState clearSprite() {
		return withSprite(Sprite.blank());
	}

	public StudioState pickColour(final int index) {
		SampleMachine.palette().color(index);
		return withPaletteIndex(index);
	}

	/* The editor holds more lines than were last saved, so a line is refused only when no file could have it. */
	private void requireLine(final int line) {
		requireProject();

		if (line < TOP) {
			throw new IllegalArgumentException(file + " has no line " + line);
		}
	}

	private void requireProject() {
		if (workspace.isEmpty()) {
			throw new IllegalStateException("No project is open");
		}
	}
}
