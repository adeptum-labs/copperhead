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

package org.copperhead.shell;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Where the browser engine lives. An installed Copperhead carries it inside the application folder and names that
 * folder through a property; a development run has no such property and keeps the engine in the home folder.
 */
public class CefLocation {
	public static final String PROPERTY = "copperhead.jcef";
	private static final String HOME_FOLDER = ".copperhead/jcef";

	private final String bundled;
	private final Path home;

	public CefLocation() {
		this(System.getProperty(PROPERTY), Path.of(System.getProperty("user.home")));
	}

	CefLocation(final String bundled, final Path home) {
		this.bundled = bundled;
		this.home = home;
	}

	public boolean isBundled() {
		return Objects.nonNull(bundled) && !bundled.isBlank();
	}

	public File directory() {
		return isBundled() ? new File(bundled) : home.resolve(HOME_FOLDER).toFile();
	}
}
