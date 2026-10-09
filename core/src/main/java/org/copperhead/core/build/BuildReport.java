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

package org.copperhead.core.build;

import java.time.Duration;
import java.util.List;

public record BuildReport(String adf, List<BuildLine> log, List<BuildWarning> warnings, List<BuildWarning> errors,
	int bytes, Duration took) {

	public BuildReport {
		log = List.copyOf(log);
		warnings = List.copyOf(warnings);
		errors = List.copyOf(errors);
	}

	/** A build came off only when it raised no error and wrote a disk. */
	public boolean ok() {
		return errors.isEmpty() && !adf.isEmpty();
	}
}
