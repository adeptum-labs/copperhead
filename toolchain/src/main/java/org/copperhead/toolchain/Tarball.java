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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/**
 * Unpacks a fetched .tar.gz. The archive may be one nobody has vetted yet (a replaced vasm release is unpacked to
 * read its version before the user decides), so nothing in it may land outside the folder it is unpacked into.
 */
public final class Tarball {
	private static final int MAX_ENTRIES = 20_000;
	private static final long MAX_BYTES = 200L * 1024 * 1024;
	private static final int OWNER_EXECUTE = 0100;
	private static final String PARENT = "..";

	private Tarball() {
	}

	public static Path extract(final Path archive, final Path into) throws IOException {
		final Path root = Files.createDirectories(into).toRealPath();
		final Set<Path> tops = new TreeSet<>();
		int entries = 0;
		long bytes = 0;

		try (TarArchiveInputStream tar = new TarArchiveInputStream(new GzipCompressorInputStream(
			new BufferedInputStream(Files.newInputStream(archive))))) {

			for (TarArchiveEntry entry = tar.getNextEntry(); entry != null; entry = tar.getNextEntry()) {
				bytes += entry.getSize();
				if (++entries > MAX_ENTRIES || bytes > MAX_BYTES) {
					throw new IOException("archive holds more than " + MAX_ENTRIES + " entries or "
						+ MAX_BYTES + " bytes");
				}
				final Path target = place(root, entry.getName());

				unpack(tar, entry, root, target);
				tops.add(root.relativize(target).getName(0));
			}
		}
		if (tops.size() != 1 || !Files.isDirectory(root.resolve(tops.iterator().next()))) {
			throw new IOException("archive does not hold a single top directory");
		}
		return root.resolve(tops.iterator().next());
	}

	private static void unpack(final TarArchiveInputStream tar, final TarArchiveEntry entry, final Path root,
		final Path target) throws IOException {

		if (entry.isDirectory()) {
			Files.createDirectories(target);
		} else if (entry.isSymbolicLink()) {
			final Path link = Path.of(entry.getLinkName());

			if (link.isAbsolute() || !target.getParent().resolve(link).normalize().startsWith(root)) {
				throw unsafe(entry.getName());
			}
			Files.createSymbolicLink(target, link);
		} else if (entry.isLink()) {
			Files.createLink(target, place(root, entry.getLinkName()));
		} else if (entry.isFile()) {
			Files.copy(tar, target, StandardCopyOption.REPLACE_EXISTING);
			if ((entry.getMode() & OWNER_EXECUTE) != 0) {
				Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));
			}
		} else {
			throw unsafe(entry.getName());
		}
	}

	/* Every folder on the way to an entry is made here and must be a real folder inside the root: a link unpacked
	   earlier could otherwise lead a later entry anywhere. */
	private static Path place(final Path root, final String name) throws IOException {
		final Path relative = Path.of(name);

		for (final Path part : relative) {
			if (PARENT.equals(part.toString())) {
				throw unsafe(name);
			}
		}
		final Path target = root.resolve(relative).normalize();

		if (relative.isAbsolute() || !target.startsWith(root) || target.equals(root)) {
			throw unsafe(name);
		}
		for (Path folder = target.getParent(); !folder.equals(root); folder = folder.getParent()) {
			if (Files.isSymbolicLink(folder)) {
				throw unsafe(name);
			}
		}
		Files.createDirectories(target.getParent());
		return target;
	}

	private static IOException unsafe(final String name) {
		return new IOException("unsafe entry " + name);
	}
}
