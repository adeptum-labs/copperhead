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

package org.copperhead.ui.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.jqwik.api.Example;
import org.copperhead.ui.TempDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkspacesTest {
	@Example
	public void shouldGiveTheNameOfTheFailureWhenItHasNoMessage() {
		assertEquals("refused", Workspaces.reason(new IOException("refused")));
		assertEquals("IOException", Workspaces.reason(new IOException()));
	}

	@Example
	public void shouldFollowLinksWhenKeepingAPathInsideTheHome() throws Exception {
		try (TempDirectory home = new TempDirectory(); TempDirectory outside = new TempDirectory()) {
			final Path link = Files.createSymbolicLink(home.path().resolve("escape"), outside.path());

			assertTrue(Workspaces.confined(home.path(), home.path().resolve("projects/new")));
			assertFalse(Workspaces.confined(home.path(), link));
			assertFalse(Workspaces.confined(home.path(), link.resolve("new")));
			assertFalse(Workspaces.confined(home.path(), Files.createSymbolicLink(home.path().resolve("gone"),
				home.path().resolve("nowhere"))));
		}
	}
}
