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

import jakarta.json.JsonObject;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.Target;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.Region;
import org.copperhead.ui.view.Tool;
import org.jsoup.nodes.Element;
import static org.copperhead.ui.Flows.GUTTER;
import static org.copperhead.ui.Flows.build;
import static org.copperhead.ui.Flows.lines;
import static org.copperhead.ui.Flows.noted;
import static org.copperhead.ui.Flows.saved;
import static org.copperhead.ui.Flows.source;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StudioFlowTest {
	private static final Pattern FONT = Pattern.compile("url\\(\"\\.\\./fonts/([^\"]+)\"\\)");
	private static final String CELLS = "#tool .sprite__grid .sprite__cell";

	/* Still unless a test moves it, so every page shows exactly the same text. */
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

	/* Another project beside the one open, made from its template and remembered as opened lately. */
	private Path made(final ProjectKind kind, final String name) throws Exception {
		final Path chp = ProjectDirectory.create(rig.home().resolve(WorkbenchRig.PROJECTS), name, kind,
			Target.A500_OCS).chp();

		rig.preferences().used(chp);
		return chp;
	}

	/* Opens a project the way the File menu's recent list does; nothing is unsaved, so nothing is asked. */
	private static Screen open(final Path chp, final Screen screen) throws Exception {
		return screen.trigger("#menus [hx-post=\"/action/project-recent\"]", Map.of("path", chp.toString()));
	}

	private Screen open(final Path chp) throws Exception {
		return open(chp, Screen.open(rig));
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
		final Screen screen = open(made(ProjectKind.GAME, "arcade"));

		assertEquals("arcade.chp", screen.find("#menus .menus__project").text());
		assertEquals("main.s", rig.session().state().file());
		assertEquals(1, rig.session().state().cursor());
		assertEquals(List.of(), rig.session().state().breakpoints());
	}

	@Example
	public void shouldOpenTheOtherProjectInTheSameLayoutWithNothingBuilt() throws Exception {
		final Path arcade = made(ProjectKind.GAME, "arcade");
		final Screen screen = Screen.open(rig).click(railTool(Tool.SPRITES)).press("ctrl+j");

		screen.trigger("#menus [hx-post=\"/action/project-recent\"]", Map.of("path", arcade.toString()));

		assertEquals("Sprites", screen.find("#rail .rail__tool--on").attr("title"));
		assertTrue(screen.find("#timeline").hasAttr("hidden"));
		assertEquals("0 warnings", screen.find("#status .status__warnings").text());
		assertEquals("last build not built yet", screen.find("#status .status__build").text());
		assertEquals("Opened arcade.chp (game)", screen.find("#status .status__note").text());
	}

	@Example
	public void shouldHideTheTimelineAndUncheckIt() throws Exception {
		final Screen screen = Screen.open(rig).press("ctrl+j");

		assertFalse(screen.find("#menus [hx-post=/action/timeline]").hasClass("menu__item--checked"));
		assertFalse(rig.session().state().timelineShown());
	}

	@Example
	public void shouldMarkTheLightModeItemSoThePageCanSwitchTheTheme() throws Exception {
		assertEquals(1, Screen.open(rig).document().select("#menus [data-theme-toggle]").size());
	}

	/* The page is never swapped on a failure, so only the state of the session shows whether a refused request
	   left everything as it was. */
	@Example
	public void shouldRefuseWhatItDoesNotKnowAndLeaveTheStateAlone() throws Exception {
		final Screen screen = Screen.open(rig);
		final Map<String, Map<String, String>> unknown = Map.of(
			"#rail [hx-vals*=SPRITES]", Map.of("tool", "NOPE"),
			"#machine [hx-vals*=AGA]", Map.of("target", "ECS"),
			"#menus [hx-post=\"/action/modal\"]", Map.of("kind", "NOPE"),
			"#menus [hx-post=\"/action/project-recent\"]",
			Map.of("path", rig.home().resolve("nowhere.chp").toString()));

		assertAll(unknown.entrySet().stream().map(bad -> () -> {
			final StudioState before = rig.session().state();

			assertEquals(500, screen.trigger(bad.getKey(), bad.getValue()).status(), bad.getKey());
			assertEquals(before, rig.session().state(), bad.getKey());
		}));
	}

	/* A request that leaves out a value is the caller's mistake, which the studio answers as one. */
	@Example
	public void shouldRefuseARequestThatLeavesOutWhatTheActionNeedsAsABadRequest() throws Exception {
		final Screen screen = Screen.open(rig);

		assertAll(Stream.of("project-recent", "project-create", "project-settings", "file-open", "file-save",
			"breakpoint", "cursor", "source-check", "tool", "target", "modal", "theme", "preferences-save",
			"build-option", "build-progress", "trust-accept", "trust-refuse", "seek", "part", "exit",
			"sprite-paint", "sprite-colour", "palette-pick", "browse").map(action -> () -> {
				final StudioState before = rig.session().state();

				assertEquals(400, screen.client().submit("/action/" + action, "").statusCode(), action);
				assertEquals(before, rig.session().state(), action);
			}));
	}

	/* The new project dialog sends what was typed so far with every folder it browses to. */
	@Example
	public void shouldRefuseABrowseOfTheNewProjectDialogThatLeavesOutTheDraftAsABadRequest() throws Exception {
		final Screen screen = Screen.open(rig).click("#menus [hx-post=\"/action/project-new\"]");
		final String folder = "path=" + URLEncoder.encode(rig.home().toString(), StandardCharsets.UTF_8);

		for (final String left : List.of("&kind=DEMO&target=OCS", "&name=x&target=OCS", "&name=x&kind=DEMO")) {
			assertEquals(400, screen.client().submit("/action/browse", folder + left).statusCode(), left);
		}

		assertEquals(200, screen.client().submit("/action/browse", folder + "&name=x&kind=DEMO&target=OCS")
			.statusCode());
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
		final Element importing = Screen.open(rig).document().select("#menus .menu__item").stream()
			.filter(item -> item.select(".menu__label").text().equals("Import asset…")).findFirst()
			.orElseThrow();

		assertTrue(importing.hasAttr("disabled"));
		assertEquals("Coming in a later release", importing.attr("title"));
	}

	@Example
	public void shouldOpenTheProjectAtItsEntryFile() throws Exception {
		final Screen screen = Screen.open(rig);
		final JsonObject source = source(screen);

		assertEquals("main.s", screen.find("#tool .tool__tab--active").text());
		assertEquals("main.s", source.getString("active"));
		assertEquals(Files.readString(rig.project().dir().resolve("src/main.s")),
			source.getJsonArray("files").getJsonObject(0).getString("text"));
		assertEquals("68000 · UTF-8 · 0 breakpoints", screen.find("#tool .tool__meta").text());
	}

	@Example
	public void shouldToggleABreakpointOnTheLineNumber() throws Exception {
		final Screen screen = Screen.open(rig).trigger(GUTTER, Map.of("line", "3"));

		assertEquals(List.of(3), lines(source(screen)));
		screen.trigger(GUTTER, Map.of("line", "3"));
		assertEquals(List.of(), lines(source(screen)));
	}

	@Example
	public void shouldSetABreakpointWhereTheCursorIsWithF9() throws Exception {
		final Screen screen = Screen.open(rig).trigger(GUTTER, Map.of("line", "2"))
			.trigger("[data-key=f9]", Map.of("line", "5"));

		assertEquals(5, source(screen).getInt("cursor"));
		assertEquals(List.of(2, 5), lines(source(screen)));
		assertEquals("68000 · UTF-8 · 2 breakpoints", screen.find("#tool .tool__meta").text());
	}

	@Example
	public void shouldSwitchFilesFromTheTabs() throws Exception {
		Files.writeString(rig.project().dir().resolve("src/tables.i"), "sine:\tdc.w 0\n");

		final Screen screen = Screen.open(rig).click("#tool [hx-vals*=tables.i]");

		assertEquals("tables.i", screen.find("#tool .tool__tab--active").text());
		assertEquals("tables.i", source(screen).getString("active"));
		assertEquals(List.of("main.s", "tables.i"), screen.document().select("#tool .tool__tab").eachText());
	}

	@Example
	public void shouldRefuseATabThatIsNoSourceOfTheProject() throws Exception {
		final Screen screen = Screen.open(rig);
		final StudioState before = rig.session().state();

		for (final String name : List.of("nope.s", "../main.s", "sub/main.s")) {
			assertEquals(500, screen.trigger("#tool .tool__tab", Map.of("name", name)).status(), name);
			assertEquals(before, rig.session().state(), name);
		}
	}

	@Example
	public void shouldWriteASourceThatClosesTheScriptElementWithItsBracketsEscaped() throws Exception {
		final String text = "</script><b>&amp;\n";

		Files.writeString(rig.project().dir().resolve("src/main.s"), text);

		final Screen screen = Screen.open(rig);
		final String block = screen.find("#tool script[data-source]").data();

		assertFalse(block.contains("</script"));
		assertTrue(block.contains("\\u003c/script>"));
		assertEquals(text, source(screen).getJsonArray("files").getJsonObject(0).getString("text"));
	}

	@Example
	public void shouldClearEveryBreakpointFromTheDebugMenu() throws Exception {
		final Screen screen = Screen.open(rig).trigger(GUTTER, Map.of("line", "2"))
			.trigger(GUTTER, Map.of("line", "4")).click("#menus [hx-post=\"/action/breakpoints-clear\"]");

		assertEquals(List.of(), lines(source(screen)));
	}

	@Example
	public void shouldRefuseALineTheFileCannotHave() throws Exception {
		final Screen screen = Screen.open(rig);
		final StudioState before = rig.session().state();

		assertEquals(500, screen.trigger("#tool [data-editor-control=cursor]", Map.of("line", "0")).status());
		assertEquals(before, rig.session().state());
	}

	@Example
	public void shouldRefuseALineThatIsNoNumber() throws Exception {
		final Screen screen = Screen.open(rig);
		final StudioState before = rig.session().state();

		assertEquals(500, screen.trigger(GUTTER, Map.of("line", "five")).status());
		assertEquals(before, rig.session().state());
	}

	@Example
	public void shouldPaintTheCellClickedInTheColourChosen() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.SPRITES))
			.click("#tool .swatch:nth-of-type(3)").click(CELLS + ":nth-of-type(1)");

		assertTrue(screen.find("#tool .swatch:nth-of-type(3)").hasClass("swatch--on"));
		assertTrue(screen.find(CELLS + ":nth-of-type(1)").hasClass("sprite__cell--2"));
		assertEquals("Highlight · $FB7", screen.find("#tool .sprite__colour").text());
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
		final Screen screen = Screen.open(rig).trigger(GUTTER, Map.of("line", "3")).click(railTool(Tool.DEBUGGER));

		assertEquals("00000017", screen.find("#tool .register:nth-of-type(1) .register__value").text());
		assertEquals("END", screen.find("#tool .copper__row:last-of-type .copper__decoded").text());
		assertEquals(4, screen.document().select("#tool .memory__row").size());
		assertEquals("main.s:3", screen.find("#tool .breakpoint__location").text());
		assertEquals("Pause · F5", screen.find("#tool .debugger__bar [hx-post=/action/run]").text());
	}

	/* A breakpoint can be set in any file, so the card names none. */
	@Example
	public void shouldListTheBreakpointsOfEveryFile() throws Exception {
		Files.writeString(rig.project().dir().resolve("src/tables.i"), "sine:\tdc.w 0\n");

		final Screen screen = Screen.open(rig).trigger(GUTTER, Map.of("line", "3"))
			.click("#tool [hx-vals*=tables.i]").trigger(GUTTER, Map.of("line", "1"))
			.click(railTool(Tool.DEBUGGER));

		assertTrue(screen.document().select("#tool .card__title").eachText()
			.contains("Breakpoints · click a line number in Code to toggle"));
		assertEquals(List.of("main.s:3", "tables.i:1"),
			screen.document().select("#tool .breakpoint__location").eachText());
	}

	@Example
	public void shouldStepWithF8AndPauseTheMachine() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.DEBUGGER)).press("f8");

		assertEquals("stopped · plasma.s:14", screen.find("#tool .debugger__status").text());
		assertEquals("00000018", screen.find("#tool .register:nth-of-type(1) .register__value").text());
		assertEquals("▶ Run", screen.find("#machine .machine__run").text());
		assertEquals("Run · F5", screen.find("#tool .debugger__bar [hx-post=/action/run]").text());
	}

	@Example
	public void shouldStopWhereTheRunStartedOnceItComesRound() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.DEBUGGER))
			.press("f8").press("f8").press("f8").press("f10");

		assertEquals("stopped · plasma.s:13", screen.find("#tool .debugger__status").text());
	}

	@Example
	public void shouldUncheckShrinklerInTheMenuOnceItIsOffInTheTool() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.BUILD)).click("#tool [hx-vals*=SHRINKLER]");

		assertFalse(screen.find("#menus [hx-vals*=SHRINKLER]").hasClass("menu__item--checked"));
		assertFalse(rig.session().state().options().shrinkler());
	}

	@Example
	public void shouldNameEachBuildOptionAlikeInTheToolAndTheMenu() throws Exception {
		final Screen screen = Screen.open(rig).click(railTool(Tool.BUILD));

		assertEquals(List.of("Shrinkler -9", "Debug symbols"),
			screen.document().select("#tool [hx-post=/action/build-option] span:last-child").eachText());
		assertEquals(List.of("Crunch with Shrinkler -9", "Debug symbols"),
			screen.document().select("#menus [hx-post=/action/build-option] .menu__label").eachText());
	}

	@Example
	public void shouldForgetTheNoteOnTheNextAction() throws Exception {
		final Screen screen = noted(Screen.open(rig), rig).click(railTool(Tool.SPRITES));

		assertTrue(screen.document().select("#status .status__note").isEmpty());
	}

	@Example
	public void shouldForgetTheNoteOnABreakpointToggle() throws Exception {
		final Screen screen = noted(Screen.open(rig), rig).trigger(GUTTER, Map.of("line", "2"));

		assertTrue(screen.document().select("#status .status__note").isEmpty());
	}

	@Example
	public void shouldForgetTheNoteOnABuildOptionToggle() throws Exception {
		final Screen screen = noted(Screen.open(rig), rig).click("#menus [hx-vals*=SHRINKLER]");

		assertTrue(screen.document().select("#status .status__note").isEmpty());
	}

	@Example
	public void shouldForgetTheNoteOnASpritePaint() throws Exception {
		final Screen screen = noted(Screen.open(rig).click(railTool(Tool.SPRITES)), rig)
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

		assertEquals(500, screen.trigger("#tool [hx-vals*=SHRINKLER]", Map.of("option", "BOOTBLOCK")).status());
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

		assertEquals("Emulator", screen.find("#emulator .emulator__name").text());
		assertEquals("A500 · OCS · PAL 50Hz", screen.find("#emulator .emulator__machine").text());
		assertEquals("NIGHTFALL · PLASMA", screen.find("#emulator .emulator__title").text());
		assertEquals("1170", screen.find("#emulator .meter__frame").text());
	}

	@Example
	public void shouldShowTheAgaMachine() throws Exception {
		final Screen screen = Screen.open(rig).click("#machine [hx-vals*=AGA]");

		assertEquals("A1200 · AGA · PAL 50Hz", screen.find("#emulator .emulator__machine").text());
		assertEquals("320×256 · 1 bpl + copper · 24-bit AGA", screen.find("#emulator .emulator__info").text());
		assertEquals("812K / 2M", screen.find("#emulator .meter__chip").text());
	}

	@Example
	public void shouldLayThePartsOutAlongTheRun() throws Exception {
		final Screen screen = Screen.open(rig);

		assertEquals(6, screen.document().select("#timeline .track__lane .block").size());
		assertEquals("Plasma", screen.find("#timeline .block--selected").text());
		assertEquals("0:23.40", screen.find("#timeline [data-clock]").text());
		assertEquals("125 BPM · pos 03 row 03", screen.find("#timeline .timeline__tempo").text());
		assertEquals("125", screen.find("#timeline").attr("data-bpm"));
		assertEquals("0:18–0:42", screen.find("#timeline .timeline__range").text());
		assertEquals(List.of("0:00", "0:18", "0:36", "0:54", "1:12", "1:30"),
			screen.document().select("#timeline .timeline__tick").eachText());
	}

	@Example
	public void shouldDrawTheNextPartOnceThePlayheadRunsIntoIt() throws Exception {
		final Screen screen = Screen.open(rig);

		assertEquals("42000", screen.find("#timeline").attr("data-next"));
		now.set(Instant.EPOCH.plusMillis(19_000));
		screen.click("#timeline [data-boundary]");

		assertEquals("Part 04 · Vector balls", screen.find("#status .status__part").text());
		assertEquals("frame 2120", screen.find("#status .status__frame").text());
		assertEquals("NIGHTFALL · VECTOR BALLS", screen.find("#emulator .emulator__title").text());
		assertEquals("Vector balls", screen.find("#timeline .block--selected").text());
		assertEquals("68000", screen.find("#timeline").attr("data-next"));
	}

	@Example
	public void shouldChangeNothingButTheTimeToDrawTheNextPart() throws Exception {
		final Screen screen = noted(Screen.open(rig), rig);
		final StudioState before = rig.session().state();

		screen.click("#timeline [data-boundary]");

		assertEquals(before, rig.session().state());
		assertEquals("Opened demo.chp (demo)", screen.find("#status .status__note").text());
	}

	@Example
	public void shouldLeaveTheToolPaneAloneWhenDrawingTheNextPart() throws Exception {
		final Screen screen = Screen.open(rig);

		screen.find("#tool").attr("data-untouched", "true");
		screen.click("#timeline [data-boundary]");

		assertEquals("true", screen.find("#tool").attr("data-untouched"));
	}

	@Example
	public void shouldPlayFromThePartClicked() throws Exception {
		final Screen screen = Screen.open(rig).click("#timeline .track__lane .block:nth-of-type(5)");

		assertEquals("Part 05 · Greetings", screen.find("#status .status__part").text());
		assertEquals("1:08.00", screen.find("#timeline [data-clock]").text());
		assertEquals("NIGHTFALL · GREETINGS", screen.find("#emulator .emulator__title").text());
	}

	@Example
	public void shouldSeekWhereTheRulerIsClicked() throws Exception {
		final Screen screen = Screen.open(rig).trigger("#timeline .timeline__ruler", Map.of("at", "1000"));

		assertEquals("Part 01 · Loader", screen.find("#status .status__part").text());
		assertEquals("frame 50", screen.find("#status .status__frame").text());
	}

	@Example
	public void shouldHoldASeekInsideTheRun() throws Exception {
		final Screen screen = Screen.open(rig).trigger("#timeline .timeline__ruler", Map.of("at", "-5000"));

		assertEquals("frame 0", screen.find("#status .status__frame").text());
		screen.trigger("#timeline .timeline__ruler", Map.of("at", "999999"));
		assertEquals("Part 06 · End", screen.find("#status .status__part").text());
		assertEquals("1:48.00", screen.find("#timeline [data-clock]").text());
	}

	@Example
	public void shouldOfferTheExitsOfALoopingLevel() throws Exception {
		final Screen screen = open(made(ProjectKind.GAME, "arcade"));

		assertEquals("↻ Level 1 · 0:30 per pass · exits:", screen.find("#timeline .timeline__loop > span").text());
		screen.click("#timeline .timeline__exit:nth-of-type(1)");
		assertEquals("Title", screen.find("#timeline .block--selected").text());
		assertEquals("0:00.00", screen.find("#timeline [data-clock]").text());
	}

	@Example
	public void shouldHideTheTimelineWithCtrlJ() throws Exception {
		assertTrue(Screen.open(rig).press("ctrl+j").find("#timeline").hasAttr("hidden"));
	}

	@Example
	public void shouldRefuseAPartOrExitThatIsNotThere() throws Exception {
		final Screen screen = Screen.open(rig);
		final StudioState before = rig.session().state();

		assertEquals(500, screen.trigger("#timeline .track__lane .block", Map.of("index", "9")).status());
		assertEquals(before, rig.session().state());
		open(made(ProjectKind.GAME, "arcade"), screen);

		final StudioState looping = rig.session().state();

		assertEquals(500, screen.trigger("#timeline .timeline__exit", Map.of("to", "Nowhere")).status());
		assertEquals(looping, rig.session().state());
	}

	@Example
	public void shouldRefuseAMomentThatIsNoNumber() throws Exception {
		final Screen screen = Screen.open(rig);
		final StudioState before = rig.session().state();

		assertEquals(500, screen.trigger("#timeline .timeline__ruler", Map.of("at", "soon")).status());
		assertEquals(before, rig.session().state());
	}

	@Example
	public void shouldOpenTheShortcutsAndCloseThemWithEscape() throws Exception {
		final Screen screen = Screen.open(rig).press("ctrl+/");

		assertEquals("Keyboard shortcuts", screen.find("#modal .modal__title").text());
		assertTrue(screen.document().select("#modal dd").eachText().contains("Ctrl+Shift+L"));
		screen.press("escape");
		assertTrue(screen.find("#modal").hasAttr("hidden"));
		assertTrue(screen.document().select("#modal .modal__card").isEmpty());
	}

	@Example
	public void shouldDescribeTheProjectInItsSettings() throws Exception {
		final Screen screen = Screen.open(rig).click("#menus [hx-vals*=SETTINGS]");

		assertEquals("demo", screen.find("#modal [data-field][name=name]").val());
		assertTrue(screen.document().select("#modal dd").eachText().contains("demo"));
		assertTrue(screen.find("#modal .modal__rows").hasClass("modal__rows--settings"));
	}

	@Example
	public void shouldCloseADialogFromItsBackdrop() throws Exception {
		final Screen screen = Screen.open(rig).click("#menus [hx-vals*=ABOUT]").click("#modal .modal__backdrop");

		assertTrue(screen.find("#modal").hasAttr("hidden"));
	}

	/* A control that names an action nobody answers would only show as a 404 in a browser console. */
	@Example
	public void shouldBindEveryControlToAnActionThatExists() throws Exception {
		final Path arcade = made(ProjectKind.GAME, "arcade");
		final Screen screen = Screen.open(rig);
		final List<String> posted = new ArrayList<>();

		for (final Path project : List.of(rig.project().chp(), arcade)) {
			open(project, screen);

			for (final Tool tool : Tool.values()) {
				screen.click(railTool(tool));
				posted.addAll(screen.document().select("[hx-post]").eachAttr("hx-post"));
			}

			for (final String dialog : List.of("SETTINGS", "PREFERENCES", "SHORTCUTS", "ABOUT")) {
				screen.click("#menus [hx-vals*=" + dialog + "]");
				posted.addAll(screen.document().select("[hx-post]").eachAttr("hx-post"));
				screen.press("escape");
			}

			for (final String dialog : List.of("project-new", "project-open")) {
				screen.click("#menus [hx-post=\"/action/" + dialog + "\"]");
				posted.addAll(screen.document().select("[hx-post]").eachAttr("hx-post"));
				screen.press("escape");
			}
		}

		assertAll(posted.stream().distinct().map(path -> () ->
			assertTrue(rig.actions().knows(path.substring("/action/".length())), path)));
	}

	@Example
	public void shouldKeepOneOfEachRegionAfterManyChanges() throws Exception {
		final Path arcade = made(ProjectKind.GAME, "arcade");
		final Screen screen = Screen.open(rig).press("f8").press("ctrl+2")
			.click("#menus [hx-vals*=AGA]").press("ctrl+j").press("ctrl+j").press("ctrl+/").press("escape");

		open(arcade, screen);

		assertAll(Arrays.stream(Region.values()).map(region -> () ->
			assertEquals(1, screen.document().select("#" + region.id()).size(), region.id())));
		assertEquals(Region.values().length, screen.document().select("[hx-swap-oob]").size());
	}

	@Example
	public void shouldWarnOfTheLineTheAssemblerWarnsOf() throws Exception {
		final Screen screen = build(saved(Screen.open(rig), "start:\n\t" + StubTools.WARNING + "\n"));

		assertEquals("1 warning · main.s:2", screen.find("#status .status__warning").text());
		assertEquals("Built demo.adf · 7 bytes", screen.find("#status .status__note").text());
		assertTrue(screen.find("#status .status__build").text().startsWith("last build 7 bytes · "));
	}

	@Example
	public void shouldBuildCleanForAga() throws Exception {
		final Screen screen = build(Screen.open(rig).click("#machine [hx-vals*=AGA]"));

		assertTrue(screen.document().select("#status .status__warning").isEmpty());
		assertEquals("0 warnings", screen.find("#status .status__warnings").text());
		assertEquals("0 errors", screen.find("#status .status__ok").text());
	}

	@Example
	public void shouldShipUncrunchedOnceShrinklerIsOff() throws Exception {
		final Screen screen = build(Screen.open(rig).click(railTool(Tool.BUILD))
			.click("#tool [hx-vals*=SHRINKLER]"));

		assertTrue(screen.document().select("#tool .build__line").eachText().stream()
			.noneMatch(line -> line.startsWith("crunched")));
		assertTrue(screen.find("#tool .build__summary").text().startsWith("demo.adf · 7 bytes · "));
		assertFalse(screen.find("#menus [hx-vals*=SHRINKLER]").hasClass("menu__item--checked"));
	}

	@Example
	public void shouldJumpToTheLineAWarningNames() throws Exception {
		final Screen screen = build(saved(Screen.open(rig), "start:\n\tnop\n\t" + StubTools.WARNING + "\n")
			.click(railTool(Tool.SPRITES))).click("#status .status__warning");

		assertEquals("main.s", screen.find("#tool .tool__tab--active").text());
		assertEquals(3, source(screen).getInt("cursor"));
	}
}
