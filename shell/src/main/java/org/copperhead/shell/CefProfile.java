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

import java.nio.file.Files;
import java.nio.file.Path;
import org.copperhead.toolchain.XdgDirs;

/**
 * Where the browser engine keeps the studio's cookies, storage and caches: a folder of Copperhead's own in the
 * cache home. The engine locks its folder against a second process, so a folder shared with another program would
 * keep either from starting while the other runs, and would hand one program's cookies to the other's pages.
 */
public class CefProfile {
	private static final String FOLDER = "cef";
	/* Chromium marks a profile in use with this link, naming the host and process that hold it. */
	private static final String LOCK = "SingletonLock";

	private final Path directory;

	public CefProfile() {
		this(XdgDirs.system());
	}

	CefProfile(final XdgDirs dirs) {
		directory = dirs.cache().resolve(FOLDER);
	}

	public Path directory() {
		return directory;
	}

	public boolean held() {
		return Files.isSymbolicLink(directory.resolve(LOCK));
	}
}
