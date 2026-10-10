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

package org.copperhead.ui.session;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.copperhead.ui.TempDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PreferencesTest {
	private static final String UNREADABLE = "preferences were unreadable; moved to preferences.json.bak";

	private TempDirectory folder;
	private Path file;

	@BeforeTry
	public void make() throws Exception {
		folder = new TempDirectory();
		file = folder.path().resolve("copperhead").resolve("preferences.json");
	}

	@AfterTry
	public void remove() throws Exception {
		folder.close();
	}

	private Path project(final String name) throws Exception {
		final Path chp = folder.path().resolve(name).resolve(name + ".chp");

		Files.createDirectories(chp.getParent());
		return Files.writeString(chp, "{}");
	}

	@Example
	public void shouldStartFromTheDefaultsWithoutWritingAnything() {
		final Preferences preferences = new Preferences(file);

		assertEquals(Layout.defaults(), preferences.layout());
		assertEquals(Optional.empty(), preferences.tool("vasm"));
		assertEquals(Optional.empty(), preferences.trustedVasm());
		assertEquals(List.of(), preferences.recent());
		assertEquals(Optional.empty(), preferences.note());
		assertFalse(Files.exists(file));
	}

	@Example
	public void shouldReadBackWhatItWrote() throws Exception {
		final Preferences preferences = new Preferences(file);
		final Path chp = project("demo");

		preferences.layout(new Layout("light", 40, 300));
		preferences.tool("vasm", Optional.of(Path.of("/opt/vasm/vasmm68k_mot")));
		preferences.trustedVasm("feedface");
		preferences.used(chp);

		final Preferences read = new Preferences(file);

		assertEquals(new Layout("light", 40, 300), read.layout());
		assertEquals(Optional.of(Path.of("/opt/vasm/vasmm68k_mot")), read.tool("vasm"));
		assertEquals(Optional.empty(), read.tool("shrinkler"));
		assertEquals(Optional.of("feedface"), read.trustedVasm());
		assertEquals(List.of(chp), read.recent());
	}

	/* A file moved over the old one is a new file; one written in place would keep the old one's identity. */
	@Example
	public void shouldReplaceTheFileWholeAndLeaveNothingElseBehind() throws Exception {
		final Preferences preferences = new Preferences(file);

		preferences.layout(Layout.defaults());

		final Object before = Files.readAttributes(file, BasicFileAttributes.class).fileKey();

		preferences.layout(Layout.defaults().withSplit(60));

		try (Stream<Path> inside = Files.list(file.getParent())) {
			assertEquals(List.of(file), inside.toList());
		}

		assertNotEquals(before, Files.readAttributes(file, BasicFileAttributes.class).fileKey());
		assertEquals(60, new Preferences(file).layout().split());
	}

	@Example
	public void shouldForgetAToolWhenItsPathIsCleared() {
		final Preferences preferences = new Preferences(file);

		preferences.tool("shrinkler", Optional.of(Path.of("/opt/shrinkler")));
		preferences.tool("shrinkler", Optional.empty());

		assertEquals(Optional.empty(), new Preferences(file).tool("shrinkler"));
	}

	@Example
	public void shouldRefuseAToolItDoesNotBuildWith() {
		assertThrows(IllegalArgumentException.class, () -> new Preferences(file).tool("make"));
	}

	@Example
	public void shouldSetAnUnreadableFileAsideAndStartAfresh() throws Exception {
		Files.createDirectories(file.getParent());
		Files.writeString(file, "{\"theme\": \"light\", ");

		final Preferences preferences = new Preferences(file);

		assertEquals(Layout.defaults(), preferences.layout());
		assertEquals("{\"theme\": \"light\", ", Files.readString(file.resolveSibling("preferences.json.bak")));
		assertFalse(Files.exists(file));
		assertEquals(Optional.of(UNREADABLE), preferences.note());
		assertEquals(Optional.empty(), preferences.note());
	}

	@Example
	public void shouldSetAsideAFileWithAThemeItDoesNotHave() throws Exception {
		Files.createDirectories(file.getParent());
		Files.writeString(file, "{\"theme\": \"purple\"}");

		assertEquals(Optional.of(UNREADABLE), new Preferences(file).note());
		assertTrue(Files.exists(file.resolveSibling("preferences.json.bak")));
	}

	@Example
	public void shouldListTheProjectsOpenedLastNewestFirst() throws Exception {
		final Preferences preferences = new Preferences(file);
		final Path first = project("first");
		final Path second = project("second");

		preferences.used(first);
		preferences.used(second);
		preferences.used(first);

		assertEquals(List.of(first, second), preferences.recent());
	}

	@Example
	public void shouldKeepTheEightProjectsOpenedLast() throws Exception {
		final Preferences preferences = new Preferences(file);
		final List<Path> projects = new ArrayList<>();

		for (int index = 0; index < 10; index++) {
			projects.add(project("p" + index));
		}

		projects.forEach(preferences::used);

		assertEquals(projects.reversed().subList(0, 8), new Preferences(file).recent());
	}

	@Example
	public void shouldLeaveOutAProjectThatIsGone() throws Exception {
		final Preferences preferences = new Preferences(file);
		final Path kept = project("kept");
		final Path gone = project("gone");

		preferences.used(kept);
		preferences.used(gone);
		Files.delete(gone);

		assertEquals(List.of(kept), preferences.recent());
		assertEquals(List.of(kept), new Preferences(file).recent());
	}
}
