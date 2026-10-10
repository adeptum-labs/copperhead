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
import java.nio.file.attribute.PosixFilePermissions;
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
import org.copperhead.ui.view.Modal;
import org.jsoup.nodes.Element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Making, finding, opening and setting up projects, all within the home folder of the person. */
public class ProjectFlowTest {
	private static final String CREATE = "#modal .modal__buttons .button--accent";
	private static final String ENTRY = "#modal .modal__entry";
	private static final String RECENT = "#menus [hx-post=\"/action/project-recent\"]";
	private static final String UNSAVED = "edited\n";
	private static final List<String> EDITOR_COMMANDS = List.of("Undo", "Redo", "Find…", "Go to line…");

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

	/* What a cloned project can hold: a src folder that is a link to somewhere else. */
	private void linkSources(final Path chp) throws Exception {
		final Path dir = chp.getParent();
		final Path elsewhere = Files.move(dir.resolve("src"), rig.home().resolve("elsewhere-" + dir.getFileName()));

		Files.createSymbolicLink(dir.resolve("src"), elsewhere);
	}

	private Map<String, String> draft(final String name, final Path parent) {
		return Map.of("name", name, "kind", "GAME", "target", "AGA", "parent", parent.toString());
	}

	private static String project(final Screen screen) {
		return screen.find("#menus .menus__project").text();
	}

	private static List<Element> editorItems(final Screen screen) {
		return screen.document().select("#menus .menu__item").stream()
			.filter(item -> EDITOR_COMMANDS.contains(item.select(".menu__label").text())).toList();
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
		assertEquals(EDITOR_COMMANDS, editorItems(screen).stream().map(item -> item.select(".menu__label").text())
			.toList());
		assertTrue(editorItems(screen).stream().allMatch(item -> item.hasAttr("disabled")));
	}

	@Example
	public void shouldOfferTheEditorCommandsOnceAProjectIsOpen() throws Exception {
		final List<Element> items = editorItems(Screen.open(rig));

		assertEquals(EDITOR_COMMANDS, items.stream().map(item -> item.select(".menu__label").text()).toList());
		assertEquals(List.of("undo", "redo", "find", "goto-line"),
			items.stream().map(item -> item.attr("data-editor")).toList());
		assertTrue(items.stream().noneMatch(item -> item.hasAttr("disabled")));
	}

	@Example
	public void shouldRefuseTheSettingsWhileNoProjectIsOpen() throws Exception {
		final Screen screen = welcome();
		final StudioState before = rig.session().state();

		assertEquals(500, screen.trigger("#menus [hx-post=\"/action/modal\"]", Map.of("kind", "SETTINGS")).status());
		assertEquals(before, rig.session().state());
		assertEquals("no project", project(Screen.open(rig)));
	}

