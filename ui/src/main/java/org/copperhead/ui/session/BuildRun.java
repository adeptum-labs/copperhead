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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.copperhead.core.build.BuildLine;
import org.copperhead.core.build.Tone;

/**
 * Where the build stands while it runs: the lines it has written so far, and the question it waits on when the vasm
 * it fetched is not the one Copperhead pins. Idle once it ends; the report it ended with is the studio's last build.
 * It keeps the last {@value #LIMIT} lines and counts the ones it let go, so a tool that never stops talking cannot
 * grow the studio without end.
 */
public record BuildRun(Phase phase, List<BuildLine> log, int omitted, Optional<TrustRequest> trust) {
	public static final int LIMIT = 2000;

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
		return new BuildRun(Phase.IDLE, List.of(), 0, Optional.empty());
	}

	public static BuildRun started() {
		return new BuildRun(Phase.RUNNING, List.of(), 0, Optional.empty());
	}

	public boolean running() {
		return phase != Phase.IDLE;
	}

	public BuildRun add(final BuildLine line) {
		final boolean full = log.size() >= LIMIT;
		final List<BuildLine> kept = new ArrayList<>(log.subList(full ? 1 : 0, log.size()));

		kept.add(line);
		return new BuildRun(phase, kept, full ? omitted + 1 : omitted, trust);
	}

	/** The lines to read: those kept, under one that says how many went before them. */
	public List<BuildLine> shown() {
		return shown(log, omitted);
	}

	/** The last {@value #LIMIT} of the lines, under a line saying how many went before them, if any did. */
	public static List<BuildLine> shown(final List<BuildLine> lines, final int before) {
		final int dropped = before + Math.max(0, lines.size() - LIMIT);

		if (dropped == 0) {
			return lines;
		}

		final List<BuildLine> shown = new ArrayList<>();

		shown.add(new BuildLine("... " + dropped + " earlier lines left out", Tone.PLAIN));
		shown.addAll(lines.subList(lines.size() - Math.min(lines.size(), LIMIT), lines.size()));
		return shown;
	}

	public BuildRun waiting(final TrustRequest request) {
		return new BuildRun(Phase.WAITING, log, omitted, Optional.of(request));
	}

	public BuildRun resumed() {
		return new BuildRun(Phase.RUNNING, log, omitted, Optional.empty());
	}

	/** Stopped, with what it wrote still to be read, since a fetch of the toolchain ends in no report of its own. */
	public BuildRun ended() {
		return new BuildRun(Phase.IDLE, log, omitted, Optional.empty());
	}
}
