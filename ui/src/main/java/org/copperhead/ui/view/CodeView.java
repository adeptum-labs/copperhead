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

package org.copperhead.ui.view;

import org.copperhead.web.action.View;

/**
 * The editor of the open project. The page edits in its own editor and keeps the unsaved text; what comes from here
 * is what is on disk, as the JSON the page reads: every source with its time of change, the breakpoints and what
 * the last build said of each line.
 */
public record CodeView(String source) implements View {
	@Override
	public String template() {
		return "fragments/code :: code";
	}
}
