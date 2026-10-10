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

package org.copperhead.ui;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.copperhead.ui.view.Tool;

/**
 * What the page's scripts would do, done by hand: the editor's text and marks read from the JSON it is sent, and
 * its controls sent with the values it would work out.
 */
final class Flows {
	static final String GUTTER = "#tool [data-editor-control=breakpoint]";

	private Flows() {
	}

	static String rail(final Tool tool) {
		return "#rail [hx-vals*=" + tool.name() + "]";
	}

	static JsonObject source(final Screen screen) {
		return Json.createReader(new StringReader(screen.find("#tool script[data-source]").data())).readObject();
	}

	static List<Integer> lines(final JsonObject source) {
		return source.getJsonArray("breakpoints").getValuesAs(JsonObject.class).stream()
			.map(mark -> mark.getInt("line")).toList();
	}

	/** Saves the entry file with the text given, as Ctrl+S does from the editor. */
	static Screen saved(final Screen screen, final String text) throws Exception {
		return screen.trigger("[data-key=\"ctrl+s\"]", Map.of("name", "main.s", "text", text));
	}

	/** Leaves a note on the status bar, the one opening the project again leaves, whichever tool is shown. */
	static Screen noted(final Screen screen, final WorkbenchRig rig) throws Exception {
		return screen.trigger("#menus [hx-post=\"/action/project-recent\"]",
			Map.of("path", rig.project().chp().toString()));
	}
}
