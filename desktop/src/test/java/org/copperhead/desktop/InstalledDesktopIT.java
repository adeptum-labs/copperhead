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

package org.copperhead.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the installed package does for the desktop it was installed on, looked for after a real install on this
 * computer: the menu entry the package ships. A runner has no desktop menu to register that entry with, so it is
 * the file itself that is looked at. The removal comes last, and what it should take with it is looked for then.
 * It installs software, so it runs only where copperhead.installed is true, which only the workflow sets.
 */
@EnabledOnOs(OS.LINUX)
@EnabledIfSystemProperty(named = "copperhead.installed", matches = "true")
@Timeout(value = 20, unit = TimeUnit.MINUTES)
public class InstalledDesktopIT {
	private static final Path DIST = Path.of("target", "dist").toAbsolutePath();
	private static final String WINDOW_CLASS = "org-copperhead-shell-Copperhead";

	private static Path launcher;
	private static Path menuEntry;

	@BeforeAll
	static void install() throws Exception {
		launcher = Installation.install(DIST);
		menuEntry = launcher.getParent().getParent().resolve("lib").resolve("copperhead-Copperhead.desktop");
	}

	/* The removal is the last thing, once everything the install left has been looked at. */
	@AfterAll
	static void removeAndLookForWhatIsLeft() throws Exception {
		Installation.uninstall();

		assertFalse(Files.exists(launcher), "The program stayed after the removal");
		assertFalse(Files.exists(menuEntry), "The menu entry stayed after the removal");
	}

	@Test
	public void shouldShipAMenuEntryUnderDevelopmentThatPointsAtTheProgram() throws Exception {
		assertTrue(Files.isRegularFile(menuEntry), "The package shipped no menu entry at " + menuEntry);

		final String entry = Files.readString(menuEntry);

		assertTrue(entry.contains("\nCategories=Development;IDE;\n"), entry);
		assertTrue(entry.contains("\nExec=" + launcher + "\n"), entry);
		assertTrue(entry.contains("\nStartupWMClass=" + WINDOW_CLASS + "\n"), entry);
	}
}
