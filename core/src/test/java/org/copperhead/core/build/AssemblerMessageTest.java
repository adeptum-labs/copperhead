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

package org.copperhead.core.build;

import net.jqwik.api.Example;
import org.copperhead.core.build.AssemblerMessage.Severity;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AssemblerMessageTest {
	@Example
	public void shouldBeFoundByFileAndLine() {
		assertEquals("src/main.s:4", new AssemblerMessage(Severity.ERROR, "src/main.s", 4, "unknown mnemonic <foo>")
			.location());
	}

	@Example
	public void shouldHaveNoLocationWhenItNamesNoFile() {
		assertEquals("", new AssemblerMessage(Severity.ERROR, "", 0, "could not open <src/main.s> for input")
			.location());
	}
}
