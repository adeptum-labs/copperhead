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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.copperhead.ui.view.Tool;

/**
 * What the page's scripts would do, done by hand: the editor's text and marks read from the JSON it is sent, its
 * saves and builds sent with the values it would work out, and the build polled until it ends.
 */
final class Flows {
	static final String GUTTER = "#tool [data-editor-control=breakpoint]";
	static final String POLL = "#status [data-poll]";

	private static final Duration LONGEST_BUILD = Duration.ofSeconds(20);
	private static final long BETWEEN_POLLS = 20;

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
		return screen.trigger("[data-key=\"ctrl+s\"]",
			Map.of("name", "main.s", "text", text, "project", source(screen).getString("project")));
	}

	/** What the page sends with the buffers it holds: the folder of the project they were typed in. */
	static Map<String, String> owned(final WorkbenchRig rig, final Map<String, String> buffers) {
		final Map<String, String> owned = new LinkedHashMap<>(buffers);

		owned.put("project", rig.project().dir().toString());
		return owned;
	}

	/** Leaves a note on the status bar, the one opening the project again leaves, whichever tool is shown. */
	static Screen noted(final Screen screen, final WorkbenchRig rig) throws Exception {
		return screen.trigger("#menus [hx-post=\"/action/project-recent\"]",
			Map.of("path", rig.project().chp().toString()));
	}

	/** Builds as Ctrl+B does with nothing unsaved, then polls as the page does until the build is over. */
	static Screen build(final Screen screen) throws Exception {
		return finished(screen.trigger("[data-key=\"ctrl+b\"]", Map.of()));
	}

	static Screen finished(final Screen screen) throws Exception {
		return polledUntil(screen, done -> done.document().select(POLL).isEmpty());
	}

	/** Polls as the page does while a build runs, until what the test waits for shows. */
	static Screen polledUntil(final Screen screen, final Predicate<Screen> shown) throws Exception {
		final long deadline = System.nanoTime() + LONGEST_BUILD.toNanos();

		while (!shown.test(screen)) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("The build never got there");
			}

			Thread.sleep(BETWEEN_POLLS);
			screen.trigger(POLL, Map.of());
		}

		return screen;
	}
}
