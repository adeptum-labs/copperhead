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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.copperhead.core.build.AssemblerMessage.Severity;

/** Picks the warnings and errors out of what vasm prints; the lines around them belong to the plain log. */
public final class VasmLog {
	/* Inside a macro vasm names the macro first and then, in brackets, the line of the file that defined it. */
	private static final Pattern MESSAGE = Pattern.compile("(?:fatal )?(?<kind>error|warning) \\d+"
		+ "(?: in line (?<line>\\d+) of \"(?<file>[^\"]*)\""
		+ "(?: \\(line (?<definedLine>\\d+) of \"(?<definedIn>[^\"]*)\"\\))?)?: (?<text>.*)");

	private VasmLog() {
	}

	public static List<AssemblerMessage> parse(final List<String> lines) {
		return lines.stream().map(MESSAGE::matcher).filter(Matcher::matches).map(VasmLog::message).toList();
	}

	private static AssemblerMessage message(final Matcher found) {
		final boolean inMacro = found.group("definedIn") != null;
		final String file = found.group(inMacro ? "definedIn" : "file");
		final String line = found.group(inMacro ? "definedLine" : "line");

		return new AssemblerMessage("warning".equals(found.group("kind")) ? Severity.WARNING : Severity.ERROR,
			file == null ? "" : file, line == null ? 0 : Integer.parseInt(line), found.group("text"));
	}
}
