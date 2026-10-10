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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.copperhead.core.project.ProjectFile;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.Target;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.ui.session.StudioState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Making, finding, opening and setting up projects, all within the home folder of the person. */
public class ProjectFlowTest {
	private static final String CREATE = "#modal .modal__buttons .button--accent";
	private static final String ENTRY = "#modal .modal__entry";
	private static final String RECENT = "#menus [hx-post=\"/action/project-recent\"]";
	private static final String UNSAVED = "edited\n";

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

	private Screen welcome() throws Exception {
		rig.close();
		rig = WorkbenchRig.empty(now::get);
		return Screen.open(rig);
	}

	private Path projects() {
		return rig.home().resolve(WorkbenchRig.PROJECTS);
	}

	private Path made(final String name) throws Exception {
		return ProjectDirectory.create(projects(), name, ProjectKind.GAME, Target.A500_OCS).chp();
	}

	private Map<String, String> draft(final String name, final Path parent) {
		return Map.of("name", name, "kind", "GAME", "target", "AGA", "parent", parent.toString());
	}

	private static String project(final Screen screen) {
		return screen.find("#menus .menus__project").text();
	}

	/* The dialog opens in the folder that holds the open project's own, and goes down the folders named. */
	private Screen browsed(final Screen screen, final String... folders) throws Exception {
		screen.click("#menus [hx-post=\"/action/project-open\"]");

		for (final String folder : folders) {
			screen.click(ENTRY + ":containsOwn(" + folder + "/)");
		}

		return screen;
	}

	/* Opens another project with the entry file unsaved, as the page sends it; the switch has to ask first. */
	private Screen asked(final Path other) throws Exception {
		return browsed(Screen.open(rig), "arcade").trigger(ENTRY + "--project",
			Map.of("path", other.toString(), "files", "main.s", "main.s", UNSAVED));
	}

	@Example
	public void shouldWelcomeWithNewAndOpenWhileNoProjectIsOpen() throws Exception {
		final Screen screen = welcome();

		assertEquals(List.of("New project…", "Open project…"),
			screen.document().select("#tool .welcome__actions .button").eachText());
		assertEquals("no project open", screen.find("#tool .tool__meta").text());
		assertEquals("no project", project(screen));
		assertTrue(screen.document().select("#menus [data-editor]").stream()
			.allMatch(item -> item.hasAttr("disabled")));
	}

	@Example
	public void shouldOfferTheEditorCommandsOnceAProjectIsOpen() throws Exception {
		assertEquals(List.of("undo", "redo", "find", "goto-line"),
			Screen.open(rig).document().select("#menus [data-editor]:not([disabled])").eachAttr("data-editor"));
	}

	@Example
	public void shouldListAProjectThatWouldNotOpenOnTheWelcomeAndSayWhy() throws Exception {
		welcome();

		final Path chp = made("future");

		Files.writeString(chp, "{\"format\": 99, \"name\": \"future\", \"kind\": \"DEMO\"}");
		rig.preferences().used(chp);

		rig = rig.restart(now::get);

		final Screen screen = Screen.open(rig);

		assertEquals("future.chp", screen.find("#tool .welcome__name").text());
		screen.click("#tool .welcome__project");
		assertEquals("Open project", screen.find("#modal .modal__title").text());
		assertTrue(screen.find("#modal .modal__line").text().contains("newer"));
	}

	@Example
	public void shouldMakeANewProjectFromItsTemplate() throws Exception {
		final Screen screen = welcome().click("#tool [hx-post=\"/action/project-new\"]");

		assertEquals("New project", screen.find("#modal .modal__title").text());
		assertEquals(rig.home().toString(), screen.find("#modal [data-field][name=parent]").val());
		screen.trigger(CREATE, draft("nova", projects()));

		final Path nova = projects().resolve("nova");

		assertTrue(Files.isRegularFile(nova.resolve("nova.chp")));
		assertTrue(Files.isRegularFile(nova.resolve("src/main.s")));
		assertEquals("nova.chp", project(screen));
		assertEquals(List.of("main.s", "sprite.i"), screen.document().select("#tool .tool__tab").eachText());
		assertEquals("AGA", screen.find("#machine .machine__chipset--on").text());
		assertTrue(screen.find("#modal").hasAttr("hidden"));
		assertEquals("Opened nova.chp (game)", screen.find("#status .status__note").text());
		assertEquals(nova.resolve("nova.chp"), rig.preferences().recent().getFirst());
	}

