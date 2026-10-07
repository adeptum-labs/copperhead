/*
    Copperhead
    Copyright © 2026 Adam Waldenberg

    This program is free software: you can redistribute it and/or modify it under the terms of the
    GNU General Public License as published by the Free Software Foundation, version 3 of the License.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
    even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along with this program.
    If not, see <http://www.gnu.org/licenses/>.
 */

package org.copperhead.shell;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShutdownDeadlineTest {
	private static final long WAIT_SECONDS = 5;

	@Test
	public void shouldEndTheProcessOnceTheGraceHasPassed() throws Exception {
		final CompletableFuture<Boolean> ended = new CompletableFuture<>();

		ShutdownDeadline.start(Duration.ofMillis(50), () -> ended.complete(Thread.currentThread().isDaemon()));
		assertTrue(ended.get(WAIT_SECONDS, TimeUnit.SECONDS), "The deadline would keep the process alive");
	}

	@Test
	public void shouldWaitOutTheGraceFirst() throws Exception {
		final CompletableFuture<Boolean> ended = new CompletableFuture<>();

		ShutdownDeadline.start(Duration.ofSeconds(WAIT_SECONDS), () -> ended.complete(true));
		Thread.sleep(100);
		assertFalse(ended.isDone());
	}
}
