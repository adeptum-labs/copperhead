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

import java.util.List;
import org.copperhead.web.action.View;

public record BuildView(List<TargetCardView> targets, List<OptionView> options, List<BuildLineView> log,
	String summary) implements View {

	public BuildView {
		targets = List.copyOf(targets);
		options = List.copyOf(options);
		log = List.copyOf(log);
	}

	@Override
	public String template() {
		return "fragments/build :: build";
	}
}
