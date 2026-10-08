/*
    Copperhead
    Copyright © 2026 Adam Waldenberg

    This program is free software: you can redistribute it and/or modify it under the terms of the
    GNU General Public License as published by the Free Software Foundation, version 3 of the License.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
    even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along with this program.
    If not, see <http://www.gnu.org/licenses/>.
 */

package org.copperhead.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Installs the Debian package the build made the way a person would, and says where its launcher is.
 * Installing changes the computer for good, so only the installed-package test uses it, and that test runs only
 * when the build is started with -Dcopperhead.installed=true.
 */
final class Installation {
	private static final long INSTALL_SECONDS = 600;

	private Installation() {
	}

	static Path install(final Path dist) throws IOException, InterruptedException {
		final Path launcher = Path.of("/opt/copperhead/bin/Copperhead");

		run("sudo", "apt-get", "install", "-y", debianPackage(dist).toString());

		if (!Files.isExecutable(launcher)) {
			throw new IOException("The installation left no launcher at " + launcher);
		}

		return launcher;
	}

	static void uninstall() throws IOException, InterruptedException {
		run("sudo", "apt-get", "remove", "-y", "copperhead");
	}

	private static Path debianPackage(final Path dist) throws IOException {
		try (Stream<Path> files = Files.list(dist)) {
			final List<Path> found = files.filter(file -> file.toString().endsWith(".deb")).toList();

			if (found.size() != 1) {
				throw new IOException("Expected one .deb in " + dist + ", found " + found);
			}

			return found.getFirst();
		}
	}

	private static void run(final String... command) throws IOException, InterruptedException {
		final Process process = new ProcessBuilder(command).inheritIO().start();

		if (!process.waitFor(INSTALL_SECONDS, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new IOException("Timed out: " + String.join(" ", command));
		}

		if (process.exitValue() != 0) {
			throw new IOException("Exit " + process.exitValue() + ": " + String.join(" ", command));
		}
	}
}
