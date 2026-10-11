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

package org.copperhead.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.jqwik.api.Example;
import org.copperhead.core.build.Bootblock;
import org.copperhead.core.build.BuildReport;
import org.copperhead.core.build.TrackDisk;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.Target;
import org.copperhead.toolchain.PinnedSource;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.toolchain.SearchPath;
import org.copperhead.toolchain.XdgDirs;
import org.copperhead.ui.session.BuildRun.Phase;
import org.copperhead.ui.session.BuildService;
import org.copperhead.ui.session.Preferences;
import org.copperhead.ui.session.ProjectSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A build started the way the studio starts one, through its service with the toolchain it makes for itself, which
 * fetches and compiles the real vasm and Shrinkler into a home of the test's own. It needs the network, make, cc and
 * c++, so only the toolchain profile runs it.
 */
public class LiveBuildIT {
	private static final Duration LONGEST = Duration.ofMinutes(10);
	private static final Duration STEP = Duration.ofMinutes(2);
	private static final long BETWEEN_LOOKS = 200;

	@Example
	public void shouldBuildABootableDiskThroughTheStudioWithTheRealTools() throws Exception {
		try (TempDirectory home = new TempDirectory()) {
			final XdgDirs dirs = XdgDirs.of(Map.of(), home.path());
			final Preferences preferences = new Preferences(dirs.config().resolve("preferences.json"));
			final ProjectDirectory project = ProjectDirectory.create(home.path().resolve("projects"), "nightrun",
				ProjectKind.DEMO, Target.A500_OCS);

			preferences.used(project.chp());

			final ProjectSession session = new ProjectSession(preferences);
			final BuildService builds = new BuildService(session, preferences,
				BuildService.toolchain(dirs, buildToolsOnly(), Path.of("/etc/os-release")), STEP,
				command -> Thread.ofVirtual().start(command));

			try {
				builds.start(false);
				awaitTheEnd(session);
			} finally {
				builds.shutDown();
			}

			final BuildReport report = session.state().build().orElseThrow();
			final byte[] image = Files.readAllBytes(project.adf());

			assertTrue(report.ok(), report.log().toString());
			assertTrue(report.bytes() > 0, report.log().toString());
			assertEquals(TrackDisk.SIZE, image.length);
			assertTrue(Bootblock.valid(Arrays.copyOf(image, Bootblock.SIZE)));
		}
	}

	/* The tools are always fetched, even where vasm or Shrinkler is installed, as on a first launch. */
	private static Function<String, Optional<Path>> buildToolsOnly() {
		final Function<String, Optional<Path>> path = SearchPath.system();

		return name -> name.equals(PinnedSource.VASM.executable())
			|| name.equals(PinnedSource.SHRINKLER.executable()) ? Optional.empty() : path.apply(name);
	}

	/* A source other than the pinned one would wait on a question nobody answers here. */
	private static void awaitTheEnd(final ProjectSession session) throws InterruptedException {
		final Instant until = Instant.now().plus(LONGEST);

		while (session.state().run().running()) {
			assertNotEquals(Phase.WAITING, session.state().run().phase(),
				session.state().run().log().toString());
			assertTrue(Instant.now().isBefore(until), session.state().run().log().toString());
			Thread.sleep(BETWEEN_LOOKS);
		}
	}
}
