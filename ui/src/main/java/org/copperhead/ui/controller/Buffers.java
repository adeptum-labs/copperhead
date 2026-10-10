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

package org.copperhead.ui.controller;

import java.util.List;
import java.util.Optional;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.web.action.Form;

/**
 * The unsaved buffers the page sends with an action that must not lose them, a build or a save of them all: their
 * names under {@code files}, separated by commas, and each one's text under its own name.
 */
final class Buffers {
	static final String FILES = "files";

	private static final String SEPARATOR = ",";

	private Buffers() {
	}

	static List<String> names(final Form form) {
		return form.has(FILES) ? List.of(form.get(FILES).split(SEPARATOR)) : List.of();
	}

	/** Writes every buffer sent; a name the project does not hold refuses them all before one is written. */
	static List<String> save(final ProjectDirectory directory, final Form form) {
		final List<String> names = names(form);

		names.forEach(name -> text(form, Workspaces.source(directory, name)));
		names.forEach(name -> Workspaces.write(() -> directory.write(name, text(form, name))));
		return names;
	}

	private static String text(final Form form, final String name) {
		return Optional.ofNullable(form.get(name))
			.orElseThrow(() -> new IllegalArgumentException("No text came for " + name));
	}
}
