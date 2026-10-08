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

package org.copperhead.shell;

import java.time.Duration;

/** Ends the process once a shutdown has had its time, for the times it never finishes on its own. */
final class ShutdownDeadline {
	private ShutdownDeadline() {
	}

	/* A daemon, so a shutdown that does finish in time is never held up by the wait. */
	static void start(final Duration grace, final Runnable end) {
		Thread.ofPlatform().name("shutdown-deadline").daemon().start(() -> {
			try {
				Thread.sleep(grace);
				end.run();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
	}
}
