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

package org.copperhead.core.project;

import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TargetTest {
	@Example
	public void shouldNameTheMachineAndItsChipset() {
		assertEquals("A500 · OCS", Target.A500_OCS.label());
		assertEquals("A1200 · AGA", Target.A1200_AGA.label());
	}

	@Example
	public void shouldOfferTheColoursItsChipsetHolds() {
		assertEquals(32, Target.A500_OCS.colours());
		assertEquals(256, Target.A1200_AGA.colours());
	}

	@Example
	public void shouldBeFoundByItsChipset() {
		assertEquals(Target.A1200_AGA, Target.chipset("AGA"));
		assertThrows(IllegalArgumentException.class, () -> Target.chipset("ECS"));
	}
}
