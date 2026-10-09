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

package org.copperhead.toolchain;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XdgDirsTest {
	private static final Path HOME = Path.of("/home/someone");
	private static final XdgDirs IN_HOME = new XdgDirs(Path.of("/home/someone/.config/copperhead"),
		Path.of("/home/someone/.local/share/copperhead"), Path.of("/home/someone/.cache/copperhead"));

	@Example
	public void shouldKeepItsFoldersInTheBaseDirectoriesNamed() {
		final Map<String, String> env = Map.of("XDG_CONFIG_HOME", "/etc/xdg", "XDG_DATA_HOME", "/srv/data",
			"XDG_CACHE_HOME", "/tmp/c");

		assertEquals(new XdgDirs(Path.of("/etc/xdg/copperhead"), Path.of("/srv/data/copperhead"),
			Path.of("/tmp/c/copperhead")), XdgDirs.of(env, HOME));
	}

	@Example
	public void shouldKeepItsFoldersInTheHomeFolderWhenNoneIsNamed() {
		assertEquals(IN_HOME, XdgDirs.of(Map.of(), HOME));
	}

	@Example
	public void shouldIgnoreAnEmptyBlankOrRelativeBaseDirectory() {
		assertAll(List.of("", "  ", "config", "./config").stream().map(named -> () -> assertEquals(IN_HOME,
			XdgDirs.of(Map.of("XDG_CONFIG_HOME", named, "XDG_DATA_HOME", named, "XDG_CACHE_HOME", named), HOME),
			named)));
	}

	@Example
	public void shouldFindTheFoldersOfTheUserRunningTheStudio() {
		final XdgDirs dirs = XdgDirs.system();

		assertAll(List.of(dirs.config(), dirs.data(), dirs.cache()).stream().map(dir -> () -> {
			assertTrue(dir.isAbsolute(), dir.toString());
			assertEquals(Path.of("copperhead"), dir.getFileName());
		}));
	}
}
