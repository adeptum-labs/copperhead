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
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.StatusView;

final class StatusViews {
	private StatusViews() {
	}

	static StatusView of(final StudioState state) {
		final List<BuildWarning> warnings = state.build().map(BuildReport::warnings).orElse(List.of());
		final int at = state.project().partAt(state.position());
		final Part part = state.project().parts().get(at);

		return new StatusView(warnings.isEmpty() ? "0 warnings" : warnings.size()
				+ (warnings.size() == 1 ? " warning · " : " warnings · ") + warnings.getFirst().location(),
			!warnings.isEmpty(), "last build " + BuildViews.outcome(state), state.note(),
			"Part %02d · %s".formatted(at + 1, part.name()), Clock.frames(state.position()),
			state.target().label());
	}
}
