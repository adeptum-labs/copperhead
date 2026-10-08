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

package org.copperhead.core.project;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.copperhead.core.debug.PcTrace;

/** A demo or game: its sources, the parts it runs through and the music it runs to. */
public record Project(String file, String name, ProjectKind kind, Duration length, String module, int bpm,
	List<SourceFile> sources, PcTrace trace, int breakpoint, List<Part> parts, List<TimelineEvent> events) {

	public Project {
		sources = List.copyOf(sources);
		parts = List.copyOf(parts);
		events = List.copyOf(events);
	}

	public SourceFile source(final String name) {
		return sources.stream().filter(source -> source.name().equals(name)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException(file + " has no source " + name));
	}

	/** The part playing at the moment given; the end of the run belongs to the last part. */
	public int partAt(final Duration at) {
		return IntStream.range(0, parts.size()).filter(i -> parts.get(i).holds(at)).findFirst()
			.orElse(parts.size() - 1);
	}

	public int part(final String name) {
		return IntStream.range(0, parts.size()).filter(i -> parts.get(i).name().equals(name)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException(file + " has no part " + name));
	}

	public String adf() {
		return name + ".adf";
	}
}
