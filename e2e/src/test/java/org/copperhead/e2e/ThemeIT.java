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

package org.copperhead.e2e;

import net.jqwik.api.Example;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class ThemeIT extends BrowserTest {
	private static final String THEME = "data-theme";
	private static final String LIGHT = "light";

	@Example
	public void shouldStartDark() {
		assertThat(page().locator("html")).hasAttribute(THEME, "dark");
	}

	@Example
	public void shouldKeepTheChosenThemeAcrossARestart() throws Exception {
		page().click(".titlebar__theme");
		assertThat(page().locator("html")).hasAttribute(THEME, LIGHT);

		awaitSaved(layout -> LIGHT.equals(layout.theme()));
		restart();
		assertThat(page().locator("html")).hasAttribute(THEME, LIGHT);
	}

	@Example
	public void shouldSwitchTheThemeWithCtrlShiftL() {
		page().keyboard().press("Control+Shift+L");

		assertThat(page().locator("html")).hasAttribute(THEME, LIGHT);
	}

	@Example
	public void shouldSwitchTheThemeFromTheViewMenu() {
		page().click(".menu__button:text-is('View')");
		page().click(".menu__item:has-text('Light mode')");

		assertThat(page().locator("html")).hasAttribute(THEME, LIGHT);
	}
}
