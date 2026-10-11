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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/** Finds a program by name the way a shell does: the first executable file of that name in the PATH folders. */
public final class SearchPath {
	private SearchPath() {
	}

	/** Whether a path is a program that can run: a folder may carry the execute bit, but it runs nothing. */
	public static boolean usable(final Path path) {
		return Files.isRegularFile(path) && Files.isExecutable(path);
	}

	/* An entry no path can be made of names no folder, as a shell would pass over it. */
	public static Function<String, Optional<Path>> of(final String path) {
		final List<Path> folders = Arrays.stream(path.split(File.pathSeparator)).filter(folder -> !folder.isBlank())
			.flatMap(SearchPath::folder).toList();

		return name -> folders.stream().map(folder -> folder.resolve(name)).filter(SearchPath::usable).findFirst();
	}

	public static Function<String, Optional<Path>> system() {
		return of(Objects.requireNonNullElse(System.getenv("PATH"), ""));
	}

	private static Stream<Path> folder(final String folder) {
		try {
			return Stream.of(Path.of(folder));
		} catch (final InvalidPathException e) {
			return Stream.empty();
		}
	}
}
