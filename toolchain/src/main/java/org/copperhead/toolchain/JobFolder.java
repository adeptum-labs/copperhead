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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.stream.Stream;
import org.copperhead.core.project.ProjectLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A folder made for one build, where the tools write. An assembler directive can redirect its output to any path
 * that resolves from the output name, so the output goes where nothing can have been planted: a folder that did not
 * exist a moment ago and that only this build can enter.
 */
final class JobFolder implements AutoCloseable {
	private static final Logger LOG = LoggerFactory.getLogger(JobFolder.class);
	private static final String PREFIX = ".job-";

	private final Path dir;
	private final String name;

	private JobFolder(final Path dir, final String name) {
		this.dir = dir;
		this.name = name;
	}

	static JobFolder create(final ProjectDirectory project) throws IOException {
		final Path build = Files.createDirectories(project.dir().resolve(ProjectLayout.BUILD));
		final Path dir = Files.createTempDirectory(build, PREFIX,
			PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));

		return new JobFolder(dir, ProjectLayout.BUILD + "/" + dir.getFileName());
	}

	/** The path of a file in the folder, relative to the project, as the tools are given it. */
	String path(final String file) {
		return name + "/" + file;
	}

	/**
	 * Moves the outputs the tool made to their places in the project, over whatever was there. Anything else in the
	 * folder means the tool wrote where it was not asked to.
	 */
	void collect(final ProjectDirectory project, final Map<String, String> outputs)
		throws IOException, ToolchainException {

		try (Stream<Path> found = Files.list(dir)) {
			for (final String entry : found.map(path -> path.getFileName().toString()).toList()) {
				if (!outputs.containsKey(entry)) {
					throw new ToolchainException("the tool left " + path(entry));
				}
			}
		}
		for (final Map.Entry<String, String> output : outputs.entrySet()) {
			final Path made = dir.resolve(output.getKey());
			final Path destination = project.dir().resolve(output.getValue());

			if (Files.exists(made, LinkOption.NOFOLLOW_LINKS)) {
				if (!Files.isRegularFile(made, LinkOption.NOFOLLOW_LINKS)) {
					throw new ToolchainException(path(output.getKey()) + " is not a file");
				}
				Files.deleteIfExists(destination);
				Files.move(made, destination);
			}
		}
	}

	@Override
	public void close() {
		try {
			Files.walkFileTree(dir, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes)
					throws IOException {

					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(final Path folder, final IOException failure)
					throws IOException {

					Files.delete(folder);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (final IOException e) {
			LOG.warn("Could not remove {}", dir, e);
		}
	}
}
