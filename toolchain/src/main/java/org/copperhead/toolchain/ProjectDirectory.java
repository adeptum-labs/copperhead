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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.copperhead.core.project.ProjectFile;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.ProjectLayout;
import org.copperhead.core.project.Target;

/** A project on disk: its folder and the project file read from it. Sources are only ever named, never pathed. */
public record ProjectDirectory(Path dir, ProjectFile file) {
	private static final String PROJECT_FILE = ProjectLayout.chp("");
	private static final String STAGED = ".tmp";
	private static final int LARGEST_PROJECT_FILE = 1 << 20;

	public static ProjectDirectory create(final Path parent, final String name, final ProjectKind kind,
		final Target target) throws IOException {

		final Path dir = parent.resolve(name);

		if (!ProjectLayout.validName(name)) {
			throw new IOException("not a project name: " + name);
		}
		/* An empty folder is taken as it is, and a link to one would have the template written wherever it leads. */
		if (Files.isSymbolicLink(dir)) {
			throw new IOException(dir + " is a link");
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

		return new ProjectDirectory(chp.toAbsolutePath().getParent(), ProjectFile.parse(projectText(chp)));
	}

	/* A project file is a few hundred bytes. A cloned project may hold a link, a pipe or gigabytes under its name, so
	   anything but a plain file of a sane size is refused, and never followed or read whole. */
	private static String projectText(final Path chp) throws IOException {
		if (!Files.readAttributes(chp, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile()) {
			throw new IOException(chp.getFileName() + " is not a plain file");
		}

		try (InputStream in = Files.newInputStream(chp, LinkOption.NOFOLLOW_LINKS)) {
			final byte[] bytes = in.readNBytes(LARGEST_PROJECT_FILE + 1);

			if (bytes.length > LARGEST_PROJECT_FILE) {
				throw new IOException(chp.getFileName() + " is larger than " + LARGEST_PROJECT_FILE
					+ " bytes");
			}
			return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
		}
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

		return sourceFiles().stream().filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
			.map(path -> path.getFileName().toString()).filter(ProjectLayout::validSource)
			.sorted(entryFirst.thenComparing(Comparator.naturalOrder())).toList();
	}

	/* The bare path of a missing folder tells the person nothing about what is wrong with the project. */
	private List<Path> sourceFiles() throws IOException {
		try {
			return listed(sourceFolder());
		} catch (final NoSuchFileException e) {
			throw new IOException("the project has no " + ProjectLayout.SRC + " folder", e);
		} catch (final NotDirectoryException e) {
			throw new IOException(ProjectLayout.SRC + " of the project is not a folder", e);
		}
	}

	public String read(final String source) throws IOException {
		return Files.readString(source(source));
	}

	public void write(final String source, final String text) throws IOException {
		replace(source(source), text.getBytes(StandardCharsets.UTF_8));
	}

	public long modified(final String source) throws IOException {
		return Files.getLastModifiedTime(source(source)).toMillis();
	}

	public ProjectDirectory save(final ProjectFile updated) throws IOException {
		final ProjectDirectory saved = new ProjectDirectory(dir, updated);

		replace(saved.chp(), updated.toJson().getBytes(StandardCharsets.UTF_8));
		return saved;
	}

	private Path source(final String name) throws IOException {
		if (!ProjectLayout.validSource(name)) {
			throw new IOException("not a source: " + name);
		}

		final Path path = sourceFolder().resolve(name);

		if (Files.isSymbolicLink(path)) {
			throw new IOException(name + " is a link");
		}
		return path;
	}

	/* A cloned project can plant a link here, and the sources would then be read and written elsewhere. */
	private Path sourceFolder() throws IOException {
		final Path folder = dir.resolve(ProjectLayout.SRC);

		if (Files.isSymbolicLink(folder)) {
			throw new IOException("src is a link");
		}
		return folder;
	}

	/* Written beside the file and moved over it in one step, so a crash or a full disk leaves the old content whole.
	   The staging name is new every time and never reused, so a link planted under a guessed name is not followed. */
	static void replace(final Path file, final byte[] content) throws IOException {
		final Path staged = file.resolveSibling("." + file.getFileName() + "." + UUID.randomUUID() + STAGED);

		try {
			Files.write(staged, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
				LinkOption.NOFOLLOW_LINKS);
			Files.move(staged, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (FileSystemException e) {
			/* The exception names the staging file, which the person never saw and which is gone by now. */
			throw new IOException(Optional.ofNullable(e.getReason())
				.orElseGet(() -> e.getClass().getSimpleName()), e);
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
