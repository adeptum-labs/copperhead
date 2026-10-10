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

import java.nio.file.Path;
import java.util.Optional;

/** The project to build and the tool choices the preferences hold: paths the user set and a vasm hash they trusted. */
public record BuildRequest(ProjectDirectory project, Optional<Path> vasm, Optional<Path> shrinkler,
	Optional<String> trustedVasm) {
}
