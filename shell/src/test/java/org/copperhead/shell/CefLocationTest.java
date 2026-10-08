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

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CefLocationTest {
	private static final Path HOME = Path.of("/home/someone");

	@Test
	public void shouldKeepTheEngineInTheHomeFolderWhenNoneIsBundled() {
		final CefLocation location = new CefLocation(null, HOME);

		assertFalse(location.isBundled());
		assertEquals(HOME.resolve(".copperhead/jcef").toFile(), location.directory());
	}

	@Test
	public void shouldUseTheFolderTheLauncherNames() {
		final CefLocation location = new CefLocation("/opt/copperhead/lib/app/jcef", HOME);

		assertTrue(location.isBundled());
		assertEquals(new File("/opt/copperhead/lib/app/jcef"), location.directory());
	}

	@Test
	public void shouldIgnoreAnEmptyOrBlankProperty() {
		for (final String value : new String[] {"", "  "}) {
			final CefLocation location = new CefLocation(value, HOME);

			assertFalse(location.isBundled());
			assertEquals(HOME.resolve(".copperhead/jcef").toFile(), location.directory());
		}
	}

	@Test
	public void shouldTrustTheBundledFolderEvenWhenItIsMissing() {
		final CefLocation location = new CefLocation("/nowhere/jcef", HOME);

		assertTrue(location.isBundled());
		assertFalse(location.directory().exists());
	}

	@Test
	public void shouldPreferTheBundledEngineOverOneInTheHomeFolder() {
		final Path home = Path.of(System.getProperty("java.io.tmpdir"));
		final CefLocation location = new CefLocation("/opt/copperhead/lib/app/jcef", home);

		assertEquals(new File("/opt/copperhead/lib/app/jcef"), location.directory());
	}
}
