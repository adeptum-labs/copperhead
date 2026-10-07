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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.copperhead.core.build.BuildOption;
import org.copperhead.core.build.BuildReport;
import org.copperhead.core.project.Target;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.BuildLineView;
import org.copperhead.ui.view.BuildView;
import org.copperhead.ui.view.OptionView;
import org.copperhead.ui.view.TargetCardView;
import org.copperhead.ui.view.ToolView;
import org.copperhead.ui.view.Vals;

final class BuildViews {
	private static final List<String> LABELS = List.of("Shrinkler -9", "Bootblock + trackloader", "Debug symbols");

	private BuildViews() {
	}

	static ToolView tool(final StudioState state) {
		final BuildReport report = state.build();

		return new ToolView(List.of(ToolViews.title("Build & export")), report.adf(), new BuildView(
			Arrays.stream(Target.values()).map(target -> new TargetCardView(target.label(), target.memory(),
				target == state.target(), Vals.of("target", target.chipset()))).toList(),
			Arrays.stream(BuildOption.values()).map(option -> new OptionView(LABELS.get(option.ordinal()),
				state.options().on(option), Vals.of("option", option))).toList(),
			report.log().stream()
				.map(line -> new BuildLineView(line.text(), line.tone().name().toLowerCase(Locale.ROOT)))
				.toList(),
			String.format(Locale.ROOT, "%s · %,d bytes · %.1fs · %s", report.adf(), report.bytes(),
				report.took().toMillis() / 1000.0, state.target().machine())));
	}
}
