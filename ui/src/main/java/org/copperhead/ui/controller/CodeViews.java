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

package org.copperhead.ui.controller;

import java.util.List;
import org.copperhead.core.project.SourceFile;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.CodeLineView;
import org.copperhead.ui.view.CodeView;
import org.copperhead.ui.view.TabView;
import org.copperhead.ui.view.ToolView;
import org.copperhead.ui.view.Vals;

/** The code editor: a tab per source, the traced one marked, and the open source line by line. */
final class CodeViews {
	private CodeViews() {
	}

	static ToolView tool(final StudioState state) {
		final String traced = state.project().trace().file();
		final int marks = state.breakpoints().size();
		final List<TabView> tabs = state.project().sources().stream().map(SourceFile::name)
			.map(name -> new TabView(name, "/action/file", Vals.of("name", name), name.equals(state.file()),
				name.equals(traced)))
			.toList();

		return new ToolView(tabs, "68000 · UTF-8 · ln " + state.cursor() + " · " + marks
			+ (marks == 1 ? " breakpoint" : " breakpoints"), code(state, state.file().equals(traced)));
	}

	private static CodeView code(final StudioState state, final boolean traced) {
		return new CodeView(state.project().source(state.file()).lines().stream()
			.map(line -> new CodeLineView(line.number(), line.label(), line.mnemonic(), line.operands(),
				line.comment(), state.breakpointAt(state.file(), line.number()),
				traced && line.number() == state.pc(), line.number() == state.cursor()))
			.toList());
	}
}
