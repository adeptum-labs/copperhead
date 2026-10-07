/*
    Copperhead
    Copyright © 2026 Adam Waldenberg

    This program is free software: you can redistribute it and/or modify it under the terms of the
    GNU General Public License as published by the Free Software Foundation, version 3 of the License.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
    even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along with this program.
    If not, see <http://www.gnu.org/licenses/>.
 */

package org.copperhead.ui.session;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.UnaryOperator;
import org.copperhead.core.project.Target;
import org.copperhead.core.sample.SampleProjects;

/** The project open in the studio and everything shown of it. Controllers change it; regions are drawn from it. */
@ApplicationScoped
public class ProjectSession {
	private volatile StudioState state = StudioState.open(SampleProjects.nightfall(), Target.A500_OCS);

	public StudioState state() {
		return state;
	}

	/**
	 * Applies a change. The note in the status bar tells what the last action did, so every change starts
	 * without one. A change that throws leaves the studio as it was, and the page keeps what it shows.
	 */
	public synchronized StudioState change(final UnaryOperator<StudioState> change) {
		state = change.apply(state.withNote(""));
		return state;
	}
}
