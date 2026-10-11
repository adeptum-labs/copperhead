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
import java.time.Duration;
import java.time.Instant;
import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FetcherTest extends ScratchTest {
	private static final Duration PATIENT = Duration.ofSeconds(10);
	private static final Duration IMPATIENT = Duration.ofMillis(200);
	private static final Duration STALL = Duration.ofSeconds(5);

	@Example
	public void shouldHashTheArchiveItFetched() throws Exception {
		final byte[] archive = new TarFixture().file("tool/Makefile", "all:\n").bytes();

		try (LocalServer server = new LocalServer().serve("/tool.tar.gz", archive)) {
			final Fetcher.Fetched fetched = fetch(server, "/tool.tar.gz");

			assertEquals(TarFixture.sha256(archive), fetched.sha256());
			assertArrayEquals(archive, Files.readAllBytes(fetched.file()));
			assertEquals(scratch(), fetched.file().getParent());
		}
	}

	@Example
	public void shouldTellAReplacedArchiveFromThePinnedOne() throws Exception {
		final String pinned = TarFixture.sha256(new TarFixture().file("tool/Makefile", "all:\n").bytes());
		final byte[] replaced = new TarFixture().file("tool/Makefile", "all:\n\ttrue\n").bytes();

		try (LocalServer server = new LocalServer().serve("/tool.tar.gz", replaced)) {
			final Fetcher.Fetched fetched = fetch(server, "/tool.tar.gz");

			assertNotEquals(pinned, fetched.sha256());
			assertEquals(TarFixture.sha256(replaced), fetched.sha256());
		}
	}

	@Example
	public void shouldKeepNothingOfAnArchiveThatIsNotThere() throws Exception {
		try (LocalServer server = new LocalServer()) {
			final IOException missing = assertThrows(IOException.class, () -> fetch(server, "/gone.tar.gz"));

			assertTrue(missing.getMessage().contains("404"), missing.getMessage());
			assertEquals(0, listed(scratch()).size());
		}
	}

	@Example
	public void shouldGiveUpOnAServerThatTakesTooLong() throws Exception {
		try (LocalServer server = new LocalServer().stall("/slow.tar.gz", STALL)) {
			final Instant start = Instant.now();

			assertThrows(IOException.class,
				() -> new Fetcher(server.client(), IMPATIENT).fetch(server.uri("/slow.tar.gz"), scratch()));
			assertTrue(Duration.between(start, Instant.now()).compareTo(STALL) < 0);
			assertEquals(0, listed(scratch()).size());
		}
	}

	@Example
	public void shouldRefuseAnArchiveThatClaimsToBeLargerThanTheLimit() throws Exception {
		try (LocalServer server = new LocalServer().claim("/huge.tar.gz", Fetcher.LIMIT + 1, STALL)) {
			final Instant start = Instant.now();
			final IOException refused = assertThrows(IOException.class, () -> fetch(server, "/huge.tar.gz"));

			assertTrue(Duration.between(start, Instant.now()).compareTo(STALL) < 0);
			assertTrue(refused.getMessage().endsWith("the download is larger than the 64 MB Copperhead accepts"),
				refused.getMessage());
			assertEquals(0, listed(scratch()).size());
		}
	}

	@Example
	public void shouldCutOffAnArchiveThatGrowsPastTheLimitAndKeepNoneOfIt() throws Exception {
		try (LocalServer server = new LocalServer().endless("/endless.tar.gz")) {
			final IOException refused = assertThrows(IOException.class, () -> fetch(server, "/endless.tar.gz"));

			assertTrue(refused.getMessage().endsWith("the download is larger than the 64 MB Copperhead accepts"),
				refused.getMessage());
			assertEquals(0, listed(scratch()).size());
		}
	}

	private Fetcher.Fetched fetch(final LocalServer server, final String path) throws Exception {
		return new Fetcher(server.client(), PATIENT).fetch(server.uri(path), scratch());
	}
}
