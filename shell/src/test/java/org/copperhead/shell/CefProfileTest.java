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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.jqwik.api.Example;
import org.copperhead.toolchain.XdgDirs;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CefProfileTest {
	private static final Path HOME = Path.of("/home/someone");
	private static final Path HOME_PROFILE = Path.of("/home/someone/.cache/copperhead/cef");
	private static final String CACHE_HOME = "XDG_CACHE_HOME";

	private static CefProfile profile(final Map<String, String> environment) {
		return new CefProfile(XdgDirs.of(environment, HOME));
	}

	@Example
	public void shouldKeepTheProfileInTheCacheHomeNamed() {
		assertEquals(Path.of("/var/cache/someone/copperhead/cef"),
			profile(Map.of(CACHE_HOME, "/var/cache/someone")).directory());
	}

	@Example
	public void shouldKeepTheProfileInTheHomeFolderCacheWhenNoCacheHomeIsNamed() {
		assertEquals(HOME_PROFILE, profile(Map.of()).directory());
	}

	@Example
	public void shouldIgnoreAnEmptyBlankOrRelativeCacheHome() {
		assertAll(List.of("", "  ", "cache", "./cache").stream().map(cacheHome -> () ->
			assertEquals(HOME_PROFILE, profile(Map.of(CACHE_HOME, cacheHome)).directory(), cacheHome)));
	}

	@Example
	public void shouldBeHeldOnlyWhileAnEngineMarksItInUse() throws Exception {
		final Path cache = Files.createTempDirectory("cache");
		final CefProfile profile = profile(Map.of(CACHE_HOME, cache.toString()));

		try {
			Files.createDirectories(profile.directory());
			assertFalse(profile.held());

			Files.createSymbolicLink(profile.directory().resolve("SingletonLock"), Path.of("somehost-4242"));
			assertTrue(profile.held());
		} finally {
			try (Stream<Path> made = Files.walk(cache)) {
				made.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
			}
		}
	}
}
