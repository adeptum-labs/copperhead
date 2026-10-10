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

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.copperhead.core.build.BuildLine;

/**
 * Where the build stands while it runs: the lines it has written so far, and the question it waits on when the vasm
 * it fetched is not the one Copperhead pins. Idle once it ends; the report it ended with is the studio's last build.
 */
public record BuildRun(Phase phase, List<BuildLine> log, Optional<TrustRequest> trust) {
	public enum Phase {
		IDLE,
		RUNNING,
		WAITING
	}

	/** A tool whose source does not match the hash Copperhead pins, waiting for the person to accept or refuse it. */
	public record TrustRequest(String tool, String version, String sha256) {
	}

	public BuildRun {
		log = List.copyOf(log);
	}

	public static BuildRun idle() {
		return new BuildRun(Phase.IDLE, List.of(), Optional.empty());
	}

	public static BuildRun started() {
		return new BuildRun(Phase.RUNNING, List.of(), Optional.empty());
	}

	public boolean running() {
		return phase != Phase.IDLE;
	}

	public BuildRun add(final BuildLine line) {
		return new BuildRun(phase, Stream.concat(log.stream(), Stream.of(line)).toList(), trust);
	}

	public BuildRun waiting(final TrustRequest request) {
		return new BuildRun(Phase.WAITING, log, Optional.of(request));
	}

	public BuildRun resumed() {
		return new BuildRun(Phase.RUNNING, log, Optional.empty());
	}

	/** Stopped, with what it wrote still to be read, since a fetch of the toolchain ends in no report of its own. */
	public BuildRun ended() {
		return new BuildRun(Phase.IDLE, log, Optional.empty());
	}
}
