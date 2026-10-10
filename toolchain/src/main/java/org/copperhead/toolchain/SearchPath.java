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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Finds a program by name the way a shell does: the first executable file of that name in the PATH folders. */
public final class SearchPath {
	private SearchPath() {
	}

	public static Function<String, Optional<Path>> of(final String path) {
		final List<Path> folders = Arrays.stream(path.split(File.pathSeparator)).filter(folder -> !folder.isBlank())
			.map(Path::of).toList();

		return name -> folders.stream().map(folder -> folder.resolve(name))
			.filter(file -> Files.isRegularFile(file) && Files.isExecutable(file)).findFirst();
	}

	public static Function<String, Optional<Path>> system() {
		return of(Objects.requireNonNullElse(System.getenv("PATH"), ""));
	}
}
