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

/** A stretch of the run with code of its own; a part with exits loops until one of them fires. */
public record Part(String name, Duration start, Duration end, String meta, String file, int hue, List<Exit> exits) {
	public Part {
		if (start.compareTo(end) >= 0) {
			throw new IllegalArgumentException(name + " ends before it starts");
		}

		exits = List.copyOf(exits);
	}

	public boolean loops() {
		return !exits.isEmpty();
	}

	public boolean holds(final Duration at) {
		return at.compareTo(start) >= 0 && at.compareTo(end) < 0;
	}
}
