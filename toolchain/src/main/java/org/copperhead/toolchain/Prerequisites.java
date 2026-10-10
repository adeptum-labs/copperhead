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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The programs building the tools needs, and what to type to install the missing ones. The studio never runs the
 * installer itself: that takes the user's password.
 */
public final class Prerequisites {
	private static final List<String> NEEDED = List.of("make", "cc", "c++");
	/* ID names the distribution and ID_LIKE the ones it derives from; either decides the package manager. */
	private static final Pattern FAMILY = Pattern.compile("(?:ID|ID_LIKE)=[\"']?([^\"']*)[\"']?");
	private static final Set<String> APT = Set.of("debian", "ubuntu");
	private static final Set<String> DNF = Set.of("fedora", "rhel");

	private final Function<String, Optional<Path>> onPath;
	private final Path osRelease;

	public Prerequisites(final Function<String, Optional<Path>> onPath, final Path osRelease) {
		this.onPath = onPath;
		this.osRelease = osRelease;
	}

	public Optional<String> missing() {
		final List<String> absent = NEEDED.stream().filter(program -> onPath.apply(program).isEmpty()).toList();

		return absent.isEmpty() ? Optional.empty()
			: Optional.of(String.join(", ", absent) + " not found; " + advice());
	}

	private String advice() {
		final Set<String> family = family();

		if (family.stream().anyMatch(APT::contains)) {
			return "run sudo apt install build-essential";
		}
		if (family.stream().anyMatch(DNF::contains)) {
			return "run sudo dnf install gcc gcc-c++ make";
		}
		return "install make, cc and c++";
	}

	private Set<String> family() {
		try (Stream<String> lines = Files.lines(osRelease)) {
			return lines.map(FAMILY::matcher).filter(Matcher::matches)
				.flatMap(found -> Arrays.stream(found.group(1).split(" "))).collect(Collectors.toSet());
		} catch (final IOException | UncheckedIOException e) {
			return Set.of();
		}
	}
}
