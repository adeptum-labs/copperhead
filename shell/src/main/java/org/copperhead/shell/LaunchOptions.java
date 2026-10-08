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

package org.copperhead.shell;

import java.util.Optional;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;

@Command(name = "copperhead", description = "Copperhead, an Amiga demo and game studio.")
public final class LaunchOptions {
	private static final String WINDOW_SYSTEM = "--window-system";

	@Option(names = WINDOW_SYSTEM, paramLabel = "<system>", defaultValue = "x11",
		description = "What the page is drawn on in a Linux desktop session: x11, wayland or auto "
			+ "(defaults to ${DEFAULT-VALUE}, which embeds it in the window on X11 and Wayland desktops alike)."
	)
	private WindowSystem windowSystem;

	@Option(names = { "-h", "--help" }, usageHelp = true, description = "Show this help and exit.")
	private boolean help;

	private LaunchOptions() {
	}

	/** Empty when the help was asked for, which has then been printed instead. */
	public static Optional<LaunchOptions> of(final String... args) {
		return of(WindowSystem.CHOOSABLE, args);
	}

	/* Where there is no window system to choose, the option is still taken but kept out of the help. */
	static Optional<LaunchOptions> of(final boolean windowSystemChoosable, final String... args) {
		final LaunchOptions options = new LaunchOptions();
		final CommandLine commandLine = new CommandLine(options).setCaseInsensitiveEnumValuesAllowed(true);

		if (!windowSystemChoosable) {
			final CommandSpec spec = commandLine.getCommandSpec();
			final OptionSpec windowSystem = spec.findOption(WINDOW_SYSTEM);

			spec.remove(windowSystem);
			spec.addOption(OptionSpec.builder(windowSystem).hidden(true).build());
		}

		return CommandLine.printHelpIfRequested(commandLine.parseArgs(args)) ? Optional.empty()
			: Optional.of(options);
	}

	public WindowSystem windowSystem() {
		return windowSystem;
	}
}
