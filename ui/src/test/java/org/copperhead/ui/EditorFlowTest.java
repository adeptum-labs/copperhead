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

import jakarta.json.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.copperhead.core.debug.Breakpoint;
import org.copperhead.core.project.ProjectFile;
import org.copperhead.core.project.Target;
import org.copperhead.ui.session.StudioState;
import org.copperhead.web.action.ActionFailed;
import org.copperhead.web.action.Form;
import static org.copperhead.ui.Flows.GUTTER;
import static org.copperhead.ui.Flows.finished;
import static org.copperhead.ui.Flows.lines;
import static org.copperhead.ui.Flows.noted;
import static org.copperhead.ui.Flows.owned;
import static org.copperhead.ui.Flows.saved;
import static org.copperhead.ui.Flows.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The editor's text lives in the page; saving it, its breakpoints and the file on disk are the studio's. */
public class EditorFlowTest {
	private static final String SAVE = "[data-key=\"ctrl+s\"]";
	private static final String SAVE_ALL = "[data-key=\"ctrl+shift+s\"]";
	private static final String CHECK = "#tool [data-editor-control=check]";
	private static final String CURSOR = "#tool [data-editor-control=cursor]";
	private static final Duration NOTE_WAIT = Duration.ofSeconds(20);
	private static final long NOTE_POLL_MILLIS = 10;

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

	private Path file(final String name) {
		return rig.project().dir().resolve("src").resolve(name);
	}

	private List<Breakpoint> kept() throws Exception {
		return ProjectFile.parse(Files.readString(rig.project().chp())).breakpoints();
	}

	/* What the studio answers when the disk refuses the write: the failure itself, with the studio as it was. */
	private Throwable refused(final String action, final String body) throws Exception {
		final Path src = rig.project().dir().resolve("src");
		final StudioState before = rig.session().state();

		Files.setPosixFilePermissions(src, PosixFilePermissions.fromString("r-xr-xr-x"));

		try {
			assumeFalse(Files.isWritable(src), "a folder that cannot be written is writable to this user");

			final ActionFailed failed = assertThrows(ActionFailed.class,
				() -> rig.actions().invoke(action, Form.parse(body + "&project="
					+ URLEncoder.encode(rig.project().dir().toString(), StandardCharsets.UTF_8))));

			assertEquals(before, rig.session().state());
			return failed.getCause();
		} finally {
			Files.setPosixFilePermissions(src, PosixFilePermissions.fromString("rwxr-xr-x"));
		}
	}

	private Path secret() throws Exception {
		return Files.writeString(rig.home().resolve("secret"), "private key");
	}

	@Example
	public void shouldSendEverySourceAsItIsOnDisk() throws Exception {
		final JsonObject sent = source(Screen.open(rig)).getJsonArray("files").getJsonObject(0);

		assertEquals("main.s", sent.getString("name"));
		assertEquals(Files.readString(file("main.s")), sent.getString("text"));
		assertEquals(Files.getLastModifiedTime(file("main.s")).toMillis(),
			sent.getJsonNumber("modified").longValue());
	}

	@Example
	public void shouldSaveTheBufferToItsFile() throws Exception {
		Files.setLastModifiedTime(file("main.s"), FileTime.fromMillis(1_000_000));

		final Screen screen = saved(Screen.open(rig), "start:\n\trts\n");
		final JsonObject sent = source(screen).getJsonArray("files").getJsonObject(0);

		assertEquals("start:\n\trts\n", Files.readString(file("main.s")));
		assertEquals("start:\n\trts\n", sent.getString("text"));
		assertNotEquals(1_000_000, sent.getJsonNumber("modified").longValue());
		assertEquals(Files.getLastModifiedTime(file("main.s")).toMillis(),
			sent.getJsonNumber("modified").longValue());
		assertEquals("Saved main.s", screen.find("#status .status__note").text());
	}

	@Example
	public void shouldSaveEveryBufferSent() throws Exception {
		Files.writeString(file("tables.i"), "sine:\n");

		final Screen screen = Screen.open(rig).trigger(SAVE_ALL,
			owned(rig, Map.of("files", "main.s,tables.i", "main.s", "; main\n", "tables.i", "; tables\n")));

		assertEquals("; main\n", Files.readString(file("main.s")));
		assertEquals("; tables\n", Files.readString(file("tables.i")));
		assertEquals("Saved main.s, tables.i", screen.find("#status .status__note").text());
	}

