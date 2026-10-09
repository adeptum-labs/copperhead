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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

/** A .tar.gz made in memory, entry by entry, standing in for a tool's source archive. */
final class TarFixture {
	private static final int PLAIN = 0644;
	private static final int RUNNABLE = 0755;

	private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
	private final TarArchiveOutputStream tar;

	TarFixture() throws IOException {
		tar = new TarArchiveOutputStream(new GzipCompressorOutputStream(bytes));
		tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
	}

	TarFixture directory(final String name) throws IOException {
		return add(new TarArchiveEntry(name + "/"), "");
	}

	TarFixture file(final String name, final String text) throws IOException {
		return add(withMode(new TarArchiveEntry(name), PLAIN), text);
	}

	TarFixture executable(final String name, final String text) throws IOException {
		return add(withMode(new TarArchiveEntry(name), RUNNABLE), text);
	}

	TarFixture absolute(final String name, final String text) throws IOException {
		return add(withMode(new TarArchiveEntry(name, true), PLAIN), text);
	}

	TarFixture link(final String name, final String target) throws IOException {
		final TarArchiveEntry entry = new TarArchiveEntry(name, TarConstants.LF_SYMLINK);

		entry.setLinkName(target);
		return add(entry, "");
	}

	byte[] bytes() throws IOException {
		tar.close();
		return bytes.toByteArray();
	}

	Path write(final Path file) throws IOException {
		return Files.write(file, bytes());
	}

	static String sha256(final byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static TarArchiveEntry withMode(final TarArchiveEntry entry, final int mode) {
		entry.setMode(mode);
		return entry;
	}

	private TarFixture add(final TarArchiveEntry entry, final String text) throws IOException {
		final byte[] content = text.getBytes(StandardCharsets.UTF_8);

		entry.setSize(content.length);
		tar.putArchiveEntry(entry);
		tar.write(content);
		tar.closeArchiveEntry();
		return this;
	}
}
