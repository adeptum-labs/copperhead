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
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.copperhead.core.project.ProjectFile;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.ProjectLayout;
import org.copperhead.core.project.Target;

/** A project on disk: its folder and the project file read from it. Sources are only ever named, never pathed. */
public record ProjectDirectory(Path dir, ProjectFile file) {
	private static final String PROJECT_FILE = ProjectLayout.chp("");
	private static final String STAGED = ".tmp";

	public static ProjectDirectory create(final Path parent, final String name, final ProjectKind kind,
		final Target target) throws IOException {

		final Path dir = parent.resolve(name);

		if (!ProjectLayout.validName(name)) {
			throw new IOException("not a project name: " + name);
		}
		if (Files.exists(dir) && (!Files.isDirectory(dir) || !listed(dir).isEmpty())) {
			throw new IOException(dir + " already exists and is not empty");
		}
		for (final String folder : List.of(ProjectLayout.SRC, ProjectLayout.GFX, ProjectLayout.BUILD)) {
			Files.createDirectories(dir.resolve(folder));
		}
		final ProjectDirectory project = new ProjectDirectory(dir, ProjectFile.create(name, kind, target));

		for (final Map.Entry<String, String> source : ProjectTemplates.sources(kind).entrySet()) {
			project.write(source.getKey(), source.getValue().replace(ProjectTemplates.NAME, name));
		}
		return project.save(project.file());
	}

	public static ProjectDirectory open(final Path chpOrDirectory) throws IOException {
		final Path chp = Files.isDirectory(chpOrDirectory) ? projectFile(chpOrDirectory) : chpOrDirectory;

		return new ProjectDirectory(chp.toAbsolutePath().getParent(), ProjectFile.parse(Files.readString(chp)));
	}

	public Path chp() {
		return dir.resolve(ProjectLayout.chp(file.name()));
	}

	public Path adf() {
		return dir.resolve(ProjectLayout.BUILD).resolve(ProjectLayout.adf(file.name()));
	}

	public List<String> sources() throws IOException {
		final String entry = Path.of(file.entry()).getFileName().toString();
		final Comparator<String> entryFirst = Comparator.comparing(name -> !name.equals(entry));

		return listed(dir.resolve(ProjectLayout.SRC)).stream().filter(Files::isRegularFile)
			.map(path -> path.getFileName().toString()).filter(ProjectLayout::validSource)
			.sorted(entryFirst.thenComparing(Comparator.naturalOrder())).toList();
	}

	public String read(final String source) throws IOException {
		return Files.readString(source(source));
	}

	public void write(final String source, final String text) throws IOException {
		replace(source(source), text);
	}

	public long modified(final String source) throws IOException {
		return Files.getLastModifiedTime(source(source)).toMillis();
	}

	public ProjectDirectory save(final ProjectFile updated) throws IOException {
		final ProjectDirectory saved = new ProjectDirectory(dir, updated);

		replace(saved.chp(), updated.toJson());
		return saved;
	}

	private Path source(final String name) throws IOException {
		if (!ProjectLayout.validSource(name)) {
			throw new IOException("not a source: " + name);
		}
		return dir.resolve(ProjectLayout.SRC).resolve(name);
	}

	/* Written beside the file and moved over it in one step, so a crash or a full disk leaves the old text whole. */
	private static void replace(final Path file, final String text) throws IOException {
		final Path staged = file.resolveSibling("." + file.getFileName() + STAGED);

		try {
			Files.writeString(staged, text);
			Files.move(staged, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(staged);
		}
	}

	private static Path projectFile(final Path directory) throws IOException {
		final List<Path> chps = listed(directory).stream()
			.filter(path -> path.getFileName().toString().endsWith(PROJECT_FILE)).toList();

		if (chps.size() != 1) {
			throw new IOException("expected one project file in " + directory + ", found " + chps.size());
		}
		return chps.getFirst();
	}

	private static List<Path> listed(final Path directory) throws IOException {
		try (Stream<Path> found = Files.list(directory)) {
			return found.toList();
		}
	}
}
