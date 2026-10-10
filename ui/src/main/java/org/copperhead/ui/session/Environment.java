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

package org.copperhead.ui.session;

import java.nio.file.Path;
import java.util.List;
import lombok.With;

/**
 * What the studio found around it rather than in a project: the projects the person opened last, newest first, and
 * where each tool of the build is, which the File menu, the welcome and the build tool show.
 */
@With
public record Environment(List<Path> recent, List<ToolStatus> tools) {
	public Environment {
		recent = List.copyOf(recent);
		tools = List.copyOf(tools);
	}

	public static Environment of(final List<Path> recent) {
		return new Environment(recent, List.of());
	}
}
