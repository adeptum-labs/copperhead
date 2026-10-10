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

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class PreferencesTest {
	private static final String UNREADABLE = "preferences were unreadable; moved to preferences.json.bak";
	private static final String SHA = "feedface".repeat(8);
	private static final String BAK = "preferences.json.bak";
	private static final String VICTIM_TEXT = "{\"theme\": \"light\"}";

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
		preferences.trustedVasm(SHA);
		preferences.used(chp);

		final Preferences read = new Preferences(file);

		assertEquals(new Layout("light", 40, 300), read.layout());
		assertEquals(Optional.of(Path.of("/opt/vasm/vasmm68k_mot")), read.tool("vasm"));
		assertEquals(Optional.empty(), read.tool("shrinkler"));
		assertEquals(Optional.of(SHA), read.trustedVasm());
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

	private Path plant(final String text) throws Exception {
		Files.createDirectories(file.getParent());
		return Files.writeString(file, text);
	}

	private Path victim() throws Exception {
		return Files.writeString(folder.path().resolve("victim"), VICTIM_TEXT);
	}

	private void assertSetAside(final String text) throws Exception {
		plant(text);

		final Preferences preferences = new Preferences(file);

		assertEquals(Layout.defaults(), preferences.layout());
		assertEquals(Optional.empty(), preferences.trustedVasm());
		assertEquals(Optional.empty(), preferences.tool("vasm"));
		assertEquals(List.of(), preferences.recent());
		assertEquals(Optional.of(UNREADABLE), preferences.note());
		assertEquals(text, Files.readString(file.resolveSibling(BAK)));
		assertFalse(Files.exists(file));
	}

	@Example
	public void shouldSetAsideAChecksumThatIsNotHex() throws Exception {
		assertSetAside("{\"trustedVasm\": \"not a checksum\"}");
	}

	@Example
	public void shouldSetAsideAChecksumThatIsAPath() throws Exception {
		assertSetAside("{\"trustedVasm\": \"../../x\"}");
	}

	@Example
	public void shouldSetAsideAChecksumInUpperCase() throws Exception {
		assertSetAside("{\"trustedVasm\": \"" + SHA.toUpperCase() + "\"}");
	}

	@Example
	public void shouldSetAsideAChecksumOfTheWrongLength() throws Exception {
		assertSetAside("{\"trustedVasm\": \"" + SHA + "0\"}");
	}

	@Example
	public void shouldKeepAChecksumInLowerCase() throws Exception {
		final Preferences preferences = new Preferences(file);

		preferences.trustedVasm(SHA.toUpperCase());

		assertEquals(Optional.of(SHA), preferences.trustedVasm());
		assertEquals(Optional.of(SHA), new Preferences(file).trustedVasm());
	}

	@Example
	public void shouldRefuseAChecksumThatIsNotHexAndWriteNothing() {
		final Preferences preferences = new Preferences(file);

		assertThrows(IllegalArgumentException.class, () -> preferences.trustedVasm("../../x"));
		assertThrows(IllegalArgumentException.class, () -> preferences.trustedVasm(SHA.substring(1)));

		assertEquals(Optional.empty(), preferences.trustedVasm());
		assertFalse(Files.exists(file));
	}

	@Example
	public void shouldRefuseARelativeToolPathAndWriteNothing() {
		final Preferences preferences = new Preferences(file);

		assertThrows(IllegalArgumentException.class,
			() -> preferences.tool("vasm", Optional.of(Path.of("bin/vasmm68k_mot"))));

		assertEquals(Optional.empty(), preferences.tool("vasm"));
		assertFalse(Files.exists(file));
	}

	@Example
	public void shouldKeepAToolPathNormalised() {
		final Preferences preferences = new Preferences(file);

		preferences.tool("vasm", Optional.of(Path.of("/opt/x/../vasm/vasmm68k_mot")));

		assertEquals(Optional.of(Path.of("/opt/vasm/vasmm68k_mot")), new Preferences(file).tool("vasm"));
	}

	@Example
	public void shouldSetAsideAToolPathThatIsRelative() throws Exception {
		assertSetAside("{\"tools\": {\"vasm\": \"bin/vasmm68k_mot\"}}");
	}

	@Example
	public void shouldSetAsideAToolPathThatIsANumber() throws Exception {
		assertSetAside("{\"tools\": {\"vasm\": 7}}");
	}

	@Example
	public void shouldKeepProjectsAsAbsolutePathsWithNoDotDotAndNoRepeats() throws Exception {
		final Path chp = project("x");

		final Path winding = chp.getParent().resolve("..").resolve("x").resolve("x.chp");

		plant("{\"recent\": [\"" + winding + "\", \"" + chp + "\"]}");

		assertEquals(List.of(chp), new Preferences(file).recent());
	}

	@Example
	public void shouldSetAsideAProjectListWithARelativeEntry() throws Exception {
		assertSetAside("{\"recent\": [\"" + project("x") + "\", \"x/x.chp\"]}");
	}

	@Example
	public void shouldSetAsideAProjectListWithAnEntryThatIsNotText() throws Exception {
		assertSetAside("{\"recent\": [3]}");
	}

	@Example
	public void shouldReadNoMoreThanTheEightProjectsOpenedLast() throws Exception {
		final List<Path> projects = new ArrayList<>();

		for (int index = 0; index < 20; index++) {
			projects.add(project("p" + index));
		}

		plant("{\"recent\": [" + projects.stream().map(path -> "\"" + path + "\"")
			.collect(Collectors.joining(", ")) + "]}");

		assertEquals(projects.subList(0, 8), new Preferences(file).recent());
	}

	@Example
	public void shouldSetAsideAThemeThatIsNotText() throws Exception {
		assertSetAside("{\"theme\": 5}");
	}

	@Example
	public void shouldSetAsideASizeBeyondTheRangeOfAnInt() throws Exception {
		assertSetAside("{\"split\": 4294967297}");
	}

	@Example
	public void shouldSetAsideASizeThatIsNotAWholeNumber() throws Exception {
		assertSetAside("{\"bottom\": 250.5}");
	}

	@Example
	public void shouldSetAsideASizeThatIsText() throws Exception {
		assertSetAside("{\"split\": \"wide\"}");
	}

	@Example
	public void shouldTakeTheDefaultForAKeyThatIsMissing() throws Exception {
		plant("{\"split\": 40}");

		final Preferences preferences = new Preferences(file);

		assertEquals(Layout.defaults().withSplit(40), preferences.layout());
		assertEquals(Optional.empty(), preferences.note());
	}

	@Example
	public void shouldSetAsideAFileLargerThanOneMebibyte() throws Exception {
		assertSetAside(" ".repeat(1024 * 1024) + "{}");
	}

	@Example
	public void shouldReadAFileOfExactlyOneMebibyte() throws Exception {
		plant("{}" + " ".repeat(1024 * 1024 - 2));

		assertEquals(Optional.empty(), new Preferences(file).note());
	}

	@Example
	public void shouldSetAsideALinkWithoutReadingOrTouchingWhatItPointsTo() throws Exception {
		final Path victim = victim();

		Files.createDirectories(file.getParent());
		Files.createSymbolicLink(file, victim);

		final Preferences preferences = new Preferences(file);

		assertEquals(Layout.defaults(), preferences.layout());
		assertEquals(Optional.of(UNREADABLE), preferences.note());
		assertFalse(Files.exists(file, LinkOption.NOFOLLOW_LINKS));
		assertTrue(Files.isSymbolicLink(file.resolveSibling(BAK)));
		assertEquals(VICTIM_TEXT, Files.readString(victim));
	}

	@Example
	public void shouldSetAsideADirectoryWhereTheFileShouldBe() throws Exception {
		Files.createDirectories(file);

		assertEquals(Optional.of(UNREADABLE), new Preferences(file).note());
		assertTrue(Files.isDirectory(file.resolveSibling(BAK)));
	}

	@Example
	public void shouldWriteThroughNoLinkAtTheFileItReplaces() throws Exception {
		final Path victim = victim();

		Files.createDirectories(file.getParent());
		Files.createSymbolicLink(file, victim);
		new Preferences(file).layout(Layout.defaults().withSplit(60));

		assertFalse(Files.isSymbolicLink(file));
		assertEquals(VICTIM_TEXT, Files.readString(victim));
	}

	@Example
	public void shouldNotFollowALinkPlantedAtTheNameOfTheFirstFileSetAside() throws Exception {
		final Path victim = victim();

		Files.createDirectories(file.getParent());
		Files.createSymbolicLink(file.resolveSibling(BAK), victim);
		plant("{broken");

		assertEquals(Optional.of(UNREADABLE + ".1"), new Preferences(file).note());
		assertEquals(VICTIM_TEXT, Files.readString(victim));
		assertTrue(Files.isSymbolicLink(file.resolveSibling(BAK)));
		assertEquals("{broken", Files.readString(file.resolveSibling(BAK + ".1")));
	}

	@Example
	public void shouldReplaceALinkPlantedAtTheLastNameSparedWithoutFollowingIt() throws Exception {
		final Path victim = victim();

		Files.createDirectories(file.getParent());
		Files.writeString(file.resolveSibling(BAK), "first");

		for (int number = 1; number < 5; number++) {
			Files.writeString(file.resolveSibling(BAK + "." + number), "older " + number);
		}

		Files.createSymbolicLink(file.resolveSibling(BAK + ".5"), victim);
		plant("{broken");

		assertEquals(Optional.of(UNREADABLE + ".5"), new Preferences(file).note());
		assertEquals(VICTIM_TEXT, Files.readString(victim));
		assertFalse(Files.isSymbolicLink(file.resolveSibling(BAK + ".5")));
		assertEquals("{broken", Files.readString(file.resolveSibling(BAK + ".5")));
		assertEquals("first", Files.readString(file.resolveSibling(BAK)));
	}

	@Example
	public void shouldKeepTheFirstFileSetAsideWhenASecondOneIsDamaged() throws Exception {
		plant("{first");
		new Preferences(file);
		plant("{second");

		assertEquals(Optional.of(UNREADABLE + ".1"), new Preferences(file).note());
		assertEquals("{first", Files.readString(file.resolveSibling(BAK)));
		assertEquals("{second", Files.readString(file.resolveSibling(BAK + ".1")));
	}

	@Example
	public void shouldKeepTheFileReadableByItsOwnerOnly() throws Exception {
		assumeTrue(folder.path().getFileSystem().supportedFileAttributeViews().contains("posix"),
			"File modes need a POSIX file system");

		new Preferences(file).layout(Layout.defaults());

		assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
	}

	@Example
	public void shouldKeepTheOldStateWhenTheFileCannotBeWritten() throws Exception {
		assumeTrue(folder.path().getFileSystem().supportedFileAttributeViews().contains("posix"),
			"Read-only folders need a POSIX file system");

		final Preferences preferences = new Preferences(file);
		final Layout before = Layout.defaults().withSplit(60);

		preferences.layout(before);
		Files.setPosixFilePermissions(file.getParent(), PosixFilePermissions.fromString("r-xr-xr-x"));

		try {
			assumeFalse(Files.isWritable(file.getParent()), "Root can write to a folder whatever its mode");
			assertThrows(UncheckedIOException.class, () -> preferences.layout(before.withTheme("light")));
			assertThrows(UncheckedIOException.class, () -> preferences.trustedVasm(SHA));
			assertThrows(UncheckedIOException.class,
				() -> preferences.tool("vasm", Optional.of(Path.of("/opt/vasm"))));
			assertEquals(before, preferences.layout());
			assertEquals(Optional.empty(), preferences.trustedVasm());
			assertEquals(Optional.empty(), preferences.tool("vasm"));
			assertEquals(before, new Preferences(file).layout());
		} finally {
			Files.setPosixFilePermissions(file.getParent(), PosixFilePermissions.fromString("rwxr-xr-x"));
		}
	}
}