	@Example
	public void shouldSayWhatANameMayHold() throws Exception {
		final Screen screen = welcome().click("#tool [hx-post=\"/action/project-new\"]")
			.trigger(CREATE, draft("my demo!", projects()));

		assertEquals("New project", screen.find("#modal .modal__title").text());
		assertTrue(screen.find("#modal .modal__line").text().startsWith("A name starts with a letter or digit"));
		assertEquals("my demo!", screen.find("#modal [data-field][name=name]").val());
		assertFalse(Files.exists(projects().resolve("my demo!")));
	}

	@Example
	public void shouldRefuseToMakeAProjectOutsideTheHome() throws Exception {
		final Screen screen = welcome().click("#tool [hx-post=\"/action/project-new\"]");
		final StudioState before = rig.session().state();

		assertEquals(500, screen.trigger(CREATE, draft("nova", rig.home().getParent())).status());
		assertEquals(before, rig.session().state());
	}

	@Example
	public void shouldSayWhyAProjectCannotBeMadeOverAnother() throws Exception {
		final Screen screen = Screen.open(rig).click("#menus [hx-post=\"/action/project-new\"]");
		final String hint = screen.find("#modal .modal__line").text();

		screen.trigger(CREATE, draft("demo", projects()));

		assertEquals("New project", screen.find("#modal .modal__title").text());
		assertNotEquals(hint, screen.find("#modal .modal__line").text());
		assertEquals("demo.chp", project(screen));
	}

	@Example
	public void shouldBrowseTheFoldersOfTheHome() throws Exception {
		final Screen screen = browsed(Screen.open(rig));

		assertEquals("~/projects", screen.find("#modal dd").text());
		assertEquals(List.of("..", "demo/"), screen.document().select(ENTRY).eachText());
		screen.click(ENTRY + "--up");
		assertEquals("~", screen.find("#modal dd").text());
		assertTrue(screen.document().select(ENTRY).eachText().contains("projects/"));
		assertTrue(screen.document().select(ENTRY).eachText().stream().noneMatch(entry -> entry.startsWith(".")));
		screen.click(ENTRY + ":containsOwn(projects/)").click(ENTRY + ":containsOwn(demo/)");
		assertEquals(List.of("..", "build/", "gfx/", "src/", "demo.chp"),
			screen.document().select(ENTRY).eachText());
	}

	@Example
	public void shouldKeepTheBrowserInsideTheHome() throws Exception {
		final Screen screen = browsed(Screen.open(rig));

		assertEquals(500, screen.trigger(ENTRY, Map.of("path", "/")).status());
		assertEquals(500, screen.trigger(ENTRY, Map.of("path", "../..")).status());
		assertEquals("~/projects", screen.find("#modal dd").text());
	}

	@Example
	public void shouldNotFollowALinkOutOfTheHome() throws Exception {
		final Path outside = Files.createTempDirectory("copperhead-outside");

		try {
			final Path link = Files.createSymbolicLink(projects().resolve("escape"), outside);
			final Screen screen = browsed(Screen.open(rig));

			assertEquals(List.of("..", "demo/"), screen.document().select(ENTRY).eachText());
			assertEquals(500, screen.trigger(ENTRY, Map.of("path", link.toString())).status());
			assertEquals("~/projects", screen.find("#modal dd").text());
		} finally {
			TempDirectory.delete(outside);
		}
	}

	@Example
	public void shouldOpenAProjectPickedInTheBrowser() throws Exception {
		final Path arcade = made("arcade");
		final Screen screen = browsed(Screen.open(rig), "arcade")
			.trigger(ENTRY + "--project", Map.of("path", arcade.toString()));

		assertEquals("arcade.chp", project(screen));
		assertEquals("Level 1", screen.find("#timeline .timeline__name").text());
		assertEquals(arcade, rig.preferences().recent().getFirst());
	}

	@Example
	public void shouldAskBeforeLeavingUnsavedChangesBehind() throws Exception {
		final Screen screen = asked(made("arcade"));

		assertEquals("Unsaved changes", screen.find("#modal .modal__title").text());
		assertEquals("main.s has changes that are not saved.", screen.find("#modal .modal__line").text());
		assertEquals(List.of("Save all", "Discard", "Cancel"),
			screen.document().select("#modal .modal__buttons .button").eachText());
		assertEquals("demo.chp", project(screen));
	}

