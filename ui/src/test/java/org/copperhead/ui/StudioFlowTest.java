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

package org.copperhead.ui;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.Region;
import org.copperhead.ui.view.Tool;
import org.jsoup.nodes.Element;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StudioFlowTest {
	private static final Pattern FONT = Pattern.compile("url\\(\"\\.\\./fonts/([^\"]+)\"\\)");

	private WorkbenchRig rig;

	@BeforeTry
	public void serve() throws Exception {
		rig = new WorkbenchRig();
	}

	@AfterTry
	public void stop() throws Exception {
		rig.close();
	}

	@Example
	public void shouldOpenOnTheStudioWithEveryRegionInPlace() throws Exception {
		final Screen screen = Screen.open(rig);

		assertEquals(200, screen.status());
		assertEquals("Copperhead", screen.document().title());
		assertAll(Arrays.stream(Region.values()).map(region -> () ->
			assertEquals(1, screen.document().select("#" + region.id()).size(), region.id())));
	}

	/* A path nothing is kept at is answered with the page itself, so an asset that went missing only shows as
	   HTML arriving where something else was asked for. The fonts are named by the stylesheet, not the page. */
	@Example
	public void shouldServeEverythingThePageAsksFor() throws Exception {
		final Screen screen = Screen.open(rig);
		final List<String> assets = new ArrayList<>(screen.document().select("script[src], link[href], img[src]")
			.stream().map(asset -> asset.hasAttr("src") ? asset.attr("src") : asset.attr("href")).toList());

		FONT.matcher(screen.client().get("/static/css/copperhead.css").body()).results()
			.map(found -> "/static/fonts/" + found.group(1)).forEach(assets::add);

		assertAll(assets.stream().map(path -> () -> {
			final HttpResponse<String> response = screen.client().get(path);

			assertEquals(200, response.statusCode(), path);
			assertFalse(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"), path);
		}));
	}

	private static String railTool(final Tool tool) {
		return "#rail [hx-vals*=" + tool.name() + "]";
	}

	@Example
	public void shouldSwitchToolsFromTheRail() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.SPRITES));

		assertEquals("Sprites", screen.find("#rail .rail__tool--on").attr("title"));
		assertEquals("Sprites", screen.find("#tool .tool__tab--active").text());
		assertTrue(screen.find("#menus [hx-vals*=SPRITES]").hasClass("menu__item--checked"));
	}

	@Example
	public void shouldSwitchToolsFromTheKeyboard() throws Exception {
		final Screen screen = Screen.open(rig).press("ctrl+4");

		assertEquals("Debugger", screen.find("#rail .rail__tool--on").attr("title"));
	}

	@Example
	public void shouldSwitchTheTargetMachine() throws Exception {
		final Screen screen = Screen.open(rig).click("#machine [hx-vals*=AGA]");

		assertEquals("AGA", screen.find("#machine .machine__chipset--on").text());
		assertEquals("chip 1.2M", screen.find("#rail .rail__chip").text());
		assertTrue(screen.find("#menus [hx-post=/action/target][hx-vals*=AGA]").hasClass("menu__item--checked"));
	}

	@Example
	public void shouldOpenTheOtherProjectAfresh() throws Exception {
		final Screen screen = Screen.open(rig);

		rig.session().change(state -> state.withFile("intro.s").withCursor(5));
		screen.click("#menus [hx-vals*=blitzball]");

		assertEquals("blitzball.chp", screen.find("#menus .menus__project").text());
		assertEquals("main.s", rig.session().state().file());
		assertEquals(11, rig.session().state().cursor());
		assertEquals("main.s:11", rig.session().state().breakpoints().getFirst().location());
	}

	@Example
	public void shouldHideTheTimelineAndUncheckIt() throws Exception {
		final Screen screen = Screen.open(rig).press("ctrl+j");

		assertFalse(screen.find("#menus [hx-post=/action/timeline]").hasClass("menu__item--checked"));
		assertFalse(rig.session().state().timelineShown());
	}

	/* The page is never swapped on a failure, so only the state of the session shows whether a refused request
	   left everything as it was. */
	@Example
	public void shouldRefuseWhatItDoesNotKnowAndLeaveTheStateAlone() throws Exception {
		final Screen screen = Screen.open(rig);
		final Map<String, Map<String, String>> unknown = Map.of(
			"#rail [hx-vals*=SPRITES]", Map.of("tool", "NOPE"),
			"#machine [hx-vals*=AGA]", Map.of("target", "ECS"),
			"#menus [hx-post=/action/modal]", Map.of("kind", "NOPE"),
			"#menus [hx-vals*=blitzball]", Map.of("name", "nowhere"));

		assertAll(unknown.entrySet().stream().map(bad -> () -> {
			final StudioState before = rig.session().state();

			assertEquals(500, screen.trigger(bad.getKey(), bad.getValue()).status(), bad.getKey());
			assertEquals(before, rig.session().state(), bad.getKey());
		}));
		assertAll(Stream.of("tool", "target", "modal", "project").map(action -> () -> {
			final StudioState before = rig.session().state();

			assertEquals(500, screen.client().submit("/action/" + action, "").statusCode(), action);
			assertEquals(before, rig.session().state(), action);
		}));
	}

	@Example
	public void shouldWriteEveryShortcutOnceInTheOrderTheScriptNamesIt() throws Exception {
		final List<String> keys = Screen.open(rig).document().select("[data-key]").eachAttr("data-key");
		final List<String> modifiers = List.of("ctrl", "alt", "shift");

		assertEquals(keys.size(), new HashSet<>(keys).size());
		assertAll(keys.stream().map(key -> () -> {
			final List<String> parts = List.of(key.split("\\+"));
			final List<String> held = parts.subList(0, parts.size() - 1);

			assertEquals(key.toLowerCase(Locale.ROOT), key);
			assertTrue(modifiers.containsAll(held), key);
			assertEquals(held.stream().sorted(Comparator.comparing(modifiers::indexOf)).toList(), held, key);
		}));
	}

	@Example
	public void shouldOfferWhatIsNotBuiltYetDisabled() throws Exception {
		final Element save = Screen.open(rig).document().select("#menus .menu__item").stream()
			.filter(item -> item.select(".menu__label").text().equals("Save")).findFirst().orElseThrow();

		assertTrue(save.hasAttr("disabled"));
		assertEquals("Coming in a later release", save.attr("title"));
	}
}
