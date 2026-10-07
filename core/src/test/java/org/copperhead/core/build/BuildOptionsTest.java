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

package org.copperhead.core.build;

import java.util.Arrays;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class BuildOptionsTest {
	@Example
	public void shouldCrunchAndBootButLeaveSymbolsOutAtFirst() {
		assertEquals(new BuildOptions(true, true, false), BuildOptions.defaults());
	}

	@Property
	public void shouldFlipOnlyTheOptionToggled(@ForAll final BuildOption toggled) {
		final BuildOptions before = BuildOptions.defaults();
		final BuildOptions after = before.toggle(toggled);

		assertNotEquals(before.on(toggled), after.on(toggled));
		Arrays.stream(BuildOption.values()).filter(option -> option != toggled)
			.forEach(option -> assertEquals(before.on(option), after.on(option), option.name()));
	}
}
