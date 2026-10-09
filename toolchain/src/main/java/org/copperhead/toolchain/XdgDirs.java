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
import java.util.Map;

/**
 * Copperhead's own folders in the XDG base directories: settings in the config home, fetched tools in the data
 * home and anything that can be rebuilt in the cache home.
 */
public record XdgDirs(Path config, Path data, Path cache) {
	private static final String FOLDER = "copperhead";

	public static XdgDirs of(final Map<String, String> env, final Path home) {
		return new XdgDirs(base(env.get("XDG_CONFIG_HOME"), home.resolve(".config")),
			base(env.get("XDG_DATA_HOME"), home.resolve(".local/share")),
			base(env.get("XDG_CACHE_HOME"), home.resolve(".cache")));
	}

	public static XdgDirs system() {
		return of(System.getenv(), Path.of(System.getProperty("user.home")));
	}

	/* The XDG specification has a relative base directory ignored, the same as one left unset. */
	private static Path base(final String named, final Path fallback) {
		return (named != null && Path.of(named).isAbsolute() ? Path.of(named) : fallback).resolve(FOLDER);
	}
}
