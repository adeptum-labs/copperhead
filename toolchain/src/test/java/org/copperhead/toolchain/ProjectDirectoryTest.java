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

package org.copperhead.toolchain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.FileTime;
import java.util.List;
import net.jqwik.api.Example;
import org.copperhead.core.debug.Breakpoint;
import org.copperhead.core.project.ProjectFile;
import org.copperhead.core.project.ProjectFormatException;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.Target;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProjectDirectoryTest extends ScratchTest {
	private static final long NOON = 1_791_547_200_000L;

	@Example
	public void shouldCreateTheFoldersTheProjectFileAndTheTemplate() throws IOException {
		final ProjectDirectory project = demo();

		assertEquals(scratch().resolve("nightrun"), project.dir());
		assertAll(List.of("src", "gfx", "build").stream()
			.map(folder -> () -> assertTrue(Files.isDirectory(project.dir().resolve(folder)), folder)));
		assertEquals(scratch().resolve("nightrun/nightrun.chp"), project.chp());
		assertEquals(scratch().resolve("nightrun/build/nightrun.adf"), project.adf());
		assertEquals(ProjectFile.create("nightrun", ProjectKind.DEMO, Target.A500_OCS).toJson(),
			Files.readString(project.chp()));
		assertTrue(project.read("main.s").startsWith("; nightrun - copper bars\n"));
		assertFalse(project.read("main.s").contains(ProjectTemplates.NAME));
	}

	@Example
	public void shouldListTheEntryFirstAndTheOtherSourcesByName() throws IOException {
		final ProjectDirectory project = ProjectDirectory.create(scratch(), "shipshape", ProjectKind.GAME,
			Target.A1200_AGA);

		project.write("alpha.i", "; first by name\n");
		Files.writeString(project.dir().resolve("src/notes.txt"), "not a source\n");
		Files.createDirectories(project.dir().resolve("src/older.s"));
		assertEquals(List.of("main.s", "alpha.i", "sprite.i"), project.sources());
	}

	@Example
	public void shouldRefuseToCreateOverAnotherFolderOrUnderABadName() throws IOException {
		Files.createDirectories(scratch().resolve("nightrun"));
		demo();

		assertTrue(assertThrows(IOException.class, this::demo).getMessage()
			.endsWith("already exists and is not empty"));
		assertEquals("not a project name: ../escaped", assertThrows(IOException.class,
			() -> ProjectDirectory.create(scratch(), "../escaped", ProjectKind.DEMO, Target.A500_OCS))
			.getMessage());
		assertFalse(Files.exists(scratch().getParent().resolve("escaped")));
	}

	@Example
	public void shouldOpenByItsProjectFileOrItsFolder() throws IOException {
		final ProjectDirectory created = demo();

		assertEquals(created.dir(), ProjectDirectory.open(created.chp()).dir());
		assertEquals("nightrun", ProjectDirectory.open(created.dir()).file().name());
		Files.createDirectories(scratch().resolve("bare"));
		assertThrows(IOException.class, () -> ProjectDirectory.open(scratch().resolve("bare")));
		Files.writeString(created.chp(), "{ not json");
		assertThrows(ProjectFormatException.class, () -> ProjectDirectory.open(created.chp()));
	}

	@Example
	public void shouldWriteASourceWholeAndKeepItsLineEndings() throws IOException {
		final ProjectDirectory project = demo();

		project.write("main.s", "\tmoveq\t#0,d0\r\n\trts\r\n");
		assertEquals("\tmoveq\t#0,d0\r\n\trts\r\n", project.read("main.s"));
		assertEquals(List.of(project.dir().resolve("src/main.s")), listed(project.dir().resolve("src")));
	}

	@Example
	public void shouldLeaveTheOldTextWhenAWriteCannotFinish() throws IOException {
		final ProjectDirectory project = demo();
		final String before = project.read("main.s");

		final Path src = project.dir().resolve("src");

		Files.setPosixFilePermissions(src, PosixFilePermissions.fromString("r-xr-xr-x"));
		try {
			assertThrows(IOException.class, () -> project.write("main.s", "half"));
		} finally {
			Files.setPosixFilePermissions(src, PosixFilePermissions.fromString("rwxr-xr-x"));
		}
		assertEquals(before, project.read("main.s"));
		assertEquals(List.of(src.resolve("main.s")), listed(src));
	}

	@Example
	public void shouldNotWriteThroughALinkPlantedAtTheOldStagingName() throws IOException {
		final ProjectDirectory project = demo();
		final Path victim = Files.writeString(scratch().resolve("victim"), "victim");

		Files.createSymbolicLink(project.dir().resolve("src/.main.s.tmp"), victim);
		Files.createSymbolicLink(project.dir().resolve(".nightrun.chp.tmp"), victim);
		project.write("main.s", "\trts\n");
		project.save(project.file().withCrunch(false));

		assertEquals("victim", Files.readString(victim));
		assertEquals("\trts\n", project.read("main.s"));
		assertFalse(ProjectDirectory.open(project.chp()).file().crunch());
	}

	@Example
	public void shouldRefuseSourcesWhenTheSourceFolderIsALink() throws IOException {
		final ProjectDirectory project = demo();
		final String before = project.read("main.s");
		final Path elsewhere = Files.move(project.dir().resolve("src"), scratch().resolve("elsewhere"));

		Files.createSymbolicLink(project.dir().resolve("src"), elsewhere);
		assertAll(List.of(() -> assertEquals("src is a link",
				assertThrows(IOException.class, () -> project.read("main.s")).getMessage()),
			() -> assertEquals("src is a link",
				assertThrows(IOException.class, () -> project.write("main.s", "evil")).getMessage()),
			() -> assertEquals("src is a link",
				assertThrows(IOException.class, project::sources).getMessage())));
		assertEquals(before, Files.readString(elsewhere.resolve("main.s")));
	}

	@Example
	public void shouldTakeOnlySourceNamesInsideTheProject() throws IOException {
		final ProjectDirectory project = demo();

		assertAll(List.of("../nightrun.chp", "sub/main.s", "/etc/passwd", "notes.txt", ".hidden.s").stream()
			.map(name -> () -> assertEquals("not a source: " + name,
				assertThrows(IOException.class, () -> project.read(name)).getMessage())));
		assertThrows(IOException.class, () -> project.write("../escaped.s", "evil"));
		assertFalse(Files.exists(project.dir().resolve("escaped.s")));
	}

	@Example
	public void shouldSaveTheProjectFileAndTellWhenASourceChanged() throws IOException {
		final ProjectDirectory project = demo();
		final List<Breakpoint> breakpoints = List.of(new Breakpoint("main.s", 12, 0));
		final ProjectFile updated = project.file().withCrunch(false).withBreakpoints(breakpoints);

		assertEquals(updated, project.save(updated).file());
		assertFalse(ProjectDirectory.open(project.chp()).file().crunch());
		assertEquals(breakpoints, ProjectDirectory.open(project.chp()).file().breakpoints());
		Files.setLastModifiedTime(project.dir().resolve("src/main.s"), FileTime.fromMillis(NOON));
		assertEquals(NOON, project.modified("main.s"));
	}

	private ProjectDirectory demo() throws IOException {
		return ProjectDirectory.create(scratch(), "nightrun", ProjectKind.DEMO, Target.A500_OCS);
	}
}
