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

package org.copperhead.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Route;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicReference;
import net.jqwik.api.Example;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.Target;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.ui.WorkbenchRig;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * What becomes of the text typed into the editor while the studio redraws, switches projects, saves or loses a
 * file: it is never dropped, never taken for saved before the disk has it, and never sent to another project.
 */
public class BufferIT extends EditorTest {
	private static final String ARCADE = "arcade";
	private static final String SEARCH = "#tool .cm-search input[name=search]";
	private static final String GUTTER = "#tool .cm-breakpoint-gutter .cm-gutterElement";
	private static final String DIALOG = "#modal";
	private static final String SAVE_ALL = ".button:text-is('Save all')";
	private static final String SWITCHING = "**/action/project-open";
	private static final String READ_ONLY = "r-xr-xr-x";
	private static final String WRITABLE = "rwxr-xr-x";
	private static final String NOTHING = "---------";

	private Path made(final String name) throws IOException {
		return ProjectDirectory.create(rig().home().resolve(WorkbenchRig.PROJECTS), name, ProjectKind.GAME,
			Target.A500_OCS).dir().resolve("src").resolve(ENTRY);
	}

	private Path src() {
		return entry().getParent();
	}

	private void permit(final Path folder, final String mode) throws IOException {
		Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString(mode));
	}

	/* The Open dialog starts beside the open project, where the other one was made. */
	private void openOther() {
		page().click(".menu__button:text-is('File')");
		page().click(".menu__item:has-text('Open project')");
		page().click(DIALOG + " .modal__entry:text-is('" + ARCADE + "/')");
		page().click(DIALOG + " .modal__entry:text-is('" + ARCADE + ".chp')");
	}

	private void press(final Locator button) {
		page().waitForResponse(SWITCHING, button::click);
	}

	/* A control drawn by an answer takes no clicks until htmx has settled it, a moment after the swap. */
	private void settled() {
		page().waitForFunction("() => document.querySelector('.htmx-added') === null");
	}

	private boolean guarded() {
		return (Boolean) page().evaluate("() => { const leaving = new Event('beforeunload', {cancelable: true});"
			+ " window.dispatchEvent(leaving); return leaving.defaultPrevented; }");
	}

	@Example
	public void shouldKeepTheTextUnsavedWhenTheSaveBeforeASwitchIsRefused() throws Exception {
		made(ARCADE);
		typeAtTheTop("; mine");
		permit(src(), READ_ONLY);

		try {
			assumeFalse(Files.isWritable(src()), "a folder that cannot be written is writable to this user");
			openOther();

			final Locator dialog = page().locator(DIALOG);
			final Path open = rig().project().dir();

			assertThat(dialog.locator(".modal__title")).hasText("Unsaved changes");
			press(dialog.locator(SAVE_ALL));
			assertThat(dialog.locator(".modal__line").first()).containsText("could not be saved");
			assertThat(page().locator(UNSAVED)).hasClass(ON);

			press(dialog.locator(SAVE_ALL));
			assertThat(dialog.locator(".modal__title")).hasText("Unsaved changes");
			assertEquals(open, rig().project().dir());
			assertThat(page().locator(UNSAVED)).hasClass(ON);
		} finally {
			permit(src(), WRITABLE);
		}

		page().click(DIALOG + " .button:text-is('Cancel')");
		assertThat(page().locator(DIALOG)).isHidden();
		settled();
		page().keyboard().press("Control+s");
		await("the file written", () -> onDisk().startsWith("; mine"));
	}

	@Example
	public void shouldSaveWhatWasTypedAndThenOpenTheOtherProject() throws Exception {
		final Path other = made(ARCADE);
		final Path demo = entry();
		final String untouched = Files.readString(other);

		typeAtTheTop("; mine\n");
		openOther();
		press(page().locator(DIALOG + " " + SAVE_ALL));

		await("the other project open", () -> ARCADE.equals(rig().project().file().name()));
		assertThat(page().locator("#tool .tool__tab--active")).hasText(ENTRY);
		assertThat(page().locator(UNSAVED)).not().hasClass(ON);
		assertEquals(untouched, Files.readString(other));
		assertTrue(Files.readString(demo).startsWith("; mine\n"));
	}

	@Example
	public void shouldLeaveTheTextBehindWhenToldToAndOpenTheOtherProject() throws Exception {
		made(ARCADE);

		final Path demo = entry();
		final String kept = onDisk();

		typeAtTheTop("; mine\n");
		openOther();
		press(page().locator(DIALOG + " .button:text-is('Discard')"));

		await("the other project open", () -> ARCADE.equals(rig().project().file().name()));
		assertThat(page().locator(UNSAVED)).not().hasClass(ON);
		assertEquals(kept, Files.readString(demo));
	}

	@Example
	public void shouldNeverSendTheTextOfOneProjectToAnother() throws Exception {
		final Path other = made(ARCADE);
		final String untouched = Files.readString(other);

		typeAtTheTop("; mine\n");
		page().click("#rail [title='Sprites']");
		assertThat(page().locator("#tool .tool__tab--active")).hasText("ball.spr");
		settled();
		openOther();
		press(page().locator(DIALOG + " .button:text-is('Discard')"));
		assertThat(page().locator("#menus .menus__project")).hasText(ARCADE + ".chp");
		settled();
		page().keyboard().press("Control+b");

		assertThat(page().locator("#status .status__note")).containsText("Built arcade.adf");
		assertEquals(untouched, Files.readString(other));
		page().click("[data-window=close]");
		await("the window closed", () -> rig().window().commands().contains(CLOSE));
		assertThat(page().locator(CLOSING)).hasCount(0);
	}

	@Example
	public void shouldSaveTwiceInARowWhateverTheEditorIsDoing() {
		typeAtTheTop("; one\n");
		page().evaluate("() => { for (let press = 0; press < 3; press++) { document.body.dispatchEvent("
			+ "new KeyboardEvent('keydown', {key: 's', ctrlKey: true, bubbles: true})); } }");

		await("the file written", () -> onDisk().startsWith("; one\n"));
		assertThat(page().locator(UNSAVED)).not().hasClass(ON);
	}

	@Example
	public void shouldSaveWhileAnotherToolIsShown() {
		typeAtTheTop("; from afar\n");
		page().click("#rail [title='Sprites']");
		assertThat(page().locator("#tool .tool__tab--active")).hasText("ball.spr");
		settled();
		page().keyboard().press("Control+s");

		await("the file written", () -> onDisk().startsWith("; from afar\n"));
		page().click("#rail [title='Code']");
		assertThat(page().locator(UNSAVED)).not().hasClass(ON);
	}

	@Example
	public void shouldSaveFromTheSearchField() {
		typeAtTheTop("; searching\n");
		page().keyboard().press("Control+f");
		assertThat(page().locator(SEARCH)).isFocused();
		page().keyboard().press("Control+s");

		await("the file written", () -> onDisk().startsWith("; searching\n"));
	}

	@Example
	public void shouldNotReloadThePageAndLoseTheTextKeptOnlyInIt() {
		page().evaluate("window.probe = true");
		typeAtTheTop("; keep");
		page().keyboard().press("F5");
		page().keyboard().press("Control+r");
		page().keyboard().press("Control+Shift+R");

		assertEquals(true, page().evaluate("window.probe === true"));
		assertThat(page().locator(UNSAVED)).hasClass(ON);
	}

	@Example
	public void shouldAskBeforeThePageIsLeftOnlyWhileTextIsUnsaved() {
		assertFalse(guarded());
		typeAtTheTop("; keep");
		assertTrue(guarded());
		page().keyboard().press("Control+s");
		await("the file written", () -> onDisk().startsWith("; keep"));
		assertThat(page().locator(UNSAVED)).not().hasClass(ON);
		assertFalse(guarded());
	}

	@Example
	public void shouldKeepTheTextOfASourceDeletedOnDiskAndWriteItBack() throws Exception {
		typeAtTheTop("; mine\n");
		Files.delete(entry());
		page().evaluate("window.dispatchEvent(new Event('focus'))");

		assertThat(page().locator("#tool .code__missing")).containsText(ENTRY + " is no longer on disk");
		assertTrue(firstLine().startsWith("; mine"));

		page().keyboard().press("Control+s");

		await("the file written back", () -> Files.exists(entry()) && onDisk().startsWith("; mine\n"));
		assertThat(page().locator("#tool .code__missing")).hasCount(0);
	}

	@Example
	public void shouldKeepTheTextWhenTheSourcesCannotBeListed() throws Exception {
		typeAtTheTop("; mine\n");
		permit(src(), NOTHING);

		try {
			assumeFalse(Files.isReadable(src()), "a folder that cannot be read is readable to this user");
			page().locator(GUTTER).nth(THIRD_LINE).click();

			assertThat(page().locator("#tool .tool__meta")).containsText("sources unavailable");
			assertThat(page().locator("#tool .code__missing")).isVisible();
			assertTrue(firstLine().startsWith("; mine"));
		} finally {
			permit(src(), WRITABLE);
		}

		page().keyboard().press("Control+s");
		await("the file written", () -> onDisk().startsWith("; mine\n"));
	}

	private void survives(final Runnable redraw) {
		final String first = firstLine();

		typeAtTheTop("; note");
		redraw.run();

		assertThat(page().locator(UNSAVED)).hasClass(ON);
		assertEquals("; note" + first, firstLine());

		page().focus(TEXT);
		page().keyboard().type("!");
		assertEquals("; note!" + first, firstLine());

		for (int undone = 0; undone < 3; undone++) {
			page().keyboard().press("Control+z");
		}

		assertEquals(first, firstLine());
	}

	@Example
	public void shouldKeepTextHistoryAndCaretWhenTheGutterRedrawsTheTool() {
		survives(() -> {
			page().locator(GUTTER).nth(THIRD_LINE).click();
			assertThat(page().locator(BREAKPOINTS)).hasCount(1);
		});
	}

	@Example
	public void shouldKeepTextHistoryAndCaretThroughAnotherTool() {
		survives(() -> {
			page().click("#rail [title='Sprites']");
			assertThat(page().locator("#tool .tool__tab--active")).hasText("ball.spr");
			page().click("#rail [title='Code']");
			assertThat(page().locator("#tool .tool__tab--active")).hasText(ENTRY);
		});
	}

	@Example
	public void shouldKeepTextHistoryAndCaretThroughAnotherSource() throws Exception {
		Files.writeString(src().resolve("tables.i"), "sine:\n");
		page().reload();
		survives(() -> {
			page().click("#tool .tool__tab:has-text('tables.i')");
			assertThat(page().locator("#tool .tool__tab--active")).hasText("tables.i");
			page().click("#tool .tool__tab:has-text('" + ENTRY + "')");
			assertThat(page().locator("#tool .tool__tab--active")).hasText(ENTRY);
		});
	}

	@Example
	public void shouldTakeTheTextFromDiskWhenAskedToReload() throws Exception {
		typeAtTheTop("; mine");
		changeElsewhere("; theirs\n" + onDisk());

		final Locator question = page().locator("#tool .cm-conflict");

		question.locator("button:text-is('Reload')").click();

		assertThat(question).hasCount(0);
		assertEquals("; theirs", firstLine());
		assertThat(page().locator(UNSAVED)).not().hasClass(ON);
	}

	@Example
	public void shouldKeepWhatWasTypedWhileASaveWasOnItsWay() {
		final AtomicReference<Route> held = new AtomicReference<>();

		page().route("**/action/file-save", route -> {
			if (!held.compareAndSet(null, route)) {
				route.resume();
			}
		});

		typeAtTheTop("; first\n");
		page().keyboard().press("Control+s");
		await("the save on its way", () -> held.get() != null);
		page().keyboard().type("; second");
		held.get().resume();

		await("the file written", () -> onDisk().startsWith("; first\n"));
		assertThat(page().locator(UNSAVED)).hasClass(ON);
		assertThat(page().locator("#tool .cm-conflict")).hasCount(0);
		assertThat(page().locator("#tool [data-editor] .cm-line").nth(1)).containsText("; second");
	}

	@Example
	public void shouldKeepTheWindowOpenWhenTheFilesCannotBeSavedOnTheWayOut() throws Exception {
		typeAtTheTop("; mine\n");
		permit(src(), READ_ONLY);

		try {
			assumeFalse(Files.isWritable(src()), "a folder that cannot be written is writable to this user");
			page().click("[data-window=close]");

			final Locator save = page().locator(CLOSING + " button:text-is('Save all and close')");

			save.click();
			assertThat(page().locator(CLOSING + " .modal__line")).containsText("main.s could not be saved");
			assertThat(save).isEnabled();
			assertFalse(rig().window().commands().contains(CLOSE));
		} finally {
			permit(src(), WRITABLE);
		}

		page().click(CLOSING + " button:text-is('Save all and close')");
		await("the window closed", () -> rig().window().commands().contains(CLOSE));
		assertTrue(onDisk().startsWith("; mine\n"));
	}
}
