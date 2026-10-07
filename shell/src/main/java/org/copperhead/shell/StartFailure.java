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

package org.copperhead.shell;

import java.util.Objects;

/**
 * What a person sees when the window cannot be opened. A launched application has no console, so without
 * this a damaged browser engine would leave a server running and nothing on the screen.
 */
final class StartFailure {
	private StartFailure() {
	}

	static String message(final CefLocation location, final Throwable cause) {
		final String reason = Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getSimpleName());

		return "Copperhead could not start its browser engine from " + location.directory() + ".\n\n" + reason
			+ "\n\nInstall Copperhead again to restore it.";
	}
}
