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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.copperhead.core.debug.Breakpoint;
import org.copperhead.core.project.ProjectFile;
import org.copperhead.ui.session.StudioState;
import static org.copperhead.ui.Flows.GUTTER;
import static org.copperhead.ui.Flows.lines;
import static org.copperhead.ui.Flows.saved;
import static org.copperhead.ui.Flows.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The editor's text lives in the page; saving it, its breakpoints and the file on disk are the studio's. */
public class EditorFlowTest {
	private static final String SAVE = "[data-key=\"ctrl+s\"]";
	private static final String SAVE_ALL = "[data-key=\"ctrl+shift+s\"]";
	private static final String CHECK = "#tool [data-editor-control=check]";

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
		final Screen screen = saved(Screen.open(rig), "start:\n\trts\n");

		assertEquals("start:\n\trts\n", Files.readString(file("main.s")));
		assertEquals("start:\n\trts\n", source(screen).getJsonArray("files").getJsonObject(0).getString("text"));
		assertEquals("Saved main.s", screen.find("#status .status__note").text());
	}

	@Example
	public void shouldSaveEveryBufferSent() throws Exception {
		Files.writeString(file("tables.i"), "sine:\n");

		final Screen screen = Screen.open(rig).trigger(SAVE_ALL,
			Map.of("files", "main.s,tables.i", "main.s", "; main\n", "tables.i", "; tables\n"));

		assertEquals("; main\n", Files.readString(file("main.s")));
		assertEquals("; tables\n", Files.readString(file("tables.i")));
		assertEquals("Saved main.s, tables.i", screen.find("#status .status__note").text());
	}

	@Example
	public void shouldRefuseANameThatLeadsOutOfTheSources() throws Exception {
		final Screen screen = Screen.open(rig);
		final String chp = Files.readString(rig.project().chp());

		for (final String name : List.of("../demo.chp", "../../escaped.s", "/tmp/escaped.s", "missing.s")) {
			assertEquals(500, screen.trigger(SAVE, Map.of("name", name, "text", "x")).status(), name);
		}

		assertEquals(chp, Files.readString(rig.project().chp()));
		assertFalse(Files.exists(file("missing.s")));
		assertFalse(Files.exists(rig.project().dir().resolve("escaped.s")));
	}

	@Example
	public void shouldWriteNoneOfTheBuffersWhenOneNameIsRefused() throws Exception {
		final String before = Files.readString(file("main.s"));
		final Screen screen = Screen.open(rig);

		assertEquals(500, screen.trigger(SAVE_ALL,
			Map.of("files", "main.s,../demo.chp", "main.s", "; lost\n", "../demo.chp", "{}")).status());
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
	public void shouldRememberTheCursorWithoutRedrawingTheEditor() throws Exception {
		final Screen screen = Screen.open(rig);

		assertEquals(204, screen.trigger("#tool [data-editor-control=cursor]", Map.of("line", "7")).status());
		assertEquals(7, rig.session().state().cursor());
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
