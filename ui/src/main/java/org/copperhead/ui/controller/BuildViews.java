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
	private static final double MILLIS_PER_SECOND = 1000.0;

	private BuildViews() {
	}

	static ToolView tool(final StudioState state) {
		final String adf = state.project().adf();

		return new ToolView(List.of(ToolViews.title("Build & export")), adf, new BuildView(
			Arrays.stream(Target.values()).map(target -> new TargetCardView(target.label(), target.memory(),
				target == state.target(), Vals.of("target", target.chipset()))).toList(),
			Arrays.stream(BuildOption.values()).map(option -> new OptionView(label(option),
				state.options().on(option), Vals.of("option", option))).toList(),
			state.build().map(BuildReport::log).orElse(List.of()).stream()
				.map(line -> new BuildLineView(line.text(), line.tone().name().toLowerCase(Locale.ROOT)))
				.toList(),
			adf + " · " + outcome(state) + " · " + state.target().machine()));
	}

	/** What the last build came to, as the build tool and the status bar both put it. */
	static String outcome(final StudioState state) {
		return state.build().map(report -> String.format(Locale.ROOT, "%,d bytes · %.1fs", report.bytes(),
			report.took().toMillis() / MILLIS_PER_SECOND)).orElse("not built yet");
	}

	static String label(final BuildOption option) {
		return switch (option) {
			case SHRINKLER -> "Shrinkler -9";
			case SYMBOLS -> "Debug symbols";
		};
	}

	/* The menu has room to say what Shrinkler does with the program; the build tool only names the option. */
	static String menuLabel(final BuildOption option) {
		return switch (option) {
			case SHRINKLER -> "Crunch with " + label(option);
			case SYMBOLS -> label(option);
		};
	}
}
