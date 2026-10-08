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

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StartFailureTest {
	private static final Path HOME = Path.of("/home/someone");

	@Test
	public void shouldNameTheFolderTheEngineWasLoadedFrom() {
		final CefLocation location = new CefLocation("/opt/copperhead/lib/app/jcef", HOME);
		final String message = StartFailure.message(location,
			new UnsatisfiedLinkError("libcef.so: cannot open shared object file"));

		assertTrue(message.contains(location.directory().toString()), message);
	}

	@Test
	public void shouldSayWhatWentWrong() {
		final String message = StartFailure.message(new CefLocation(null, HOME),
			new UnsatisfiedLinkError("libcef.so: cannot open shared object file"));

		assertTrue(message.contains("libcef.so: cannot open shared object file"), message);
	}

	@Test
	public void shouldNameTheHomeFolderWhenNoEngineIsBundled() {
		final String message = StartFailure.message(new CefLocation(null, HOME), new IllegalStateException("x"));

		assertTrue(message.contains(HOME.resolve(".copperhead/jcef").toString()), message);
	}

	@Test
	public void shouldStillSpeakWhenTheFailureHasNoMessage() {
		final String message = StartFailure.message(new CefLocation(null, HOME), new IllegalStateException());

		assertTrue(message.contains("IllegalStateException"), message);
	}
}
