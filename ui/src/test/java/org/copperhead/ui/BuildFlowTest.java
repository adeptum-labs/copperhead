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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builds run with the stand-ins for vasm and Shrinkler while the page polls; what they say reaches the editor. */
public class BuildFlowTest {
	private static final String BUILD = "[data-key=\"ctrl+b\"]";
	private static final String BUILD_AND_RUN = "[data-key=\"ctrl+shift+b\"]";
	private static final String CANCEL = "#menus [hx-post=\"/action/build-cancel\"]";
	private static final String SETTINGS_SAVE = "#modal .modal__buttons .button--accent";
	private static final Duration CLOSING = Duration.ofSeconds(5);
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
		return running(BUILD);
	}

	private Screen running(final String key) throws Exception {
		return saved(Screen.open(rig), "start:\n\t" + StubTools.SLOW + "\n").trigger(key, Map.of());
	}

	private Screen cancelled(final Screen screen) throws Exception {
		return finished(screen.click(CANCEL));
	}

	private boolean emulatorRuns() {
		return rig.session().state().running();
	}

	private void stopEmulator() {
		rig.session().change(state -> state.withRunning(false));
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
		cancelled(screen);
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
		cancelled(screen);
	}

	@Example
	public void shouldWriteNothingForABuildItRefuses() throws Exception {
		final Screen screen = running();
		final Path source = rig.project().dir().resolve("src/main.s");
		final String kept = Files.readString(source);

		assertEquals(500, screen.client().submit("/action/build",
			"files=main.s&main.s=" + URLEncoder.encode("start:\n\tnop\n", StandardCharsets.UTF_8)).statusCode());
		assertEquals(kept, Files.readString(source));
		cancelled(screen);
	}

	@Example
	public void shouldStartTheRunOnceTheBuildHasSucceeded() throws Exception {
		stopEmulator();
		finished(Screen.open(rig).trigger(BUILD_AND_RUN, Map.of()));

		assertTrue(emulatorRuns());
	}

	@Example
	public void shouldNotStartTheRunOfABuildThatFailed() throws Exception {
		stopEmulator();
		finished(saved(Screen.open(rig), FAILED).trigger(BUILD_AND_RUN, Map.of()));

		assertFalse(emulatorRuns());
	}

	@Example
	public void shouldNotStartTheRunOfABuildThatIsCancelled() throws Exception {
		stopEmulator();
		final Screen screen = running(BUILD_AND_RUN);

		assertFalse(emulatorRuns());
		cancelled(screen);
		assertFalse(emulatorRuns());
	}

	@Example
	public void shouldRefuseToChangeTheSettingsWhileABuildRuns() throws Exception {
		final Screen screen = Screen.open(rig).click("#menus [hx-vals*=SETTINGS]");

		screen.trigger(BUILD, Map.of("files", "main.s", "main.s", "start:\n\t" + StubTools.SLOW + "\n"));
		screen.trigger(SETTINGS_SAVE, Map.of("name", "nova", "target", "OCS", "loadAddress", "$20000",
			"entry", "src/main.s"));

		assertEquals("A build is running; wait until it ends", screen.find("#modal .modal__line").text());
		assertTrue(Files.isRegularFile(rig.project().chp()));
		assertTrue(Files.notExists(rig.project().dir().resolve("nova.chp")));
		cancelled(screen);
	}

	@Example
	public void shouldLeaveNoToolRunningOnceTheStudioCloses() throws Exception {
		final String marker = "30." + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L) + "1";

		saved(Screen.open(rig), "start:\n\t" + StubTools.SLOW + " " + marker + "\n").trigger(BUILD, Map.of());
		awaitTool(marker);

		final Instant start = Instant.now();

		rig.close();

		assertTrue(Duration.between(start, Instant.now()).compareTo(CLOSING) < 0);
		assertEquals(List.of(), toolsRunning(marker));
		rig = WorkbenchRig.empty(now::get);
	}

	private static void awaitTool(final String marker) throws InterruptedException {
		for (int attempt = 0; attempt < 250 && toolsRunning(marker).isEmpty(); attempt++) {
			Thread.sleep(20);
		}
		assertFalse(toolsRunning(marker).isEmpty());
	}

	private static List<String> toolsRunning(final String marker) {
		return ProcessHandle.allProcesses().filter(ProcessHandle::isAlive)
			.map(handle -> handle.info().commandLine().orElse("")).filter(line -> line.contains(marker))
			.toList();
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
