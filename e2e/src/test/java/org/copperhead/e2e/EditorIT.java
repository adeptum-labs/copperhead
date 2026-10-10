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
import net.jqwik.api.Example;
import org.copperhead.ui.StubTools;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The Code tool's editor over the demo the studio opened, with the stand-in tools to build it. */
public class EditorIT extends EditorTest {
	@Example
	public void shouldShowTheEntrySourceAsItIsOnDisk() {
		assertThat(page().locator("#tool .tool__tab--active")).hasText(ENTRY);
		assertEquals(onDisk().lines().findFirst().orElseThrow(), firstLine());
		assertThat(page().locator(UNSAVED)).not().hasClass(ON);
	}

	@Example
	public void shouldMarkTheTabOnceTheTextIsChanged() {
		typeAtTheTop("; note");

		assertThat(page().locator(UNSAVED)).hasClass(ON);
	}

	@Example
	public void shouldWriteTheFileWithCtrlS() {
		typeAtTheTop("; saved by the editor\n");
		page().keyboard().press("Control+s");

		await("the file written", () -> onDisk().startsWith("; saved by the editor\n"));
		assertThat(page().locator(UNSAVED)).not().hasClass(ON);
	}

	@Example
	public void shouldWriteEveryChangedFileWithCtrlShiftS() {
		typeAtTheTop("; saved with the rest\n");
		page().keyboard().press("Control+Shift+S");

		await("the file written", () -> onDisk().startsWith("; saved with the rest\n"));
		assertThat(page().locator(UNSAVED)).not().hasClass(ON);
	}

	@Example
	public void shouldTakeBackWhatWasTypedWithCtrlZ() {
		final String first = firstLine();

		typeAtTheTop("abc");
		page().keyboard().press("Control+z");

		assertEquals(first, firstLine());
		assertThat(page().locator(UNSAVED)).not().hasClass(ON);
	}

	@Example
	public void shouldOpenTheSearchWithCtrlF() {
		page().click(TEXT);
		page().keyboard().press("Control+f");

		assertThat(page().locator("#tool .cm-search input[name=search]")).isFocused();
	}

	@Example
	public void shouldKeepABreakpointSetInTheGutterInTheProject() {
		page().locator("#tool .cm-breakpoint-gutter .cm-gutterElement").nth(THIRD_LINE).click();

		assertThat(page().locator(BREAKPOINTS)).hasCount(1);
		await("the breakpoint kept in the project", () -> breakpointKept(THIRD_LINE + 1));
	}

	/* F9 is the menu's key: the editor leaves it to the studio, which takes the caret's line. */
	@Example
	public void shouldToggleTheBreakpointOfTheCaretsLineWithF9() {
		page().click(TEXT);
		page().keyboard().press("Control+Home");
		page().keyboard().press("ArrowDown");
		page().keyboard().press("F9");

		assertThat(page().locator(BREAKPOINTS)).hasCount(1);
		await("the breakpoint kept in the project", () -> breakpointKept(2));

		page().keyboard().press("F9");

		assertThat(page().locator(BREAKPOINTS)).hasCount(0);
		await("the breakpoint taken out of the project", () -> !breakpointKept(2));
	}

	@Example
	public void shouldShowAFileChangedOnDiskWhileUnchangedHere() throws Exception {
		changeElsewhere("; changed elsewhere\n" + onDisk());

		await("the file read again", () -> "; changed elsewhere".equals(firstLine()));
		assertThat(page().locator(UNSAVED)).not().hasClass(ON);
	}

	@Example
	public void shouldAskWhichTextStaysWhenBothChanged() throws Exception {
		final String first = firstLine();

		typeAtTheTop("; mine");
		changeElsewhere("; theirs\n" + onDisk());

		final Locator question = page().locator("#tool .cm-conflict");

		assertThat(question).containsText(ENTRY + " was changed on disk");
		question.locator("button:text-is('Keep mine')").click();

		assertThat(question).hasCount(0);
		assertEquals("; mine" + first, firstLine());
		assertThat(page().locator(UNSAVED)).hasClass(ON);
	}

	@Example
	public void shouldAskBeforeTheWindowClosesOnUnsavedText() throws Exception {
		typeAtTheTop("; not saved");
		page().click("[data-window=close]");
		page().click(CLOSING + " button:text-is('Cancel')");

		assertThat(page().locator(CLOSING)).hasCount(0);
		assertFalse(rig().window().commands().contains(CLOSE));

		page().click("[data-window=close]");
		page().click(CLOSING + " button:text-is('Close without saving')");

		await("the window closed", () -> rig().window().commands().contains(CLOSE));
		assertFalse(onDisk().startsWith("; not saved"));
	}

	@Example
	public void shouldSaveEverythingBeforeTheWindowClosesWhenAsked() {
		typeAtTheTop("; saved on the way out\n");
		page().click("[data-window=close]");
		page().click(CLOSING + " button:text-is('Save all and close')");

		await("the window closed", () -> rig().window().commands().contains(CLOSE));
		assertEquals("; saved on the way out", onDisk().lines().findFirst().orElseThrow());
	}

	@Example
	public void shouldUnderlineABuildErrorAndShowItFromTheStatusBar() {
		typeAtTheTop("\t" + StubTools.ERROR + " d0\n");
		awaitCaretHeard(2);
		page().keyboard().press("Control+b");

		assertThat(page().locator("#tool .cm-lintRange-error")).isVisible();

		page().locator("#status .status__error").click();

		assertThat(page().locator("#tool .cm-activeLine")).containsText(StubTools.ERROR + " d0");
		assertThat(page().locator(TEXT)).isFocused();
	}

	@Example
	public void shouldSaveUnsavedTextBeforeBuilding() {
		typeAtTheTop("; built as typed\n");
		awaitCaretHeard(2);
		page().keyboard().press("Control+b");

		await("the unsaved text written", () -> onDisk().startsWith("; built as typed\n"));
		assertThat(page().locator("#status .status__note")).hasText("Built demo.adf · 7 bytes");
		assertThat(page().locator(UNSAVED)).not().hasClass(ON);
	}
}
