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

package org.copperhead.ui.view;

import java.util.Map;
import org.copperhead.web.action.View;

/** The whole window, made of its regions so the first page and every later swap draw them alike. */
public record IndexView(String title, Map<Region, View> regions) implements View {
	public IndexView {
		regions = Map.copyOf(regions);
	}

	public View region(final String name) {
		return regions.get(Region.valueOf(name));
	}

	@Override
	public String template() {
		return "index";
	}
}
