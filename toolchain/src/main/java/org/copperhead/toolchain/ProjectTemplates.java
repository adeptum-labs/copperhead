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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.copperhead.core.project.ProjectKind;

/**
 * The sources a new project starts with: a demo that rolls copper bars, or a game that steers a sprite. Both
 * assemble as they are and boot from the disk the build makes. Each text holds {@link #NAME} where the project's
 * name belongs.
 */
public final class ProjectTemplates {
	public static final String NAME = "@NAME@";
	private static final Map<ProjectKind, List<String>> FILES = Map.of(ProjectKind.DEMO, List.of("main.s"),
		ProjectKind.GAME, List.of("main.s", "sprite.i"));

	private ProjectTemplates() {
	}

	public static Map<String, String> sources(final ProjectKind kind) {
		final Map<String, String> sources = new LinkedHashMap<>();

		FILES.get(kind).forEach(file -> sources.put(file, text(kind, file)));
		return sources;
	}

	private static String text(final ProjectKind kind, final String file) {
		final String resource = "/templates/" + kind.name().toLowerCase(Locale.ROOT) + "/src/" + file;

		try (InputStream in = ProjectTemplates.class.getResourceAsStream(resource)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (final IOException e) {
			throw new UncheckedIOException("The template " + resource + " ships inside the jar", e);
		}
	}
}
