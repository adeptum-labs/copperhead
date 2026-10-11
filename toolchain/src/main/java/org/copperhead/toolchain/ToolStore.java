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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The tools Copperhead built, one folder per tool and archive hash: {@code <root>/<tool>/<sha256>/<executable>}.
 * A tool built from a different archive lands beside the first instead of replacing it.
 */
public final class ToolStore {
	/* The hash names a folder, and a trusted hash comes from the preferences file, so it must not be a path. */
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

	private final Path root;

	public ToolStore(final Path root) {
		this.root = root;
	}

	public Optional<Path> find(final PinnedSource source, final String hash) {
		final String sha256 = hash.toLowerCase(Locale.ROOT);

		if (!SHA256.matcher(sha256).matches()) {
			return Optional.empty();
		}
		final Path executable = folder(source, sha256).resolve(source.executable());

		return Optional.of(executable).filter(SearchPath::usable);
	}

	/* Copied next to its final name and moved there in one step, so a build running at the same time never finds
	   half a program. */
	public Path install(final PinnedSource source, final String hash, final Path binary) throws IOException {
		final String sha256 = hash.toLowerCase(Locale.ROOT);

		if (!SHA256.matcher(sha256).matches()) {
			throw new IllegalArgumentException("not a sha-256: " + sha256);
		}
		final Path folder = Files.createDirectories(folder(source, sha256));
		final Path staged = Files.createTempFile(folder, source.executable(), ".part");

		try {
			Files.copy(binary, staged, StandardCopyOption.REPLACE_EXISTING);
			Files.setPosixFilePermissions(staged, PosixFilePermissions.fromString("rwxr-xr-x"));
			return Files.move(staged, folder.resolve(source.executable()), StandardCopyOption.ATOMIC_MOVE,
				StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(staged);
		}
	}

	private Path folder(final PinnedSource source, final String sha256) {
		return root.resolve(source.tool()).resolve(sha256);
	}
}