	@Example
	public void shouldRefuseANameThatLeadsOutOfTheSources() throws Exception {
		final Screen screen = Screen.open(rig);
		final String chp = Files.readString(rig.project().chp());

		for (final String name : List.of("../demo.chp", "../../escaped.s", "/tmp/escaped.s", "main.txt")) {
			assertEquals(500, screen.trigger(SAVE, owned(rig, Map.of("name", name, "text", "x"))).status(),
				name);
		}

		assertEquals(chp, Files.readString(rig.project().chp()));
		assertFalse(Files.exists(file("main.txt")));
		assertFalse(Files.exists(rig.project().dir().resolve("escaped.s")));
	}

	@Example
	public void shouldWriteASourceThatLeftTheProjectBackAsANewFile() throws Exception {
		final Screen screen = Screen.open(rig);

		Files.delete(file("main.s"));
		screen.trigger(SAVE, owned(rig, Map.of("name", "main.s", "text", "; typed meanwhile\n")));

		assertEquals("; typed meanwhile\n", Files.readString(file("main.s")));
		assertEquals("main.s", screen.find("#status").attr("data-saved"));
	}

	@Example
	public void shouldTellWhichSourcesItWroteAndNoneWhenItWroteNone() throws Exception {
		final Screen screen = Screen.open(rig).trigger(SAVE_ALL, owned(rig, Map.of("files", "main.s",
			"main.s", "; one\n")));

		assertEquals("main.s", screen.find("#status").attr("data-saved"));
		assertEquals("", screen.trigger(CHECK, Map.of("name", "main.s", "modified", "1")).find("#status")
			.attr("data-saved"));
	}

	@Example
	public void shouldNameTheFolderOfTheOpenProjectInTheStatusBar() throws Exception {
		assertEquals(rig.project().dir().toString(), Screen.open(rig).find("#status").attr("data-project"));
	}

	@Example
	public void shouldRefuseTextTypedInAnotherProject() throws Exception {
		final Screen screen = Screen.open(rig);
		final String before = Files.readString(file("main.s"));
		final StudioState state = rig.session().state();

		assertEquals(500, screen.trigger(SAVE, Map.of("name", "main.s", "text", "; stray\n", "project",
			rig.home().resolve("other").toString())).status());
		assertEquals(500, screen.trigger(SAVE_ALL, Map.of("files", "main.s", "main.s", "; stray\n")).status());
		assertEquals(before, Files.readString(file("main.s")));
		assertEquals(state, rig.session().state());
	}

	@Example
	public void shouldRefuseASaveThatBringsNoNameOrNoText() throws Exception {
		final Screen screen = Screen.open(rig);
		final StudioState state = rig.session().state();

		assertEquals(400, screen.trigger(SAVE, Map.of("text", "x")).status());
		assertEquals(400, screen.trigger(SAVE, Map.of("name", "main.s")).status());
		assertEquals(400, screen.trigger(GUTTER, Map.of()).status());
		assertEquals(400, screen.trigger(CURSOR, Map.of()).status());
		assertEquals(400, screen.trigger(CHECK, Map.of()).status());
		assertEquals(state, rig.session().state());
	}

	@Example
	public void shouldSaveAnEmptyText() throws Exception {
		Screen.open(rig).trigger(SAVE, owned(rig, Map.of("name", "main.s", "text", "")));

		assertEquals("", Files.readString(file("main.s")));
	}

	@Example
	public void shouldWriteNoneOfTheBuffersWhenOneNameIsRefused() throws Exception {
		final String before = Files.readString(file("main.s"));
		final Screen screen = Screen.open(rig);

		assertEquals(500, screen.trigger(SAVE_ALL, owned(rig,
			Map.of("files", "main.s,../demo.chp", "main.s", "; lost\n", "../demo.chp", "{}"))).status());
		assertEquals(before, Files.readString(file("main.s")));
	}

	@Example
	public void shouldKeepTheBreakpointsInTheProjectFile() throws Exception {
		final Screen screen = Screen.open(rig).trigger(GUTTER, Map.of("line", "3"));

		assertEquals(List.of(new Breakpoint("main.s", 3, 0)), kept());
		screen.trigger(GUTTER, Map.of("line", "3"));
		assertEquals(List.of(), kept());
	}