	@Example
	public void shouldStillDrawWhenASettingsDialogIsLeftOpenWithNoProject() throws Exception {
		welcome();
		rig.session().change(state -> state.withModal(Modal.SETTINGS));

		final Screen screen = Screen.open(rig);

		assertTrue(screen.find("#modal").hasAttr("hidden"));
		assertEquals("no project", project(screen));
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
	public void shouldSayWhyAProjectWhoseSourceFolderIsALinkWillNotOpen() throws Exception {
		final Path linked = made("arcade");

		linkSources(linked);

		final StudioState before = rig.session().state();
		final Screen screen = browsed(Screen.open(rig), "arcade")
			.trigger(ENTRY + "--project", Map.of("path", linked.toString()));

		assertEquals("Open project", screen.find("#modal .modal__title").text());
		assertTrue(screen.find("#modal .modal__line").text().contains("src is a link"));
		assertEquals(before.workspace(), rig.session().state().workspace());
		assertEquals(200, Screen.open(rig).status());
	}

	@Example
	public void shouldSkipAProjectWhoseSourceFolderIsALinkWhenTheStudioStarts() throws Exception {
		welcome();

		final Path usable = made("nova");
		final Path linked = made("arcade");

		linkSources(linked);
		rig.preferences().used(usable);
		rig.preferences().used(linked);
		rig = rig.restart(now::get);

		assertEquals("nova.chp", project(Screen.open(rig)));
	}

	@Example
	public void shouldStillDrawWhenTheSourceFolderBecomesALinkAfterTheProjectOpened() throws Exception {
		linkSources(rig.project().chp());

		final Screen screen = Screen.open(rig);

		assertEquals(200, screen.status());
		assertEquals(List.of(), screen.document().select("#tool .tool__tab").eachText());
		assertEquals("sources unavailable: src is a link", screen.find("#tool .tool__meta").text());
		assertEquals("Project settings",
			screen.click("#menus [hx-vals*=SETTINGS]").find("#modal .modal__title").text());
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
		assertEquals(List.of(folder.resolve("nova.chp")), rig.preferences().recent());
	}

	@Example
	public void shouldRefuseARenameOntoAProjectFileThatIsThere() throws Exception {
		final Path folder = projects().resolve("demo");
		final Path other = Files.writeString(folder.resolve("nova.chp"), "kept");
		final Screen screen = Screen.open(rig).click("#menus [hx-vals*=SETTINGS]")
			.trigger(CREATE, Map.of("name", "nova", "target", "OCS", "loadAddress", "$20000",
				"entry", "src/main.s"));

		assertEquals("Project settings", screen.find("#modal .modal__title").text());
		assertEquals("The folder already holds nova.chp", screen.find("#modal .modal__line").text());
		assertEquals("kept", Files.readString(other));
		assertTrue(Files.isRegularFile(folder.resolve("demo.chp")));
		assertEquals("demo.chp", project(screen));
	}

	@Example
	public void shouldNotOpenAProjectFileCalledAnythingButItsName() throws Exception {
		final Path folder = projects().resolve("demo");
		final Path copy = Files.copy(folder.resolve("demo.chp"), folder.resolve("copy.chp"));
		final StudioState before = rig.session().state();
		final Screen screen = Screen.open(rig).trigger("#menus [hx-post=\"/action/project-open\"]",
			Map.of("path", copy.toString()));

		assertEquals("Open project", screen.find("#modal .modal__title").text());
		assertTrue(screen.find("#modal .modal__line").text().contains("has to be called demo.chp"));
		assertEquals(before.workspace(), rig.session().state().workspace());
		assertTrue(Files.isRegularFile(folder.resolve("demo.chp")));
	}

	@Example
	public void shouldLookInTheHomeWhenTheFileAskedForIsTheHomeItself() throws Exception {
		final StudioState before = rig.session().state();

		for (final String path : List.of(".", "projects/..", rig.home().toString())) {
			final Screen screen = Screen.open(rig).trigger("#menus [hx-post=\"/action/project-open\"]",
				Map.of("path", path));

			assertEquals("Open project", screen.find("#modal .modal__title").text(), path);
			assertEquals("~", screen.find("#modal dd").text(), path);
			assertEquals(before.workspace(), rig.session().state().workspace(), path);
		}
	}

	@Example
	public void shouldRefuseAFolderMadeThroughALinkOutOfTheHome() throws Exception {
		final Path outside = Files.createTempDirectory("copperhead-outside");

		try {
			final Path link = Files.createSymbolicLink(projects().resolve("escape"), outside);
			final Screen screen = Screen.open(rig).click("#menus [hx-post=\"/action/project-new\"]");
			final StudioState before = rig.session().state();

			assertEquals(500, screen.trigger(CREATE, draft("nova", link)).status());
			assertEquals(before, rig.session().state());
			assertFalse(Files.exists(outside.resolve("nova")));
		} finally {
			TempDirectory.delete(outside);
		}
	}

	@Example
	public void shouldSaveNothingWhenOneOfTheBuffersIsNoSourceOfTheProject() throws Exception {
		final Path arcade = made("arcade");
		final String saved = Files.readString(projects().resolve("demo/src/main.s"));
		final Screen screen = asked(arcade);
		final StudioState before = rig.session().state();

		for (final String bad : List.of("nope.s", "../main.s", "sub/main.s")) {
			assertEquals(500, screen.trigger(CREATE, Map.of("path", arcade.toString(), "save", "true",
				"files", "main.s," + bad, "main.s", UNSAVED, bad, UNSAVED)).status(), bad);
			assertEquals(saved, Files.readString(projects().resolve("demo/src/main.s")), bad);
			assertEquals(before, rig.session().state(), bad);
		}
	}

	@Example
	public void shouldSayWhichBufferCouldNotBeSavedAndStay() throws Exception {
		final Path src = projects().resolve("demo/src");
		final Path arcade = made("arcade");
		final Screen screen = asked(arcade);
		final StudioState before = rig.session().state();

		Files.setPosixFilePermissions(src, PosixFilePermissions.fromString("r-xr-xr-x"));

		try {
			assumeFalse(Files.isWritable(src), "a folder that cannot be written is writable to this user");
			screen.trigger(CREATE, Map.of("path", arcade.toString(), "save", "true", "files", "main.s",
				"main.s", UNSAVED));
			assertEquals("Unsaved changes", screen.find("#modal .modal__title").text());
			assertTrue(screen.document().select("#modal .modal__line").first().text()
				.startsWith("main.s could not be saved"));
			assertEquals("demo.chp", project(screen));
			assertEquals(before.workspace(), rig.session().state().workspace());
		} finally {
			Files.setPosixFilePermissions(src, PosixFilePermissions.fromString("rwxr-xr-x"));
		}
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
