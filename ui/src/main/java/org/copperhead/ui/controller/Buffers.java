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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.copperhead.core.project.ProjectLayout;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.web.action.Form;

/**
 * The unsaved buffers the page sends with an action that must not lose them, a build or a save of them all: their
 * names under {@code files}, separated by commas, and each one's text under its own name, together with the folder
 * of the project they were typed in.
 */
final class Buffers {
	static final String FILES = "files";

	private static final String PROJECT = "project";
	private static final String SEPARATOR = ",";

	private Buffers() {
	}

	static List<String> names(final Form form) {
		return form.has(FILES) ? List.of(form.get(FILES).split(SEPARATOR)) : List.of();
	}

	/**
	 * Refuses text typed in another project than the one open: sources are named alike in every project, so text
	 * that crossed over would overwrite a file of this one without a name to tell it apart.
	 */
	static void owned(final ProjectDirectory directory, final Form form) {
		if (!directory.dir().toString().equals(form.get(PROJECT))) {
			throw new IllegalArgumentException("These changes belong to another project");
		}
	}

	/** A disk that refused one buffer; the ones before it stay written, and the message says so. */
	static final class Failed extends RuntimeException {
		private static final long serialVersionUID = 1L;

		private final transient List<String> saved;

		Failed(final String name, final List<String> saved, final IOException cause) {
			super(name + " could not be saved: " + Workspaces.reason(cause)
				+ (saved.isEmpty() ? "" : "; " + String.join(", ", saved) + " already saved"), cause);
			this.saved = List.copyOf(saved);
		}

		/** The buffers written before the refused one, which the disk holds. */
		List<String> saved() {
			return saved;
		}
	}

	/**
	 * Writes every buffer sent; a name that is no source's, or one with no text, refuses them all before one is
	 * written. A write the disk refuses stops the rest and reports what was saved before it. A source the project
	 * no longer holds is written anew, which is how text typed into one that was deleted meanwhile is kept.
	 */
	static List<String> save(final ProjectDirectory directory, final Form form) {
		final List<String> names = names(form);

		if (!names.isEmpty()) {
			owned(directory, form);
		}

		return save(directory, names, form::get);
	}

	/** The same for the names given, each one's text asked of {@code texts}, which answers null for none. */
	static List<String> save(final ProjectDirectory directory, final List<String> names,
		final UnaryOperator<String> texts) {

		final List<String> saved = new ArrayList<>();

		names.forEach(name -> text(texts, source(name)));

		for (final String name : names) {
			try {
				directory.write(name, text(texts, name));
				saved.add(name);
			} catch (IOException e) {
				throw new Failed(name, saved, e);
			}
		}

		return saved;
	}

	private static String source(final String name) {
		if (!ProjectLayout.validSource(name)) {
			throw new IllegalArgumentException("The project holds no source " + name);
		}

		return name;
	}

	private static String text(final UnaryOperator<String> texts, final String name) {
		return Optional.ofNullable(texts.apply(name))
			.orElseThrow(() -> new IllegalArgumentException("No text came for " + name));
	}
}
