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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Where a program is loaded and started, written the way the project file and the assembler spell it. */
public final class LoadAddress {
	private static final Pattern WRITTEN = Pattern.compile("(?:\\$|0[xX])(\\p{XDigit}{1,8})|(\\d{1,10})");
	private static final int HEX = 16;
	/* Below $400 lie the 68000's exception vectors; past $FFFFFC the 24-bit address bus ends. */
	private static final long LOWEST = 0x400;
	private static final long HIGHEST = 0xFF_FFFC;
	private static final int ALIGNMENT = 4;

	private LoadAddress() {
	}

	public static int parse(final String text) {
		final Matcher written = WRITTEN.matcher(text.strip());

		if (!written.matches()) {
			throw new IllegalArgumentException("not an address: " + text);
		}

		return checked(written.group(1) == null ? Long.parseLong(written.group(2))
			: Long.parseLong(written.group(1), HEX));
	}

	public static String format(final int address) {
		return "$" + Integer.toHexString(checked(address)).toUpperCase(Locale.ROOT);
	}

	private static int checked(final long address) {
		if (address < LOWEST || address > HIGHEST || address % ALIGNMENT != 0) {
			throw new IllegalArgumentException(String.format(Locale.ROOT,
				"load address $%X is not a multiple of %d between $%X and $%X", address, ALIGNMENT, LOWEST,
				HIGHEST));
		}

		return (int) address;
	}
}
