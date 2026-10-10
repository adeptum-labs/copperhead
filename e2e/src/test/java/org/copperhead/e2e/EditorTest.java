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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.regex.Pattern;
import org.copperhead.toolchain.ProjectDirectory;

/** What the editor tests share: the demo's entry source on disk, the editor's text and what is typed into it. */
public abstract class EditorTest extends BrowserTest {
	protected static final String ENTRY = "main.s";
	protected static final String TEXT = "#tool [data-editor] .cm-content";
	protected static final String UNSAVED = "#tool .tool__tab--active .tool__mark";
	protected static final String BREAKPOINTS = "#tool .cm-breakpoint-gutter .code__dot--on";
	protected static final String CLOSING = "dialog.closing";
	protected static final String CLOSE = "close";
	protected static final Pattern ON = Pattern.compile("(^|\\s)tool__mark--on(\\s|$)");
	protected static final int THIRD_LINE = 2;

	private static final long LATER_MILLIS = 5_000;

	protected Path entry() {
		return rig().project().dir().resolve("src").resolve(ENTRY);
	}

	protected String onDisk() {
		try {
			return Files.readString(entry());
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/* The project file as it is on disk, not as the session holds it. */
	protected boolean breakpointKept(final int line) {
		try {
			return ProjectDirectory.open(rig().project().dir()).file().breakpoints().stream()
				.anyMatch(breakpoint -> breakpoint.line() == line);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/* Another program writes the file; its time of change moves on clearly, as a later save's would. */
	protected void changeElsewhere(final String text) throws IOException {
		final long before = Files.getLastModifiedTime(entry()).toMillis();

		Files.writeString(entry(), text);
		Files.setLastModifiedTime(entry(), FileTime.fromMillis(before + LATER_MILLIS));
		page().evaluate("window.dispatchEvent(new Event('focus'))");
	}

	protected String firstLine() {
		return (String) page().evaluate("document.querySelector('#tool [data-editor] .cm-line').textContent");
	}

	/* The caret goes to the start of the text, so whatever is typed next begins the first line. */
	protected void typeAtTheTop(final String text) {
		page().click(TEXT);
		page().keyboard().press("Control+Home");
		page().keyboard().type(text);
	}
}
