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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Example;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuildReportTest {
	private static final BuildWarning UNKNOWN = new BuildWarning("src/main.s", 6, "unknown mnemonic <foo>");

	private static BuildReport report(final String adf, final List<BuildWarning> errors) {
		return new BuildReport(adf, List.of(), List.of(), errors, 0, Duration.ZERO);
	}

	@Example
	public void shouldComeOffWithADiskAndNoError() {
		assertTrue(report("nightfall.adf", List.of()).ok());
	}

	@Example
	public void shouldFailOnAnyError() {
		assertFalse(report("nightfall.adf", List.of(UNKNOWN)).ok());
	}

	@Example
	public void shouldFailWhenNoDiskCameOut() {
		assertFalse(report("", List.of()).ok());
	}

	@Example
	public void shouldKeepTheErrorsItWasGiven() {
		final List<BuildWarning> errors = new ArrayList<>(List.of(UNKNOWN));
		final BuildReport report = report("", errors);

		errors.clear();
		assertEquals(List.of(UNKNOWN), report.errors());
	}
}
