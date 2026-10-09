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
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BootblockTest {
	private static final int ROOT_BLOCK = 880;
	private static final int CODE = 12;

	private static byte[] assembled(final byte[] code) {
		return ByteBuffer.allocate(CODE + code.length).put(new byte[] {'D', 'O', 'S', 0}).putInt(0)
			.putInt(ROOT_BLOCK).put(code).array();
	}

	private static int checksum(final byte[] block) {
		return ByteBuffer.wrap(block).getInt(4);
	}

	@Property
	public void shouldSealAnyCodeThatFitsIntoABlockKickstartBoots(@ForAll @Size(max = 1012) final byte[] code) {
		final byte[] block = Bootblock.seal(assembled(code));

		assertEquals(Bootblock.SIZE, block.length);
		assertTrue(Bootblock.valid(block));
		assertArrayEquals(code, Arrays.copyOfRange(block, CODE, CODE + code.length));
	}

	@Property
	public void shouldNoticeAnyBitThatChangedAfterSealing(@ForAll @Size(max = 1012) final byte[] code,
		@ForAll @IntRange(max = 1023) final int at, @ForAll @IntRange(max = 7) final int bit) {

		final byte[] block = Bootblock.seal(assembled(code));

		block[at] ^= (byte) (1 << bit);
		assertFalse(Bootblock.valid(block));
	}

	@Example
	public void shouldInvertTheSumOfTheBlock() {
		assertEquals(0xBBB0A98F, checksum(Bootblock.seal(assembled(new byte[0]))));
	}

	@Example
	public void shouldWrapTheCarryBackIntoTheSum() {
		assertEquals(0xFBB0A98E, checksum(Bootblock.seal(assembled(new byte[] {(byte) 0xC0, 0, 0, 0}))));
	}

	@Example
	public void shouldIgnoreWhateverTheChecksumFieldHeldBefore() {
		final byte[] dirty = assembled(new byte[0]);

		dirty[4] = 0x7F;
		assertEquals(0xBBB0A98F, checksum(Bootblock.seal(dirty)));
	}

	@Example
	public void shouldRefuseCodeTooLargeForTheBlock() {
		assertEquals("bootblock too large: 1025 bytes, at most 1024", assertThrows(IllegalArgumentException.class,
			() -> Bootblock.seal(assembled(new byte[1013]))).getMessage());
	}

	@Example
	public void shouldRefuseABlockThatIsNoDosDisk() {
		assertThrows(IllegalArgumentException.class, () -> Bootblock.seal(new byte[] {'K', 'I', 'C', 'K'}));
		assertThrows(IllegalArgumentException.class, () -> Bootblock.seal(new byte[] {'D', 'O'}));
		assertFalse(Bootblock.valid(new byte[Bootblock.SIZE]));
		assertFalse(Bootblock.valid(new byte[] {'D', 'O', 'S', 0}));
	}
}
