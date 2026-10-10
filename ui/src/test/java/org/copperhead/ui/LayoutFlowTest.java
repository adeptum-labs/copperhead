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

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.copperhead.ui.session.Layout;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The theme and the pane sizes are the person's, kept with their preferences and drawn into the next page. */
public class LayoutFlowTest {
	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
	private WorkbenchRig rig;

	@BeforeTry
	public void serve() throws Exception {
		rig = new WorkbenchRig(now::get);
	}

	@AfterTry
	public void stop() throws Exception {
		rig.close();
	}

	private Screen relaunched() throws Exception {
		rig = rig.restart(now::get);
		return Screen.open(rig);
	}

	@Example
	public void shouldStartDarkWithTheToolPaneTakingMoreThanHalf() throws Exception {
		final Screen screen = Screen.open(rig);

		assertEquals("dark", screen.find("html").attr("data-theme"));
		assertEquals("--split:56%;--bottom:250px", screen.find("[data-workspace]").attr("style"));
	}

	@Example
	public void shouldSwitchTheThemeFromTheTitleBarAndKeepItForTheNextLaunch() throws Exception {
		final Screen screen = Screen.open(rig).trigger(".titlebar__theme", Map.of("theme", "light"));

		assertEquals("light", screen.find("#status").attr("data-theme"));
		assertEquals("light", rig.preferences().layout().theme());
		assertEquals("light", relaunched().find("html").attr("data-theme"));
	}

	@Example
	public void shouldSwitchTheThemeFromTheViewMenu() throws Exception {
		final Screen screen = Screen.open(rig).trigger("#menus [data-theme-toggle]", Map.of("theme", "light"));

		assertEquals("light", screen.find("#status").attr("data-theme"));
	}

	@Example
	public void shouldRefuseAThemeItDoesNotHave() throws Exception {
		final Screen screen = Screen.open(rig);

		assertEquals(500, screen.trigger(".titlebar__theme", Map.of("theme", "purple")).status());
		assertEquals(Layout.defaults(), rig.preferences().layout());
	}

	/* The splitter sends where it was let go of and expects nothing back, since the page has laid itself out. */
	@Example
	public void shouldRememberWhereTheSplittersWereLeftForTheNextLaunch() throws Exception {
		assertEquals(204, Screen.open(rig).client().submit("/action/layout", "split=44&bottom=320").statusCode());
		assertEquals("--split:44%;--bottom:320px", relaunched().find("[data-workspace]").attr("style"));
	}

	@Example
	public void shouldPullASizeTheWindowCannotShowBackInsideIt() throws Exception {
		Screen.open(rig).client().submit("/action/layout", "split=99&bottom=5");

		assertEquals(new Layout("dark", 78, 120), rig.preferences().layout());
	}
}
