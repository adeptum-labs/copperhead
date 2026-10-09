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

package org.copperhead.core.build;

import java.util.Locale;

/**
 * A double-density floppy written without a filesystem: the bootblock, then the program from the third sector on,
 * which the bootblock reads back through the trackdisk device in whole sectors.
 */
public final class TrackDisk {
	public static final int SECTOR = 512;
	public static final int SIZE = 901_120;
	public static final int PAYLOAD_OFFSET = Bootblock.SIZE;
	public static final int CAPACITY = SIZE - PAYLOAD_OFFSET;

	private TrackDisk() {
	}

	/** The bytes the trackdisk device reads for a payload, which come only in whole sectors. */
	public static int padded(final int length) {
		return Math.ceilDiv(length, SECTOR) * SECTOR;
	}

	public static byte[] image(final byte[] sealedBootblock, final byte[] payload) {
		if (!Bootblock.valid(sealedBootblock)) {
			throw new IllegalArgumentException("bootblock is not sealed");
		}

		if (payload.length > CAPACITY) {
			throw new IllegalArgumentException(String.format(Locale.ROOT,
				"payload %,d bytes exceeds disk capacity %,d", payload.length, CAPACITY));
		}

		final byte[] disk = new byte[SIZE];

		System.arraycopy(sealedBootblock, 0, disk, 0, Bootblock.SIZE);
		System.arraycopy(payload, 0, disk, PAYLOAD_OFFSET, payload.length);
		return disk;
	}
}
