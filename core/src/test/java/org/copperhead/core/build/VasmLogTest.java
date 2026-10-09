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

import java.util.List;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.Chars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NumericChars;
import net.jqwik.api.constraints.StringLength;
import org.copperhead.core.build.AssemblerMessage.Severity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each transcript is what vasm 2.0f printed on stderr, one entry per line. */
public class VasmLogTest {
	private static final String UNKNOWN = "unknown mnemonic <foo>";

	@Example
	public void shouldReadAnErrorAtTheLineItNames() {
		final List<String> printed = List.of("", "error 2 in line 6 of \"src/main.s\": " + UNKNOWN, ">\tfoo d1");

		assertEquals(List.of(new AssemblerMessage(Severity.ERROR, "src/main.s", 6, UNKNOWN)),
			VasmLog.parse(printed));
	}

	@Example
	public void shouldPlaceAnErrorInAMacroWhereTheMacroIsWritten() {
		final List<String> printed = List.of("",
			"error 2 in line 1 of \"mymac\" (line 2 of \"src/main.s\"): " + UNKNOWN,
			"\tcalled from line 7 of \"src/main.s\": \tmymac", ">\tfoo d0");

		assertEquals(List.of(new AssemblerMessage(Severity.ERROR, "src/main.s", 2, UNKNOWN)),
			VasmLog.parse(printed));
	}

	@Example
	public void shouldReadAWarningInAnIncludedFile() {
		final List<String> printed = List.of("",
			"warning 51 in line 2 of \"src/inc.i\": instruction has been auto-aligned",
			"\tincluded from line 2 of \"src/main.s\"", ">\tnop");

		assertEquals(List.of(new AssemblerMessage(Severity.WARNING, "src/inc.i", 2,
			"instruction has been auto-aligned")), VasmLog.parse(printed));
	}

	@Example
	public void shouldTakeAFatalErrorForAnErrorAtItsLine() {
		final String unsupported = "instruction not supported on selected architecture";
		final List<String> printed = List.of("", "fatal error 2001 in line 2 of \"src/main.s\": " + unsupported,
			">\tlea 4(a0,d0.w*4),a1", "aborting...");

		assertEquals(List.of(new AssemblerMessage(Severity.ERROR, "src/main.s", 2, unsupported)),
			VasmLog.parse(printed));
	}

	@Example
	public void shouldTakeAFatalErrorWithoutALineForAnErrorOfTheWholeRun() {
		final String unopened = "could not open <src/main.s> for input";
		final List<String> printed = List.of("", "fatal error 13: " + unopened, "aborting...");

		assertEquals(List.of(new AssemblerMessage(Severity.ERROR, "", 0, unopened)), VasmLog.parse(printed));
	}

	@Example
	public void shouldKeepTheMessagesInTheOrderVasmPrintedThem() {
		final List<String> printed = List.of("", "error 2 in line 6 of \"src/main.s\": " + UNKNOWN, ">\tfoo d1", "",
			"error 2 in line 1 of \"mymac\" (line 2 of \"src/main.s\"): " + UNKNOWN,
			"\tcalled from line 7 of \"src/main.s\": \tmymac", ">\tfoo d0");

		assertEquals(List.of("src/main.s:6", "src/main.s:2"),
			VasmLog.parse(printed).stream().map(AssemblerMessage::location).toList());
	}

	@Example
	public void shouldFindNoMessageInTheLinesAroundThem() {
		assertTrue(VasmLog.parse(List.of("", ">\terror 2 in line 6 of \"src/main.s\": " + UNKNOWN, "aborting...",
			"***maximum number of errors reached!***", "\tincluded from line 2 of \"src/main.s\"",
			"vasm 2.0f (c) in 2002-2026 Volker Barthelmann", "message 1: nothing wrong")).isEmpty());
	}

	@Property
	public void shouldReadBackAnyMessageInTheFormVasmWritesIt(@ForAll final Severity severity,
		@ForAll @AlphaChars @NumericChars @Chars({'_', '-'}) @StringLength(min = 1, max = 20) final String name,
		@ForAll @IntRange(min = 1, max = 1_000_000) final int line,
		@ForAll @AlphaChars @Chars({' ', ':', '<', '>', '"', '(', ')', '\t'}) @StringLength(max = 60)
		final String text) {

		final String file = "src/" + name + ".s";
		final String kind = severity == Severity.WARNING ? "warning" : "error";

		assertEquals(List.of(new AssemblerMessage(severity, file, line, text)), VasmLog.parse(List.of("",
			kind + " 7 in line " + line + " of \"" + file + "\": " + text, ">" + text)));
	}
}
