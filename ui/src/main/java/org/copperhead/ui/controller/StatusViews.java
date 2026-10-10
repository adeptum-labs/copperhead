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
import org.copperhead.core.build.BuildReport;
import org.copperhead.core.build.BuildWarning;
import org.copperhead.core.project.Part;
import org.copperhead.toolchain.PinnedSource;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.StatusView;
import org.copperhead.ui.view.Vals;

final class StatusViews {
	private StatusViews() {
	}

	static StatusView of(final StudioState state) {
		final List<BuildWarning> warnings = state.build().map(BuildReport::warnings).orElse(List.of());
		final int errors = state.build().map(report -> report.errors().size()).orElse(0);
		final boolean failed = state.build().filter(report -> !report.ok()).isPresent();
		final int at = state.project().partAt(state.position());
		final Part part = state.project().parts().get(at);

		return new StatusView("vasm " + PinnedSource.VASM.version(),
			failed ? "build failed · " + counted(errors, "error") : "0 errors", failed,
			warnings.isEmpty() ? "0 warnings" : counted(warnings.size(), "warning") + " · "
				+ StudioState.sourceName(warnings.getFirst().file()) + ":" + warnings.getFirst().line(),
			!warnings.isEmpty(), "last build " + BuildViews.outcome(state), state.note(),
			"Part %02d · %s".formatted(at + 1, part.name()), Clock.frames(state.position()),
			state.target().label(), state.layout().theme(),
			state.run().running() ? Vals.of("phase", state.run().phase()) : null,
			state.workspace().map(directory -> directory.dir().toString()).orElse(""), List.of());
	}

	private static String counted(final int count, final String what) {
		return count + " " + what + (count == 1 ? "" : "s");
	}
}
