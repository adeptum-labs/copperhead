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

package org.copperhead.ui.session;

import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LayoutTest {
	@Example
	public void shouldStartDarkWithTheToolPaneTakingMoreThanHalf() {
		assertEquals(new Layout("dark", 56, 250), Layout.defaults());
	}

	@Property
	public void shouldKeepTheToolPaneWhereTheWindowCanShowIt(@ForAll final int split) {
		final int kept = Layout.defaults().withSplit(split).split();

		assertTrue(kept >= 36 && kept <= 78, String.valueOf(kept));
	}

	@Property
	public void shouldKeepTheTimelineWhereTheWindowCanShowIt(@ForAll final int bottom) {
		final int kept = Layout.defaults().withBottom(bottom).bottom();

		assertTrue(kept >= 120 && kept <= 460, String.valueOf(kept));
	}

	@Example
	public void shouldKeepASizeTheWindowCanShowAsItIs() {
		assertEquals(new Layout("light", 40, 300),
			Layout.defaults().withTheme("light").withSplit(40).withBottom(300));
	}

	@Example
	public void shouldRefuseAThemeItDoesNotHave() {
		assertThrows(IllegalArgumentException.class, () -> Layout.defaults().withTheme("purple"));
	}
}
