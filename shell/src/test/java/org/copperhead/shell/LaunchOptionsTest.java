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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LaunchOptionsTest {
	private static final String WINDOW_SYSTEM = "--window-system";

	@Test
	public void shouldDrawOnX11WhenNothingIsSaid() {
		assertEquals(WindowSystem.X11, LaunchOptions.of().orElseThrow().windowSystem());
	}

	@Test
	public void shouldTakeTheWindowSystemInAnyCase() {
		assertEquals(WindowSystem.WAYLAND, LaunchOptions.of("--window-system=wayland").orElseThrow().windowSystem());
		assertEquals(WindowSystem.AUTO, LaunchOptions.of("--window-system", "Auto").orElseThrow().windowSystem());
	}

	@Test
	public void shouldRefuseAWindowSystemItDoesNotKnow() {
		assertThrows(CommandLine.ParameterException.class, () -> LaunchOptions.of("--window-system=mir"));
	}

	@Test
	public void shouldRefuseAnOptionItDoesNotKnow() {
		assertThrows(CommandLine.ParameterException.class, () -> LaunchOptions.of("--frobnicate"));
	}

	@Test
	public void shouldStartNothingWhenAskedForHelp() {
		assertTrue(LaunchOptions.of("--help").isEmpty());
	}

	@Test
	public void shouldOfferTheWindowSystemWhereItCanBeChosen() {
		final String help = help(true);

		assertTrue(help.contains(WINDOW_SYSTEM), help);
	}

	@Test
	public void shouldKeepTheWindowSystemOutOfTheHelpWhereItCannotBeChosen() {
		final String help = help(false);

		assertFalse(help.contains(WINDOW_SYSTEM), help);
		assertEquals(WindowSystem.WAYLAND, LaunchOptions.of(false, WINDOW_SYSTEM + "=wayland").orElseThrow()
			.windowSystem());
	}

	private static String help(final boolean windowSystemChoosable) {
		final PrintStream stdout = System.out;
		final ByteArrayOutputStream printed = new ByteArrayOutputStream();

		System.setOut(new PrintStream(printed, true, StandardCharsets.UTF_8));

		try {
			LaunchOptions.of(windowSystemChoosable, "--help");
		} finally {
			System.setOut(stdout);
		}

		return printed.toString(StandardCharsets.UTF_8);
	}
}
