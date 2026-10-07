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

package org.copperhead.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.BoundingBox;
import java.util.Map;
import net.jqwik.api.Example;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StudioIT extends BrowserTest {
	private static final Map<String, String> TABS = Map.of("Sprites", "ball.spr", "Palette & copper", "palette.pal",
		"Debugger", "Debugger", "Build & export", "Build & export", "Code", "plasma.s");
	private static final double SETTLE_MILLIS = 600;
	private static final int DRAG = 200;

	@Example
	public void shouldSwitchEveryToolFromTheRail() {
		TABS.forEach((tool, tab) -> {
			page().click("#rail [title='" + tool + "']");
			assertThat(page().locator("#tool .tool__tab--active")).hasText(tab);
		});
	}

	@Example
	public void shouldOpenMenusOnAClickAndFollowThePointer() {
		final Locator file = page().locator(".menu:has(> .menu__button:text-is('File')) .menu__list");
		final Locator edit = page().locator(".menu:has(> .menu__button:text-is('Edit')) .menu__list");

		page().click(".menu__button:text-is('File')");
		assertThat(file).isVisible();

		page().hover(".menu__button:text-is('Edit')");
		assertThat(edit).isVisible();
		assertThat(file).isHidden();

		page().keyboard().press("Escape");
		assertThat(edit).isHidden();
	}

	@Example
	public void shouldBuildWithCtrlB() {
		page().keyboard().press("Control+b");

		assertThat(page().locator("#status .status__note")).hasText("Built nightfall.adf · 2,911 bytes");
	}

	/* On a Swedish keyboard "/" is Shift+7, so Ctrl+/ arrives with Shift held. */
	@Example
	public void shouldOpenTheShortcutsWhereTheSlashNeedsShift() {
		page().evaluate("document.body.dispatchEvent(new KeyboardEvent('keydown',"
			+ " {key: '/', ctrlKey: true, shiftKey: true, bubbles: true}))");

		assertThat(page().locator("#modal .modal__title")).hasText("Keyboard shortcuts");
	}

	@Example
	public void shouldMoveThePlayheadOnlyWhileTheMachineRuns() {
		final Locator clock = page().locator("#timeline [data-clock]");
		final String before = clock.textContent();

		page().waitForTimeout(SETTLE_MILLIS);
		assertNotEquals(before, clock.textContent());

		page().evaluate("document.addEventListener('htmx:load', event => {"
			+ " if (event.detail.elt.id === 'timeline') { window.timelineLoaded = true; } })");
		page().click("#machine .machine__run");
		assertThat(page().locator("#machine .machine__run")).hasText("▶ Run");
		page().waitForFunction("window.timelineLoaded");

		final String paused = clock.textContent();

		page().waitForTimeout(SETTLE_MILLIS);
		assertEquals(paused, clock.textContent());
	}

	@Example
	public void shouldSeekWhereTheRulerIsClicked() {
		page().locator("#timeline .timeline__ruler").click(new Locator.ClickOptions().setPosition(2, 5));

		assertThat(page().locator("#status .status__part")).hasText("Part 01 · Loader");
	}

	@Example
	public void shouldKeepTheSplitItWasDraggedToAcrossAReload() {
		final BoundingBox handle = page().locator("[data-splitter=vertical]").boundingBox();
		final double x = handle.x + handle.width / 2;
		final double y = handle.y + handle.height / 2;

		page().mouse().move(x, y);
		page().mouse().down();
		page().mouse().move(x - DRAG, y);
		page().mouse().up();

		final String split = (String) page().evaluate(
			"getComputedStyle(document.querySelector('[data-workspace]')).getPropertyValue('--split')");

		page().reload();
		assertEquals(split, page().evaluate(
			"getComputedStyle(document.querySelector('[data-workspace]')).getPropertyValue('--split')"));
		assertNotEquals("", split.trim());
	}

	@Example
	public void shouldDrawTheEmulatorScreen() {
		page().waitForTimeout(SETTLE_MILLIS);

		assertTrue((Boolean) page().evaluate("() => { const canvas = document.querySelector('[data-emulator]');"
			+ " const pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;"
			+ " return pixels.some((value, index) => index % 4 !== 3 && value > 0); }"));
	}
}
