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

package org.copperhead.ui.controller;

import java.util.List;
import java.util.Locale;
import org.copperhead.core.debug.Breakpoint;
import org.copperhead.core.sample.SampleMachine;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.BreakpointView;
import org.copperhead.ui.view.CopperRowView;
import org.copperhead.ui.view.DebuggerView;
import org.copperhead.ui.view.MemoryRowView;
import org.copperhead.ui.view.RegisterView;
import org.copperhead.ui.view.ToolView;

final class DebuggerViews {
	/* Where the plasma's code is loaded, so the program counter reads as an address rather than a line. */
	private static final int CODE_BASE = 0xC0E30;
	private static final int INSTRUCTION = 2;
	private static final String SUPERVISOR = "2700";

	private DebuggerViews() {
	}

	static ToolView tool(final StudioState state) {
		final String at = state.project().trace().file() + ":" + state.pc();

		return new ToolView(List.of(ToolViews.title("Debugger")), state.running() ? "running" : "stopped at " + at,
			new DebuggerView(state.running(), status(state, at),
				SampleMachine.registers(state.d0()).stream()
					.map(register -> new RegisterView(register.name(), register.hex())).toList(),
				"%08X".formatted(CODE_BASE + state.pc() * INSTRUCTION), SUPERVISOR,
				SampleMachine.copperList().instructions().stream()
					.map(row -> new CopperRowView(row.location(), row.words(), row.decode())).toList(),
				SampleMachine.memory().stream()
					.map(row -> new MemoryRowView(row.address(), row.words(), row.text())).toList(),
				state.breakpoints().stream()
					.map(mark -> new BreakpointView(mark.location(), hits(mark.hits()))).toList()));
	}

	private static String status(final StudioState state, final String at) {
		if (state.running()) {
			return "running · " + at;
		}

		return state.breakpoints().stream().filter(mark -> mark.location().equals(at)).findFirst()
			.map((Breakpoint mark) -> "stopped · " + at + " · " + hits(mark.hits())).orElse("stopped · " + at);
	}

	private static String hits(final int hits) {
		return String.format(Locale.ROOT, "%,d hits", hits);
	}
}
