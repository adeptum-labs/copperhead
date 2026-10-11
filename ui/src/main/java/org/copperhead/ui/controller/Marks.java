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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.copperhead.core.debug.Breakpoint;
import org.copperhead.core.project.ProjectLayout;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.ui.session.StudioState;
import org.copperhead.web.action.Form;

/**
 * The breakpoints the page holds for the sources it names, which moved with the lines typed above them. The page
 * names the sources under {@code marked}, separated by commas, and each one's lines under {@code breakpoints:} and
 * its name; a list replaces what the studio held for that source, so a toggle that follows lands on the line shown.
 */
final class Marks {
	static final String MARKED = "marked";

	private static final String LINES = "breakpoints:";
	private static final String SEPARATOR = ",";
	private static final char NEW_LINE = '\n';
	/* Far more than anyone sets by hand, and few enough that a crafted request cannot bloat the project file. */
	private static final int MOST = 4096;

	private final Map<String, List<Integer>> lines;

	private Marks(final Map<String, List<Integer>> lines) {
		this.lines = lines;
	}

	/**
	 * The lists the request brought, each checked against the text of its source: the one the request carried, as
	 * the page holds it, or else the one on disk. A list that names no source of the project, or a line the text
	 * does not have, refuses them all.
	 */
	static Marks of(final ProjectDirectory directory, final Form form, final UnaryOperator<String> texts) {
		if (!form.has(MARKED)) {
			return new Marks(Map.of());
		}

		Buffers.owned(directory, form);

		final Map<String, List<Integer>> lines = new LinkedHashMap<>();

		for (final String name : form.get(MARKED).split(SEPARATOR)) {
			if (!ProjectLayout.validSource(name)) {
				throw new IllegalArgumentException("The project holds no source " + name);
			}

			final String text = Optional.ofNullable(texts.apply(name))
				.orElseGet(() -> Workspaces.read(() -> directory.read(Workspaces.source(directory, name))));

			lines.put(name, lines(name, form.require(LINES + name), text));
		}

		if (lines.values().stream().mapToInt(List::size).sum() > MOST) {
			throw new IllegalArgumentException("More than " + MOST + " breakpoints came");
		}

		return new Marks(lines);
	}

	/** The breakpoints of the studio with the lists brought in place of the ones it held for those sources. */
	StudioState placed(final StudioState state) {
		if (lines.isEmpty()) {
			return state;
		}

		return state.withBreakpoints(Stream.concat(
			state.breakpoints().stream().filter(mark -> !lines.containsKey(mark.file())),
			lines.entrySet().stream().flatMap(entry -> entry.getValue().stream()
				.map(line -> held(state, entry.getKey(), line)))).toList());
	}

	/* A breakpoint that stayed on its line keeps the hits counted on it. */
	private static Breakpoint held(final StudioState state, final String file, final int line) {
		return state.breakpoints().stream().filter(mark -> mark.file().equals(file) && mark.line() == line)
			.findFirst().orElseGet(() -> new Breakpoint(file, line, 0));
	}

	private static List<Integer> lines(final String name, final String sent, final String text) {
		final long last = text.chars().filter(character -> character == NEW_LINE).count() + 1;
		final String[] entries = sent.isEmpty() ? new String[0] : sent.split(SEPARATOR, -1);

		if (entries.length > MOST) {
			throw new IllegalArgumentException("More than " + MOST + " breakpoints came for " + name);
		}

		final List<Integer> lines = Arrays.stream(entries).map(Integer::valueOf).distinct().sorted().toList();

		if (lines.stream().anyMatch(line -> line < 1 || line > last)) {
			throw new IllegalArgumentException("A breakpoint of " + name + " is not on one of its lines 1 to "
				+ last);
		}

		return lines;
	}
}
