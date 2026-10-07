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

import net.jqwik.api.Example;
import org.copperhead.core.project.Target;
import org.copperhead.core.sample.SampleProjects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuildsTest {
	private static BuildReport demo(final Target target, final BuildOptions options) {
		return Builds.canned(SampleProjects.nightfall(), target, options);
	}

	@Example
	public void shouldWarnThatTheScaledIndexNeedsA68020OnOcs() {
		final BuildReport report = demo(Target.A500_OCS, BuildOptions.defaults());

		assertEquals("plasma.s:24", report.warnings().getFirst().location());
		assertTrue(report.log().stream().anyMatch(line -> line.tone() == Tone.WARN));
	}

	@Example
	public void shouldBuildCleanForAga() {
		assertTrue(demo(Target.A1200_AGA, BuildOptions.defaults()).warnings().isEmpty());
	}

	@Example
	public void shouldBuildTheGameCleanOnEitherMachine() {
		assertTrue(Builds.canned(SampleProjects.blitzball(), Target.A500_OCS, BuildOptions.defaults()).warnings()
			.isEmpty());
	}

	@Example
	public void shouldShipUncrunchedWithoutShrinkler() {
		final BuildReport report = demo(Target.A500_OCS, BuildOptions.defaults().toggle(BuildOption.SHRINKLER));

		assertEquals(7016, report.bytes());
		assertFalse(report.log().stream().anyMatch(line -> line.text().startsWith("shrinkler")));
	}

	@Example
	public void shouldCrunchWithShrinkler() {
		assertEquals(2911, demo(Target.A500_OCS, BuildOptions.defaults()).bytes());
	}

	@Example
	public void shouldPutTheSymbolsOnTheDiskWhenAskedTo() {
		final BuildReport report = demo(Target.A500_OCS, BuildOptions.defaults().toggle(BuildOption.SYMBOLS));

		assertTrue(report.log().getLast().text().contains("4 files · nightfall.sym"), report.log().getLast().text());
	}

	@Example
	public void shouldNameTheDiskAfterTheProjectAndEndOnIt() {
		final BuildReport report = Builds.canned(SampleProjects.blitzball(), Target.A500_OCS,
			BuildOptions.defaults());

		assertEquals("blitzball.adf", report.adf());
		assertEquals(Tone.OK, report.log().getLast().tone());
		assertTrue(report.log().getLast().text().contains("blitzball.adf"));
		assertEquals("vasm -m68000 -Fhunkexe main.s player.s", report.log().getFirst().text());
	}
}
