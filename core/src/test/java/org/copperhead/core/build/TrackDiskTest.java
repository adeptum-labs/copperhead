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

import java.util.Arrays;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrackDiskTest {
	private static final byte[] BOOTBLOCK = Bootblock.seal(new byte[] {'D', 'O', 'S', 0, 0, 0, 0, 0, 0, 0, 3, 0x70});

	private static byte[] payload(final int length) {
		final byte[] payload = new byte[length];

		Arrays.fill(payload, (byte) 0xA5);
		return payload;
	}

	@Example
	public void shouldHoldWhatAnEightyTrackDiskHoldsAfterItsBootblock() {
		assertEquals(901_120, TrackDisk.SIZE);
		assertEquals(900_096, TrackDisk.CAPACITY);
	}

	@Property(tries = 200)
	public void shouldPutTheBootblockFirstAndThePayloadRightAfterIt(
		@ForAll @IntRange(max = TrackDisk.CAPACITY) final int length) {

		final byte[] disk = TrackDisk.image(BOOTBLOCK, payload(length));

		assertEquals(TrackDisk.SIZE, disk.length);
		assertArrayEquals(BOOTBLOCK, Arrays.copyOf(disk, Bootblock.SIZE));
		assertArrayEquals(payload(length), Arrays.copyOfRange(disk, TrackDisk.PAYLOAD_OFFSET,
			TrackDisk.PAYLOAD_OFFSET + length));
		assertArrayEquals(new byte[TrackDisk.CAPACITY - length], Arrays.copyOfRange(disk,
			TrackDisk.PAYLOAD_OFFSET + length, TrackDisk.SIZE));
	}

	@Example
	public void shouldRefuseAPayloadOneByteLargerThanTheDisk() {
		assertEquals("payload 900,097 bytes exceeds disk capacity 900,096",
			assertThrows(IllegalArgumentException.class,
				() -> TrackDisk.image(BOOTBLOCK, payload(TrackDisk.CAPACITY + 1))).getMessage());
	}

	@Example
	public void shouldRefuseABootblockKickstartWouldNotRun() {
		assertThrows(IllegalArgumentException.class, () -> TrackDisk.image(new byte[Bootblock.SIZE], payload(1)));
	}

	@Property
	public void shouldPadToTheNextWholeSector(@ForAll @IntRange(max = TrackDisk.CAPACITY) final int length) {
		final int padded = TrackDisk.padded(length);

		assertEquals(0, padded % TrackDisk.SECTOR);
		assertTrue(padded >= length && padded < length + TrackDisk.SECTOR, () -> length + " -> " + padded);
	}

	@Example
	public void shouldLeaveAWholeSectorAsItIs() {
		assertEquals(0, TrackDisk.padded(0));
		assertEquals(512, TrackDisk.padded(1));
		assertEquals(512, TrackDisk.padded(512));
		assertEquals(1024, TrackDisk.padded(513));
	}
}
