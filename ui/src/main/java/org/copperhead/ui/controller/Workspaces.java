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

package org.copperhead.ui.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.copperhead.core.project.ProjectLayout;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.ui.session.StudioState;

/**
 * The project directory open in the studio, read and written for the actions. A disk that refuses fails the
 * action, which leaves the studio as it was.
 */
final class Workspaces {
	private Workspaces() {
	}

	@FunctionalInterface
	interface Reading<T> {
		T read() throws IOException;
	}

	@FunctionalInterface
	interface Writing {
		void write() throws IOException;
	}

	static <T> T read(final Reading<T> reading) {
		try {
			return reading.read();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static void write(final Writing writing) {
		try {
			writing.write();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Whether a path, or its nearest ancestor that exists, resolves with every link followed to a place inside the
	 * home folder. Comparing the paths as written would let a link in the home folder lead out of it.
	 */
	static boolean confined(final Path home, final Path path) {
		Path existing = path;

		while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
			existing = existing.getParent();
		}

		try {
			return existing != null && existing.toRealPath().startsWith(home.toRealPath());
		} catch (IOException e) {
			return false;
		}
	}

	/** What went wrong in words for the person; some exceptions carry no message of their own. */
	static String reason(final Exception failure) {
		return Optional.ofNullable(failure.getMessage()).orElseGet(() -> failure.getClass().getSimpleName());
	}

	static ProjectDirectory open(final StudioState state) {
		return state.workspace().orElseThrow(() -> new IllegalStateException("No project is open"));
	}

	static List<String> sources(final ProjectDirectory directory) {
		return read(directory::sources);
	}

	/** A source the page named: only a file of the project's src folder, never a path that leads out of it. */
	static String source(final ProjectDirectory directory, final String name) {
		if (!ProjectLayout.validSource(name) || !sources(directory).contains(name)) {
			throw new IllegalArgumentException("The project holds no source " + name);
		}

		return name;
	}
}