	@Example
	public void shouldKeepTheBreakpointsForTheNextLaunch() throws Exception {
		Screen.open(rig).trigger(GUTTER, Map.of("line", "4")).trigger("[data-key=f9]", Map.of("line", "6"));
		rig = rig.restart(now::get);

		assertEquals(List.of(4, 6), lines(source(Screen.open(rig))));
	}

	@Example
	public void shouldClearTheBreakpointsOfTheProjectFile() throws Exception {
		Screen.open(rig).trigger(GUTTER, Map.of("line", "2"))
			.click("#menus [hx-post=\"/action/breakpoints-clear\"]");

		assertEquals(List.of(), kept());
	}

	@Example
	public void shouldKeepTheBuildOptionsInTheProjectFile() throws Exception {
		Screen.open(rig).click("#menus [hx-vals*=SHRINKLER]");

		assertFalse(ProjectFile.parse(Files.readString(rig.project().chp())).crunch());
	}

	@Example
	public void shouldNameTheSourceThatCouldNotBeSavedWithoutTheStagingFile() throws Exception {
		final String message = refused("file-save", "name=main.s&text=x").getMessage();

		assertTrue(message.startsWith("main.s could not be saved: "), message);
		assertFalse(message.contains(".tmp"), message);
	}

	@Example
	public void shouldNameTheFirstSourceThatCouldNotBeSavedWhenSavingAll() throws Exception {
		Files.writeString(file("tables.i"), "sine:\n");

		final String message = refused("file-save-all", "files=main.s,tables.i&main.s=a&tables.i=b").getMessage();

		assertTrue(message.startsWith("main.s could not be saved: "), message);
		assertFalse(message.contains(".tmp") || message.contains("already saved"), message);
	}

	@Example
	public void shouldOfferSaveAndSaveAllOnlyWithAProjectOpen() throws Exception {
		final Screen with = Screen.open(rig);

		assertFalse(with.find(SAVE).hasAttr("disabled"));
		assertFalse(with.find(SAVE_ALL).hasAttr("disabled"));
		rig.close();
		rig = WorkbenchRig.empty(now::get);

		final Screen without = Screen.open(rig);

		assertTrue(without.find(SAVE).hasAttr("disabled"));
		assertTrue(without.find(SAVE_ALL).hasAttr("disabled"));
	}

	@Example
	public void shouldKeepTheMachineInTheProjectFile() throws Exception {
		Screen.open(rig).click("#menus [hx-post=/action/target][hx-vals*=AGA]");

		assertEquals(Target.A1200_AGA, ProjectFile.parse(Files.readString(rig.project().chp())).target());
		rig = rig.restart(now::get);
		assertEquals(Target.A1200_AGA, rig.session().state().target());
	}

	@Example
	public void shouldDrawTheEditorAnewWithoutTheTabOfASourceThatLeftTheProject() throws Exception {
		Files.writeString(file("tables.i"), "sine:\n");

		final Screen screen = Screen.open(rig);

		Files.delete(file("tables.i"));
		screen.trigger(CHECK, Map.of("name", "tables.i", "modified", "1"));

		assertEquals(200, screen.status());
		assertEquals(List.of("main.s"), screen.document().select("#tool .tool__tab").eachText());
	}

	@Example
	public void shouldRefuseToCheckANameThatIsNoSourceName() throws Exception {
		assertEquals(500, Screen.open(rig).trigger(CHECK, Map.of("name", "../demo.chp", "modified", "1")).status());
	}

	@Example
	public void shouldNotShowASourceThatIsALinkToAFileElsewhere() throws Exception {
		Files.createSymbolicLink(file("tables.i"), secret());

		final Screen screen = Screen.open(rig);

		assertEquals(List.of("main.s"), screen.document().select("#tool .tool__tab").eachText());
		assertFalse(screen.document().html().contains("private key"));
		assertEquals(500, screen.trigger("#tool [hx-post=\"/action/file-open\"]", Map.of("name", "tables.i"))
			.status());
		assertEquals(500, screen.trigger(SAVE, owned(rig, Map.of("name", "tables.i", "text", "x"))).status());
		assertEquals("private key", Files.readString(secret()));
	}

