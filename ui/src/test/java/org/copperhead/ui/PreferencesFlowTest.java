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

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Edit → Preferences: the theme and the tools to build with, kept for every project and every launch. */
public class PreferencesFlowTest {
	private static final String SAVE = "#modal .modal__buttons .button--accent";

	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
	private WorkbenchRig rig;
	private Path vasm;

	@BeforeTry
	public void serve() throws Exception {
		rig = new WorkbenchRig(now::get);
		vasm = rig.preferences().tool("vasm").orElseThrow();
	}

	@AfterTry
	public void stop() throws Exception {
		rig.close();
	}

	private Map<String, String> chosen(final String theme, final String vasmPath) {
		return Map.of("theme", theme, "vasm", vasmPath,
			"shrinkler", rig.preferences().tool("shrinkler").orElseThrow().toString());
	}

	@Example
	public void shouldOpenThePreferencesWithCtrlComma() throws Exception {
		final Screen screen = Screen.open(rig).press("ctrl+,");

		assertEquals("Preferences", screen.find("#modal .modal__title").text());
		assertEquals("dark", screen.find("#modal select[name=theme] option[selected]").val());
		assertEquals(vasm.toString(), screen.find("#modal [data-field][name=vasm]").val());
	}

	@Example
	public void shouldKeepTheThemeAndTheToolsChosen() throws Exception {
		final Screen screen = Screen.open(rig).press("ctrl+,").trigger(SAVE, chosen("light", ""));

		assertTrue(screen.find("#modal").hasAttr("hidden"));
		assertEquals("light", screen.find("#status").attr("data-theme"));
		assertEquals("Saved preferences", screen.find("#status .status__note").text());
		assertEquals("light", rig.preferences().layout().theme());
		assertEquals(Optional.empty(), rig.preferences().tool("vasm"));
	}

	@Example
	public void shouldRefuseAToolThatIsNoProgram() throws Exception {
		final Screen screen = Screen.open(rig).press("ctrl+,").trigger(SAVE, chosen("light", "/nowhere/vasm"));

		assertEquals("vasm: /nowhere/vasm is not a program Copperhead can run",
			screen.find("#modal .modal__line").text());
		assertEquals(Optional.of(vasm), rig.preferences().tool("vasm"));
		assertEquals("dark", rig.preferences().layout().theme());
	}

	@Example
	public void shouldRefuseAToolPathThatIsNotNormalised() throws Exception {
		final Path winding = vasm.getParent().resolve("..").resolve(vasm.getParent().getFileName())
			.resolve(vasm.getFileName());
		final Screen screen = Screen.open(rig).press("ctrl+,").trigger(SAVE, chosen("light", winding.toString()));

		assertEquals("vasm: " + winding + " is not a program Copperhead can run",
			screen.find("#modal .modal__line").text());
		assertEquals(Optional.of(vasm), rig.preferences().tool("vasm"));
	}

	@Example
	public void shouldRefuseAToolThatIsAFolder() throws Exception {
		final String folder = vasm.getParent().toString();
		final Screen screen = Screen.open(rig).press("ctrl+,").trigger(SAVE, chosen("light", folder));

		assertEquals("vasm: " + folder + " is not a program Copperhead can run",
			screen.find("#modal .modal__line").text());
	}

	@Example
	public void shouldRefuseAThemeThereIsNone() throws Exception {
		final Screen screen = Screen.open(rig).press("ctrl+,").trigger(SAVE, chosen("neon", vasm.toString()));

		assertEquals("theme: neon is not a theme", screen.find("#modal .modal__line").text());
		assertEquals("dark", rig.preferences().layout().theme());
	}

	@Example
	public void shouldRefuseAPathThatCannotBeNamed() throws Exception {
		final Screen screen = Screen.open(rig).press("ctrl+,").trigger(SAVE, chosen("light", "/bin/\0vasm"));

		assertTrue(screen.find("#modal .modal__line").text().startsWith("vasm: "));
		assertEquals(Optional.of(vasm), rig.preferences().tool("vasm"));
	}

	@Example
	public void shouldRefuseAToolNamedOnlyByWhereCopperheadStarted() throws Exception {
		final Screen screen = Screen.open(rig).press("ctrl+,").trigger(SAVE, chosen("dark", "vasmm68k_mot"));

		assertEquals("vasm: vasmm68k_mot is not a program Copperhead can run",
			screen.find("#modal .modal__line").text());
	}
}
