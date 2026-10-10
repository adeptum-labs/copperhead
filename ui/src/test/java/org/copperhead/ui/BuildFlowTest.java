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

import jakarta.json.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.copperhead.core.build.TrackDisk;
import org.copperhead.ui.view.Region;
import org.copperhead.ui.view.Tool;
import static org.copperhead.ui.Flows.POLL;
import static org.copperhead.ui.Flows.build;
import static org.copperhead.ui.Flows.finished;
import static org.copperhead.ui.Flows.rail;
import static org.copperhead.ui.Flows.saved;
import static org.copperhead.ui.Flows.source;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builds run with the stand-ins for vasm and Shrinkler while the page polls; what they say reaches the editor. */
public class BuildFlowTest {
	private static final String BUILD = "[data-key=\"ctrl+b\"]";
	private static final String FAILED = "start:\n\tnop\n\t" + StubTools.ERROR + "\n";

	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
	private WorkbenchRig rig;

	@BeforeTry
	public void serve() throws Exception {
		rig = new WorkbenchRig(now::get);
	}

	@AfterTry
	public void stop() throws Exception {
		rig.close();
	}

	private Path adf() {
		return rig.project().adf();
	}

	/* The stand-in for vasm takes its time over a source that asks it to, long enough to see the build running. */
	private Screen running() throws Exception {
		return saved(Screen.open(rig), "start:\n\t" + StubTools.SLOW + "\n").trigger(BUILD, Map.of());
	}

	@Example
	public void shouldWriteABootableDiskOfTheProject() throws Exception {
		final Screen screen = build(Screen.open(rig));

		assertEquals(TrackDisk.SIZE, Files.size(adf()));
		assertEquals("Built demo.adf · 7 bytes", screen.find("#status .status__note").text());
		assertEquals("0 errors", screen.find("#status .status__ok").text());
		assertTrue(screen.document().select(POLL).isEmpty());
	}

	@Example
	public void shouldPollAndHoldTheBuildControlsBackWhileItRuns() throws Exception {
		final Screen screen = running();

		assertEquals("every 500ms", screen.find(POLL).attr("hx-trigger"));
		assertTrue(screen.find("#machine [hx-post=\"/action/build\"]").hasAttr("disabled"));
		assertTrue(screen.find(BUILD).hasAttr("disabled"));
		assertEquals("last build building", screen.find("#status .status__build").text());
		screen.click("#menus [hx-post=\"/action/build-cancel\"]");
	}

	@Example
	public void shouldStopABuildThatIsCancelled() throws Exception {
		final Screen screen = finished(running().click(rail(Tool.BUILD))
			.click("#tool [hx-post=\"/action/build-cancel\"]"));

		assertEquals("build failed · 1 error", screen.find("#status .status__error").text());
		assertTrue(screen.document().select("#tool .build__line--err").eachText().contains("build cancelled"));
		assertTrue(Files.notExists(adf()));
	}

	@Example
	public void shouldRunOneBuildAtATime() throws Exception {
		final Screen screen = running();

		assertEquals(500, screen.client().submit("/action/build", "").statusCode());
		screen.click("#menus [hx-post=\"/action/build-cancel\"]");
	}

	@Example
	public void shouldSaveWhatItIsSentBeforeBuilding() throws Exception {
		final Screen screen = finished(Screen.open(rig).trigger(BUILD,
			Map.of("files", "main.s", "main.s", "start:\n\t" + StubTools.WARNING + "\n")));

		assertEquals("start:\n\t" + StubTools.WARNING + "\n",
			Files.readString(rig.project().dir().resolve("src/main.s")));
		assertEquals("1 warning · main.s:2", screen.find("#status .status__warning").text());
	}

	@Example
	public void shouldMarkTheErrorInTheEditorAndKeepTheLastGoodDisk() throws Exception {
		build(Screen.open(rig));

		final byte[] good = Files.readAllBytes(adf());
		final Screen screen = build(saved(Screen.open(rig), FAILED));

		assertEquals("build failed · 1 error", screen.find("#status .status__error").text());
		assertEquals("Build failed · 1 error", screen.find("#status .status__note").text());
		assertEquals(Json.createArrayBuilder().add(Json.createObjectBuilder().add("file", "main.s").add("line", 3)
			.add("severity", "error").add("message", "unknown mnemonic <BOGUS>")).build(),
			source(screen).getJsonArray("diagnostics"));
		assertArrayEquals(good, Files.readAllBytes(adf()));
	}

	@Example
	public void shouldJumpToTheErrorFromTheStatusBar() throws Exception {
		final Screen screen = build(saved(Screen.open(rig), FAILED)).click(rail(Tool.SPRITES))
			.click("#status .status__error");

		assertEquals("main.s", screen.find("#tool .tool__tab--active").text());
		assertEquals(3, source(screen).getInt("cursor"));
	}

	@Example
	public void shouldShowTheLogInTheBuildTool() throws Exception {
		final List<String> log = build(Screen.open(rig).click(rail(Tool.BUILD))).document()
			.select("#tool .build__line").eachText();

		assertTrue(log.contains("crunched 7 bytes"), log.toString());
		assertTrue(log.getLast().startsWith("adf demo.adf"), log.toString());
	}

	@Example
	public void shouldKeepOneOfEachRegionThroughABuild() throws Exception {
		final Screen screen = build(Screen.open(rig).click(rail(Tool.BUILD)));

		assertAll(Arrays.stream(Region.values()).map(region -> () ->
			assertEquals(1, screen.document().select("#" + region.id()).size(), region.id())));
		assertEquals(Region.values().length, screen.document().select("[hx-swap-oob]").size());
	}

	@Example
	public void shouldBuildNothingWithNoProjectOpen() throws Exception {
		rig.close();
		rig = WorkbenchRig.empty(now::get);

		final Screen screen = Screen.open(rig);

		assertTrue(screen.find("#machine [hx-post=\"/action/build\"]").hasAttr("disabled"));
		assertEquals(500, screen.client().submit("/action/build", "").statusCode());
	}
}
