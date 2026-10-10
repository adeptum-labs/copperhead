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
		return link(name, target, TarConstants.LF_SYMLINK);
	}

	private TarFixture link(final String name, final String target, final byte type) throws IOException {
		final TarArchiveEntry entry = new TarArchiveEntry(name, type);

		entry.setLinkName(target);
		return add(entry, "");
	}

	TarFixture mode(final String name, final String text, final int mode) throws IOException {
		return add(withMode(new TarArchiveEntry(name), mode), text);
	}

	TarFixture hardLink(final String name, final String target) throws IOException {
		return link(name, target, TarConstants.LF_LINK);
	}

	TarFixture special(final String name, final byte type) throws IOException {
		return add(new TarArchiveEntry(name, type), "");
	}

	/** An extended header of the PAX kind holding the records {@code key=value} given as "key=value" strings. */
	TarFixture pax(final String... records) throws IOException {
		final StringBuilder body = new StringBuilder();

		for (final String record : records) {
			final String rest = " " + record + "\n";
			int length = rest.length();

			while (String.valueOf(length).length() + rest.length() != length) {
				length = String.valueOf(length).length() + rest.length();
			}
			body.append(length).append(rest);
		}
		return add(new TarArchiveEntry("././@PaxHeader", TarConstants.LF_PAX_EXTENDED_HEADER_LC), body.toString());
	}

	/** A GNU long-name header of the given size, filled with a letter, followed by nothing it could name. */
	TarFixture longNameOfSize(final int size) throws IOException {
		return add(new TarArchiveEntry("././@LongLink", TarConstants.LF_GNUTYPE_LONGNAME), "a".repeat(size));
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
