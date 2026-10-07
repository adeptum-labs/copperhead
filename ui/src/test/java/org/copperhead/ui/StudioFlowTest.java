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
	private static final String CELLS = "#tool .sprite__grid .sprite__cell";

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
		assertEquals("ball.spr", screen.find("#tool .tool__tab--active").text());
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

	private static String line(final int number) {
		return "#tool .code__line:nth-of-type(" + number + ")";
	}

	@Example
	public void shouldOpenTheTracedFileStoppedAtItsBreakpoint() throws Exception {
		final Screen screen = Screen.open(rig);

		assertEquals("plasma.s", screen.find("#tool .tool__tab--active").text());
		screen.find("#tool .tool__tab--active .tool__mark");
		assertTrue(screen.find(line(13)).hasClass("code__line--pc"));
		assertTrue(screen.find(line(13) + " .code__dot").hasClass("code__dot--on"));
		assertEquals("68000 · UTF-8 · ln 13 · 1 breakpoint", screen.find("#tool .tool__meta").text());
	}

	@Example
	public void shouldToggleABreakpointOnTheLineNumber() throws Exception {
		final Screen screen = Screen.open(rig).click(line(20) + " .code__gutter");

		assertTrue(screen.find(line(20) + " .code__dot").hasClass("code__dot--on"));
		screen.click(line(20) + " .code__gutter");
		assertFalse(screen.find(line(20) + " .code__dot").hasClass("code__dot--on"));
	}

	@Example
	public void shouldSetABreakpointWhereTheCursorIsWithF9() throws Exception {
		final Screen screen = Screen.open(rig).click(line(5) + " .code__text").press("f9");

		assertTrue(screen.find(line(5)).hasClass("code__line--cursor"));
		assertTrue(screen.find(line(5) + " .code__dot").hasClass("code__dot--on"));
		assertEquals("68000 · UTF-8 · ln 5 · 2 breakpoints", screen.find("#tool .tool__meta").text());
	}

	@Example
	public void shouldSwitchFilesFromTheTabs() throws Exception {
		final Screen screen = Screen.open(rig).click("#tool [hx-vals*=intro.s]");

		assertEquals("intro.s", screen.find("#tool .tool__tab--active").text());
		assertTrue(screen.document().select("#tool .code__line--pc").isEmpty());
		assertEquals(20, screen.document().select("#tool .code__line").size());
	}

	@Example
	public void shouldClearEveryBreakpointFromTheDebugMenu() throws Exception {
		final Screen screen = Screen.open(rig).click("#menus [hx-post=/action/breakpoints-clear]");

		assertTrue(screen.document().select("#tool .code__dot--on").isEmpty());
	}

	@Example
	public void shouldRefuseALineTheFileDoesNotHave() throws Exception {
		final Screen screen = Screen.open(rig);
		final StudioState before = rig.session().state();

		assertEquals(500, screen.trigger(line(5) + " .code__text", Map.of("line", "99")).status());
		assertEquals(before, rig.session().state());
	}

	@Example
	public void shouldRefuseALineThatIsNoNumber() throws Exception {
		final Screen screen = Screen.open(rig);
		final StudioState before = rig.session().state();

		assertEquals(500, screen.trigger(line(5) + " .code__gutter", Map.of("line", "five")).status());
		assertEquals(before, rig.session().state());
	}

	@Example
	public void shouldPaintTheCellClickedInTheColourChosen() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.SPRITES))
			.click("#tool .swatch:nth-of-type(3)").click(CELLS + ":nth-of-type(1)");

		assertTrue(screen.find("#tool .swatch:nth-of-type(3)").hasClass("swatch--on"));
		assertTrue(screen.find(CELLS + ":nth-of-type(1)").hasClass("sprite__cell--2"));
	}

	@Example
	public void shouldClearEveryCell() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.SPRITES))
			.click("#tool [hx-post=/action/sprite-clear]");

		assertEquals(256, screen.document().select(CELLS + ".sprite__cell--0").size());
	}

	@Example
	public void shouldRefuseAColourAHardwareSpriteCannotShow() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.SPRITES));
		final StudioState before = rig.session().state();

		assertEquals(500, screen.trigger("#tool .swatch:nth-of-type(3)", Map.of("colour", "4")).status());
		assertEquals(before, rig.session().state());
	}

	@Example
	public void shouldRefuseACellOutsideTheSprite() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.SPRITES));
		final StudioState before = rig.session().state();

		assertEquals(500, screen.trigger(CELLS + ":nth-of-type(1)", Map.of("cell", "256")).status());
		assertEquals(before, rig.session().state());
	}

	@Example
	public void shouldOpenThePaletteOnColourFour() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.PALETTE));

		assertEquals("COLOR04 $015C", screen.find("#tool .palette__picked").text());
		assertTrue(screen.find("#tool .palette__entry:nth-of-type(5)").hasClass("palette__entry--on"));
		assertEquals("12-bit OCS · 4 bits per channel shown", screen.find("#tool .palette__depth").text());
	}

	@Example
	public void shouldPickTheColourClicked() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.PALETTE))
			.click("#tool .palette__entry:nth-of-type(32)");

		assertEquals("COLOR31 $0330", screen.find("#tool .palette__picked").text());
	}

	@Example
	public void shouldDescribeTheAgaPaletteAsTwentyFourBit() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.PALETTE)).click("#machine [hx-vals*=AGA]");

		assertEquals("24-bit AGA · 4 bits per channel shown", screen.find("#tool .palette__depth").text());
		assertEquals("32 colours · 24-bit", screen.find("#tool .tool__meta").text());
	}

	@Example
	public void shouldRefuseAColourThePaletteDoesNotHold() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.PALETTE));
		final StudioState before = rig.session().state();

		assertEquals(500, screen.trigger("#tool .palette__entry:nth-of-type(1)", Map.of("index", "40")).status());
		assertEquals(before, rig.session().state());
	}

	@Example
	public void shouldShowRegistersCopperMemoryAndBreakpoints() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.DEBUGGER));

		assertEquals("00000017", screen.find("#tool .register:nth-of-type(1) .register__value").text());
		assertEquals("END", screen.find("#tool .copper__row:last-of-type .copper__decoded").text());
		assertEquals(4, screen.document().select("#tool .memory__row").size());
		assertEquals("plasma.s:13", screen.find("#tool .breakpoint__location").text());
	}

	@Example
	public void shouldStepWithF8AndPauseTheMachine() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.DEBUGGER)).press("f8");

		assertEquals("stopped · plasma.s:14", screen.find("#tool .debugger__status").text());
		assertEquals("00000018", screen.find("#tool .register:nth-of-type(1) .register__value").text());
		assertEquals("▶ Run", screen.find("#machine .machine__run").text());
	}

	@Example
	public void shouldCountTheHitWhenTheRunComesRound() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.DEBUGGER))
			.press("f8").press("f8").press("f8").press("f10");

		assertEquals("stopped · plasma.s:13 · 1,171 hits", screen.find("#tool .debugger__status").text());
		assertEquals("1,171 hits", screen.find("#tool .breakpoint__hits").text());
	}

	@Example
	public void shouldMarkTheLineTheRunStoppedOnInTheCode() throws Exception {
		final Screen screen = Screen.open(rig).press("f8").click(railTool(Tool.CODE));

		assertTrue(screen.find(line(14)).hasClass("code__line--pc"));
	}

	@Example
	public void shouldWarnOfTheScaledIndexWhenBuildingForOcs() throws Exception {
		final Screen screen = Screen.open(rig).press("ctrl+b");

		assertEquals("1 warning · plasma.s:24", screen.find("#status .status__warning").text());
		assertEquals("Built nightfall.adf · 2,911 bytes", screen.find("#status .status__note").text());
		assertEquals("last build 2,911 bytes · 0.9s", screen.find("#status .status__build").text());
	}

	@Example
	public void shouldBuildCleanForAga() throws Exception {
		final Screen screen = Screen.open(rig).click("#machine [hx-vals*=AGA]").press("ctrl+b");

		assertTrue(screen.document().select("#status .status__warning").isEmpty());
		assertEquals("0 warnings", screen.find("#status .status__warnings").text());
	}

	@Example
	public void shouldShipUncrunchedOnceShrinklerIsOff() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.BUILD))
			.click("#tool [hx-vals*=SHRINKLER]").click("#tool [hx-post=/action/build]");

		assertEquals("nightfall.adf · 7,016 bytes · 0.9s · A500", screen.find("#tool .build__summary").text());
		assertFalse(screen.find("#menus [hx-vals*=SHRINKLER]").hasClass("menu__item--checked"));
	}

	@Example
	public void shouldJumpToTheLineAWarningNames() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.SPRITES)).press("ctrl+b")
			.click("#status .status__warning");

		assertEquals("plasma.s", screen.find("#tool .tool__tab--active").text());
		assertTrue(screen.find(line(24)).hasClass("code__line--cursor"));
	}

	@Example
	public void shouldForgetTheNoteOnTheNextAction() throws Exception {
		final Screen screen = Screen.open(rig).press("ctrl+b").click(railTool(Tool.SPRITES));

		assertTrue(screen.document().select("#status .status__note").isEmpty());
	}

	@Example
	public void shouldForgetTheNoteOnABreakpointToggle() throws Exception {
		final Screen screen = Screen.open(rig).press("ctrl+b").click(line(20) + " .code__gutter");

		assertTrue(screen.document().select("#status .status__note").isEmpty());
	}

	@Example
	public void shouldForgetTheNoteOnABuildOptionToggle() throws Exception {
		final Screen screen = Screen.open(rig).press("ctrl+b").click("#menus [hx-vals*=SHRINKLER]");

		assertTrue(screen.document().select("#status .status__note").isEmpty());
	}

	@Example
	public void shouldForgetTheNoteOnASpritePaint() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.SPRITES)).press("ctrl+b")
			.click(CELLS + ":nth-of-type(1)");

		assertTrue(screen.document().select("#status .status__note").isEmpty());
	}

	@Example
	public void shouldShowThePartAndFrameTheRunIsAt() throws Exception {
		final Screen screen = Screen.open(rig);

		assertEquals("Part 03 · Plasma", screen.find("#status .status__part").text());
		assertEquals("frame 1170", screen.find("#status .status__frame").text());
		assertEquals("A500 · OCS", screen.find("#status .status__machine").text());
	}

	@Example
	public void shouldRefuseAnOptionItDoesNotHave() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.BUILD));
		final StudioState before = rig.session().state();

		assertEquals(500, screen.trigger("#tool [hx-vals*=SHRINKLER]", Map.of("option", "TURBO")).status());
		assertEquals(before, rig.session().state());
	}

	@Example
	public void shouldPauseAndRunAgain() throws Exception {
		final Screen screen = Screen.open(rig).click("#machine .machine__run");

		assertEquals("▶ Run", screen.find("#machine .machine__run").text());
		screen.find("#emulator .emulator__dot--paused");
		assertEquals("❚❚ Pause", screen.click("#machine .machine__run").find("#machine .machine__run").text());
	}

	@Example
	public void shouldResetTheMachineToTheStartOfTheRun() throws Exception {
		final Screen screen = Screen.open(rig).press("shift+f5");

		assertEquals("Part 01 · Loader", screen.find("#status .status__part").text());
		assertEquals("frame 0", screen.find("#status .status__frame").text());
		assertEquals("▶ Run", screen.find("#machine .machine__run").text());
		assertEquals("NIGHTFALL · LOADER", screen.find("#emulator .emulator__title").text());
	}

	@Example
	public void shouldShowThePlasmaRunningOnAnA500() throws Exception {
		final Screen screen = Screen.open(rig);

		assertEquals("Emulator · A500 · OCS · PAL 50Hz", screen.find("#emulator .emulator__name").text());
		assertEquals("NIGHTFALL · PLASMA", screen.find("#emulator .emulator__title").text());
		assertEquals("1170", screen.find("#emulator .meter__frame").text());
	}

	@Example
	public void shouldShowTheAgaMachine() throws Exception {
		final Screen screen = Screen.open(rig).click("#machine [hx-vals*=AGA]");

		assertEquals("Emulator · A1200 · AGA · PAL 50Hz", screen.find("#emulator .emulator__name").text());
		assertEquals("320×256 · 1 bpl + copper · 24-bit AGA", screen.find("#emulator .emulator__info").text());
		assertEquals("812K / 2M", screen.find("#emulator .meter__chip").text());
	}
}
