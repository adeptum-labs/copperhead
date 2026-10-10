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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.copperhead.core.build.BuildLine;
import org.copperhead.core.build.BuildOption;
import org.copperhead.core.build.BuildReport;
import org.copperhead.core.project.ProjectLayout;
import org.copperhead.core.project.Target;
import org.copperhead.ui.session.BuildRun;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.session.ToolStatus;
import org.copperhead.ui.view.BuildLineView;
import org.copperhead.ui.view.BuildView;
import org.copperhead.ui.view.OptionView;
import org.copperhead.ui.view.TargetCardView;
import org.copperhead.ui.view.ToolLineView;
import org.copperhead.ui.view.ToolView;
import org.copperhead.ui.view.Vals;

final class BuildViews {
	private static final double MILLIS_PER_SECOND = 1000.0;

	private BuildViews() {
	}

	static ToolView tool(final StudioState state) {
		final String adf = state.workspace().map(directory -> ProjectLayout.adf(directory.file().name()))
			.orElse("no project open");

		return new ToolView(List.of(ToolViews.title("Build & export")), adf, new BuildView(
			Arrays.stream(Target.values()).map(target -> new TargetCardView(target.label(), target.memory(),
				target == state.target(), Vals.of("target", target.chipset()))).toList(),
			Arrays.stream(BuildOption.values()).map(option -> new OptionView(label(option),
				state.options().on(option), Vals.of("option", option))).toList(),
			state.environment().tools().stream().map(BuildViews::tool).toList(),
			log(state).stream().map(line -> new BuildLineView(line.text(),
				line.tone().name().toLowerCase(Locale.ROOT))).toList(),
			adf + " · " + outcome(state) + " · " + state.target().machine(), buildable(state),
			state.run().running()));
	}

	private static ToolLineView tool(final ToolStatus status) {
		return new ToolLineView(status.tool() + " " + status.version(), status.found().isPresent(),
			status.found().map(Path::toString).orElse("fetched and compiled on first use"));
	}

	/** A build can start with a project open and no other run going. */
	static boolean buildable(final StudioState state) {
		return state.workspace().isPresent() && !state.run().running();
	}

	/** What the last build came to, as the build tool and the status bar both put it. */
	static String outcome(final StudioState state) {
		if (state.run().running()) {
			return "building";
		}

		return state.build().map(report -> report.ok()
			? String.format(Locale.ROOT, "%,d bytes · %.1fs", report.bytes(), seconds(report))
			: String.format(Locale.ROOT, "failed · %.1fs", seconds(report))).orElse("not built yet");
	}

	private static double seconds(final BuildReport report) {
		return report.took().toMillis() / MILLIS_PER_SECOND;
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

	/* While a run goes on it shows what it wrote so far; after a build, the report's log; after a fetch, its own. */
	private static List<BuildLine> log(final StudioState state) {
		return state.run().running() || !state.run().log().isEmpty() ? state.run().shown()
			: BuildRun.shown(state.build().map(BuildReport::log).orElse(List.of()), 0);
	}
}