	@Example
	public void shouldSaveTheChangesIntoTheProjectLeft() throws Exception {
		final Path arcade = made("arcade");
		final Screen screen = asked(arcade).trigger(CREATE, Map.of("path", arcade.toString(), "save", "true",
			"files", "main.s", "main.s", UNSAVED));

		assertEquals(UNSAVED, Files.readString(projects().resolve("demo/src/main.s")));
		assertEquals("arcade.chp", project(screen));
	}

	@Example
	public void shouldLeaveTheChangesBehindWhenToldTo() throws Exception {
		final String saved = Files.readString(projects().resolve("demo/src/main.s"));
		final Screen screen = asked(made("arcade")).click("#modal .modal__buttons .button:containsOwn(Discard)");

		assertEquals("arcade.chp", project(screen));
		assertEquals(saved, Files.readString(projects().resolve("demo/src/main.s")));
	}

	@Example
	public void shouldStayWhenTheSwitchIsCancelled() throws Exception {
		final Screen screen = asked(made("arcade")).click("#modal .modal__buttons .button:containsOwn(Cancel)");

		assertTrue(screen.find("#modal").hasAttr("hidden"));
		assertEquals("demo.chp", project(screen));
	}

	@Example
	public void shouldSayWhyAProjectWillNotOpen() throws Exception {
		final Path broken = made("arcade");

		Files.writeString(broken, "{\"format\": 99, \"name\": \"arcade\", \"kind\": \"GAME\"}");

		final Screen screen = browsed(Screen.open(rig), "arcade")
			.trigger(ENTRY + "--project", Map.of("path", broken.toString()));

		assertTrue(screen.find("#modal .modal__line").text().contains("newer"));
		assertEquals("demo.chp", project(screen));
	}

	@Example
	public void shouldOpenAProjectOpenedLatelyFromTheFileMenu() throws Exception {
		final Path demo = rig.project().chp();
		final Screen screen = Screen.open(rig).click("#menus [hx-post=\"/action/project-new\"]")
			.trigger(CREATE, draft("nova", projects()));

		assertEquals(List.of("Open recent · nova.chp · " + projects().resolve("nova"),
			"Open recent · demo.chp · " + projects().resolve("demo")),
			screen.document().select(RECENT + " .menu__label").eachText());
		screen.trigger(RECENT, Map.of("path", demo.toString()));
		assertEquals("demo.chp", project(screen));
	}

	@Example
	public void shouldRefuseAProjectNotOpenedLately() throws Exception {
		final Path arcade = made("arcade");
		final Screen screen = Screen.open(rig);
		final StudioState before = rig.session().state();

		assertEquals(500, screen.trigger(RECENT, Map.of("path", arcade.toString())).status());
		assertEquals(before, rig.session().state());
	}

	@Example
	public void shouldChangeTheSettingsOfTheProject() throws Exception {
		final Path folder = projects().resolve("demo");
		final Screen screen = Screen.open(rig).click("#menus [hx-vals*=SETTINGS]");

		assertEquals("$20000", screen.find("#modal [data-field][name=loadAddress]").val());
		screen.trigger(CREATE, Map.of("name", "nova", "target", "AGA", "loadAddress", "$30000",
			"entry", "src/main.s"));

		final ProjectFile saved = ProjectFile.parse(Files.readString(folder.resolve("nova.chp")));

		assertFalse(Files.exists(folder.resolve("demo.chp")));
		assertEquals(0x30000, saved.loadAddress());
		assertEquals(Target.A1200_AGA, saved.target());
		assertEquals("nova.chp", project(screen));
		assertEquals("AGA", screen.find("#machine .machine__chipset--on").text());
		assertEquals(folder.resolve("nova.chp"), rig.preferences().recent().getFirst());
	}

	@Example
	public void shouldSayWhatALoadAddressMustBe() throws Exception {
		final Screen screen = Screen.open(rig).click("#menus [hx-vals*=SETTINGS]")
			.trigger(CREATE, Map.of("name", "demo", "target", "OCS", "loadAddress", "$3",
				"entry", "src/main.s"));

		assertEquals("Project settings", screen.find("#modal .modal__title").text());
		assertTrue(screen.find("#modal .modal__line").text().startsWith("Load address:"));
		assertEquals("$3", screen.find("#modal [data-field][name=loadAddress]").val());
	}
}