	@Example
	public void shouldOpenAProjectWhoseEntryIsALinkWithoutItsTextAndSayWhy() throws Exception {
		Files.delete(file("main.s"));
		Files.createSymbolicLink(file("main.s"), secret());
		rig = rig.restart(now::get);

		final Screen screen = noted(Screen.open(rig), rig);

		assertEquals(List.of(), screen.document().select("#tool .tool__tab").eachText());
		assertFalse(screen.document().html().contains("private key"));
		assertEquals("Opened demo.chp (demo); the entry source main.s is missing or a link",
			screen.find("#status .status__note").text());
	}

	@Example
	public void shouldRememberTheCursorWithoutRedrawingTheEditor() throws Exception {
		final Screen screen = Screen.open(rig);

		assertEquals(204, screen.trigger(CURSOR, Map.of("line", "7")).status());
		assertEquals(7, rig.session().state().cursor());
	}

	@Example
	public void shouldShowTheNoteOfABuildThatEndedBeforeACaretReportArrived() throws Exception {
		final Screen screen = Screen.open(rig).trigger("[data-key=\"ctrl+b\"]", Map.of());

		awaitNote("Built demo.adf · 7 bytes");
		assertEquals(204, screen.trigger(CURSOR, Map.of("line", "7")).status());

		assertEquals("Built demo.adf · 7 bytes", finished(screen).find("#status .status__note").text());
	}

	@Example
	public void shouldKeepTheNoteWhileAnAnswerThatDrawsNothingComes() throws Exception {
		final Screen screen = noted(Screen.open(rig), rig);
		final String note = rig.session().state().note();
		final String modified = String.valueOf(Files.getLastModifiedTime(file("main.s")).toMillis());

		assertEquals(204, screen.trigger(CURSOR, Map.of("line", "7")).status());
		assertEquals(204, screen.client().submit("/action/layout", "split=44&bottom=320").statusCode());
		assertEquals(204, screen.trigger(CHECK, Map.of("name", "main.s", "modified", modified)).status());
		assertEquals(note, rig.session().state().note());
	}

	private void awaitNote(final String note) throws InterruptedException {
		final long deadline = System.nanoTime() + NOTE_WAIT.toNanos();

		while (!note.equals(rig.session().state().note())) {
			assertTrue(System.nanoTime() < deadline, "The note never came: " + note);
			Thread.sleep(NOTE_POLL_MILLIS);
		}
	}

	@Example
	public void shouldAnswerNothingWhileTheFileIsAsTheEditorLoadedIt() throws Exception {
		final Screen screen = Screen.open(rig);
		final String modified = String.valueOf(Files.getLastModifiedTime(file("main.s")).toMillis());

		assertEquals(204, screen.trigger(CHECK, Map.of("name", "main.s", "modified", modified)).status());
	}

	@Example
	public void shouldSendTheFileAnewOnceItChangedOnDisk() throws Exception {
		final Screen screen = Screen.open(rig);
		final String modified = String.valueOf(Files.getLastModifiedTime(file("main.s")).toMillis());

		Files.writeString(file("main.s"), "; changed elsewhere\n");
		Files.setLastModifiedTime(file("main.s"), FileTime.fromMillis(Long.parseLong(modified) + 5_000));
		screen.trigger(CHECK, Map.of("name", "main.s", "modified", modified));

		assertEquals(200, screen.status());
		assertEquals("; changed elsewhere\n",
			source(screen).getJsonArray("files").getJsonObject(0).getString("text"));
	}

	/* A source is text the page reads as JSON from inside a script element, which a closing tag would end early. */
	@Example
	public void shouldSendASourceHoldingAClosingScriptTagWhole() throws Exception {
		final String text = "; </script><b>not markup</b>\n";
		final Screen screen = saved(Screen.open(rig), text);

		assertTrue(screen.document().select("#tool b").isEmpty());
		assertTrue(screen.find("#tool script[data-source]").data().contains("\\u003c/script>"));
		assertEquals(text, source(screen).getJsonArray("files").getJsonObject(0).getString("text"));
	}

	@Example
	public void shouldRefuseToOpenAFileTheProjectDoesNotHold() throws Exception {
		final Screen screen = Screen.open(rig);
		final StudioState before = rig.session().state();

		assertEquals(500, screen.trigger("#tool [hx-post=\"/action/file-open\"]", Map.of("name", "../demo.chp"))
			.status());
		assertEquals(before, rig.session().state());
	}
}
