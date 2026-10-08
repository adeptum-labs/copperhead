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

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the browser engine keeps the studio's cookies, storage and caches: a folder of Copperhead's own in the
 * cache home. The engine locks its folder against a second process, so a folder shared with another program would
 * keep either from starting while the other runs, and would hand one program's cookies to the other's pages.
 */
public class CefProfile {
	private static final String CACHE_HOME = "XDG_CACHE_HOME";
	private static final String HOME_CACHE = ".cache";
	private static final String FOLDER = "copperhead/cef";
	/* Chromium marks a profile in use with this link, naming the host and process that hold it. */
	private static final String LOCK = "SingletonLock";

	private final Path directory;

	public CefProfile() {
		this(System.getenv(CACHE_HOME), Path.of(System.getProperty("user.home")));
	}

	/* The XDG specification has a relative cache home ignored, the same as one left unset. */
	CefProfile(final String cacheHome, final Path home) {
		final boolean named = cacheHome != null && Path.of(cacheHome).isAbsolute();

		directory = (named ? Path.of(cacheHome) : home.resolve(HOME_CACHE)).resolve(FOLDER);
	}

	public Path directory() {
		return directory;
	}

	public boolean held() {
		return Files.isSymbolicLink(directory.resolve(LOCK));
	}
}
