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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.IntStream;

/** The first two sectors of a disk, which Kickstart loads and runs when their checksum holds. */
public final class Bootblock {
	public static final int SIZE = 1024;
	private static final byte[] DOS = {'D', 'O', 'S', 0};
	private static final int CHECKSUM = 4;
	private static final long LONG_MASK = 0xFFFF_FFFFL;

	private Bootblock() {
	}

	/** Pads an assembled bootblock to its full size and writes the checksum Kickstart looks for. */
	public static byte[] seal(final byte[] assembled) {
		if (!dos(assembled)) {
			throw new IllegalArgumentException("bootblock does not start with DOS\\0");
		}

		if (assembled.length > SIZE) {
			throw new IllegalArgumentException("bootblock too large: " + assembled.length + " bytes, at most "
				+ SIZE);
		}

		final byte[] block = Arrays.copyOf(assembled, SIZE);
		final ByteBuffer longs = ByteBuffer.wrap(block).putInt(CHECKSUM, 0);

		longs.putInt(CHECKSUM, (int) ~sum(block));
		return block;
	}

	public static boolean valid(final byte[] block) {
		return block.length == SIZE && dos(block) && sum(block) == LONG_MASK;
	}

	private static boolean dos(final byte[] block) {
		return block.length >= DOS.length && Arrays.equals(block, 0, DOS.length, DOS, 0, DOS.length);
	}

	private static long sum(final byte[] block) {
		final ByteBuffer longs = ByteBuffer.wrap(block);

		return IntStream.range(0, SIZE / Integer.BYTES)
			.mapToLong(index -> Integer.toUnsignedLong(longs.getInt(index * Integer.BYTES)))
			.reduce(0, Bootblock::addWithCarry);
	}

	/* Kickstart wraps the carry out of each addition back into the sum, so a sealed block adds up to -1. */
	private static long addWithCarry(final long total, final long next) {
		final long sum = total + next;

		return (sum & LONG_MASK) + (sum >>> Integer.SIZE);
	}
}
