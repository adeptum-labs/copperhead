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

package org.copperhead.core.debug;

import java.util.List;

/**
 * The lines the program counter passes through, in order, standing in for a running machine until the
 * emulator is wired in. The run loops, as the main loop of a demo does.
 */
public record PcTrace(String file, List<Integer> lines) {
	public PcTrace {
		if (lines.isEmpty()) {
			throw new IllegalArgumentException("A run passes through at least one line");
		}

		lines = List.copyOf(lines);
	}

	public int first() {
		return lines.getFirst();
	}

	public int after(final int line) {
		final int at = lines.indexOf(line);

		return at < 0 ? first() : lines.get((at + 1) % lines.size());
	}
}
