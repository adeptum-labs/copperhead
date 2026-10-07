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
import java.util.stream.Stream;
import lombok.With;
import org.copperhead.core.build.BuildOptions;
import org.copperhead.core.build.BuildReport;
import org.copperhead.core.build.Builds;
import org.copperhead.core.debug.Breakpoint;
import org.copperhead.core.graphics.Sprite;
import org.copperhead.core.project.Project;
import org.copperhead.core.project.Target;
import org.copperhead.core.sample.SampleMachine;
import org.copperhead.ui.view.Modal;
import org.copperhead.ui.view.Tool;

/**
 * Everything the studio shows, in one value. A change makes a new one, so what a region draws can never be
 * half of one action and half of another.
 */
@With
public record StudioState(Project project, Target target, Tool tool, Modal modal, boolean running,
	boolean timelineShown, String note, String file, int cursor, List<Breakpoint> breakpoints, int pc, int d0,
	Sprite sprite, int spriteColour, int paletteIndex, BuildOptions options, BuildReport build,
	Duration position) {

	private static final int BYTE = 0xFF;
	private static final int FIRST_D0 = 0x17;
	private static final int FIRST_HITS = 1170;
	private static final int BASE_COLOUR = 1;
	private static final int FIRST_PICK = 4;
	private static final Duration FIRST_POSITION = Duration.ofMillis(23_400);

	public StudioState {
		breakpoints = List.copyOf(breakpoints);
	}

	/** A project as it opens: stopped at its breakpoint, playing from inside its third part, last build shown. */
	public static StudioState open(final Project project, final Target target) {
		final String traced = project.trace().file();
		final BuildOptions options = BuildOptions.defaults();

		return new StudioState(project, target, Tool.CODE, Modal.NONE, true, true, "", traced, project.breakpoint(),
			List.of(new Breakpoint(traced, project.breakpoint(), FIRST_HITS)), project.breakpoint(), FIRST_D0,
			SampleMachine.ball(), BASE_COLOUR, FIRST_PICK, options, Builds.canned(project, target, options),
			FIRST_POSITION);
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

	/** Opens a source; the traced one opens where the run stopped, any other at its top. */
	public StudioState openFile(final String name) {
		project.source(name);
		return withFile(name).withCursor(name.equals(project.trace().file()) ? pc : 1);
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
		return build.warnings().stream().findFirst()
			.map(warning -> withTool(Tool.CODE).withFile(warning.file()).withCursor(warning.line()))
			.orElse(this);
	}

	/* The run stops for a step, the way a debugger halts the machine before moving it on by one instruction. */
	public StudioState step() {
		final String traced = project.trace().file();
		final int next = project.trace().after(pc);

		return withRunning(false).withPc(next).withD0(d0 + 1 & BYTE).withBreakpoints(breakpoints.stream()
			.map(mark -> mark.file().equals(traced) && mark.line() == next ? mark.hit() : mark).toList());
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

	private void requireLine(final int line) {
		project.source(file).line(line);
	}
}
