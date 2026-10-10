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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/**
 * Unpacks a fetched .tar.gz. The archive may be one nobody has vetted yet (a replaced vasm release is unpacked to
 * read its version before the user decides), so nothing in it may land outside the folder it is unpacked into.
 * Only plain files and folders are unpacked: the tools' sources need nothing more, and every other kind of entry
 * (links, devices, pipes, sparse files) reaches or fills something the archive has no business with.
 */
public final class Tarball {
	private static final int MAX_ENTRIES = 20_000;
	private static final long MAX_BYTES = 200L * 1024 * 1024;
	/* The tar reader keeps an entry's header records in memory, so what it may inflate between two entries is capped
	   far below the size cap: the headers of a real archive are nowhere near it. */
	private static final long HEADER_ALLOWANCE = 1024 * 1024;
	private static final int OWNER_EXECUTE = 0100;
	private static final String PARENT = "..";
	private static final String MALFORMED = "archive is malformed";
	private static final String TOO_MUCH = "archive holds too many entries or too many bytes";

	private Tarball() {
	}

	public static Path extract(final Path archive, final Path into) throws IOException {
		return extract(archive, into, MAX_ENTRIES, MAX_BYTES);
	}

	static Path extract(final Path archive, final Path into, final int maxEntries, final long maxBytes)
		throws IOException {

		final Path root = Files.createDirectories(into).toRealPath();
		final Set<Path> tops = new TreeSet<>();
		final Allowance inflated = new Allowance(new GzipCompressorInputStream(
			new BufferedInputStream(Files.newInputStream(archive))));
		int entries = 0;
		long bytes = 0;

		try (TarArchiveInputStream tar = new TarArchiveInputStream(inflated)) {
			for (TarArchiveEntry entry = next(tar, inflated); entry != null; entry = next(tar, inflated)) {
				if (++entries > maxEntries) {
					throw new IOException(TOO_MUCH);
				}
				final Path target = place(root, entry.getName());

				if (holds(entry, true)) {
					Files.createDirectories(target);
				} else if (holds(entry, false)) {
					bytes += write(tar, entry, target, maxBytes - bytes, inflated);
				} else {
					throw unsafe(entry.getName());
				}
				tops.add(root.relativize(target).getName(0));
			}
		}
		if (tops.size() == 1) {
			final Path top = root.resolve(tops.iterator().next());

			if (Files.isDirectory(top, LinkOption.NOFOLLOW_LINKS)) {
				return top;
			}
		}
		throw new IOException("archive does not hold a single top directory");
	}

	private static TarArchiveEntry next(final TarArchiveInputStream tar, final Allowance inflated) throws IOException {
		inflated.allow(HEADER_ALLOWANCE);
		try {
			return tar.getNextEntry();
		} catch (final RuntimeException | StackOverflowError e) {
			throw new IOException(MALFORMED, e);
		}
	}

	/* A sparse entry keeps an ordinary type flag when its map is carried in PAX records, so the flag alone is no
	   proof of a plain file. */
	private static boolean holds(final TarArchiveEntry entry, final boolean folder) {
		final byte type = entry.getLinkFlag();

		return !entry.isSparse() && !entry.isPaxGNU1XSparse() && entry.isDirectory() == folder
			&& (type == TarConstants.LF_NORMAL || type == TarConstants.LF_OLDNORM
			|| folder && type == TarConstants.LF_DIR);
	}

	/* What is really copied is counted, not what the header claims: the size cap must hold whatever the header says. */
	private static long write(final TarArchiveInputStream tar, final TarArchiveEntry entry, final Path target,
		final long room, final Allowance inflated) throws IOException {

		final byte[] buffer = new byte[8192];
		long written = 0;

		if (entry.getSize() > room) {
			throw new IOException(TOO_MUCH);
		}
		inflated.allow(entry.getSize() + TarConstants.DEFAULT_RCDSIZE);
		Files.deleteIfExists(target);
		try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
			for (int read = tar.read(buffer); read > 0; read = tar.read(buffer)) {
				written += read;
				if (written > room) {
					throw new IOException(TOO_MUCH);
				}
				out.write(buffer, 0, read);
			}
		}
		if ((entry.getMode() & OWNER_EXECUTE) != 0) {
			Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));
		}
		return written;
	}

	/* Every folder on the way to an entry is made here and must be a real folder inside the root. */
	private static Path place(final Path root, final String name) throws IOException {
		final Path relative = relative(name);

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

	private static Path relative(final String name) throws IOException {
		try {
			return Path.of(name);
		} catch (final InvalidPathException e) {
			throw unsafe(name);
		}
	}

	private static IOException unsafe(final String name) {
		return new IOException("unsafe entry " + name);
	}

	/* Fails once more than the allowed bytes come out of the archive. It asks for one byte past the allowance so that
	   an overrun is seen instead of passing unnoticed at exactly the limit. */
	private static final class Allowance extends FilterInputStream {
		private long left;

		private Allowance(final InputStream in) {
			super(in);
		}

		private void allow(final long bytes) {
			left = bytes;
		}

		@Override
		public int read() throws IOException {
			final int read = in.read();

			spend(read < 0 ? 0 : 1);
			return read;
		}

		@Override
		public int read(final byte[] buffer, final int offset, final int length) throws IOException {
			return (int) spend(in.read(buffer, offset, (int) Math.min(length, left + 1)));
		}

		@Override
		public long skip(final long count) throws IOException {
			return spend(in.skip(Math.min(count, left + 1)));
		}

		private long spend(final long count) throws IOException {
			if (count > left) {
				throw new IOException(MALFORMED);
			}
			left -= Math.max(count, 0);
			return count;
		}
	}
}
