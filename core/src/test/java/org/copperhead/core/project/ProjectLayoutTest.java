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

package org.copperhead.core.project;

import java.util.stream.Stream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProjectLayoutTest {
	@Example
	public void shouldNameTheProjectFileAndTheDiskAfterTheProject() {
		assertEquals("nightfall.chp", ProjectLayout.chp("nightfall"));
		assertEquals("nightfall.adf", ProjectLayout.adf("nightfall"));
	}

	@Property
	public void shouldAcceptAnyNameOfLettersDigitsDashesAndUnderscores(@ForAll("names") final String name) {
		assertTrue(ProjectLayout.validName(name), name);
	}

	@Provide
	public Arbitrary<String> names() {
		return Combinators.combine(Arbitraries.strings().alpha().numeric().ofLength(1),
			Arbitraries.strings().alpha().numeric().withChars('_', '-').ofMaxLength(63)).as(String::concat);
	}

	@Example
	public void shouldRefuseANameAPathWouldReadOtherwise() {
		assertAll(Stream.of("", "-demo", "_demo", ".demo", "my demo", "demo/x", "..", "demo.chp", "démo",
			"a".repeat(65)).map(name -> () -> assertFalse(ProjectLayout.validName(name), name)));
	}

	@Example
	public void shouldAcceptSourcesAndIncludesInTheSourceDirectory() {
		assertAll(Stream.of("main.s", "sprite.i", "_x.s", "intro-2.s", "part.one.s")
			.map(source -> () -> assertTrue(ProjectLayout.validSource(source), source)));
	}

	@Example
	public void shouldRefuseWhatIsNoSourceOrLiesElsewhere() {
		assertAll(Stream.of("", ".s", ".hidden.s", "main", "main.S", "main.asm", "main.s.bak", "src/main.s",
			"../main.s", "main.s/", "C:main.s")
			.map(source -> () -> assertFalse(ProjectLayout.validSource(source), source)));
	}
}
