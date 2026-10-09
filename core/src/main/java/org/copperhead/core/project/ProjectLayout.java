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

import java.util.regex.Pattern;

/** Where a project keeps its files, and which names it accepts for itself and its sources. */
public final class ProjectLayout {
	public static final String SRC = "src";
	public static final String GFX = "gfx";
	public static final String BUILD = "build";
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
	private static final Pattern SOURCE = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9_.-]*\\.[si]");

	private ProjectLayout() {
	}

	public static String chp(final String name) {
		return name + ".chp";
	}

	public static String adf(final String name) {
		return name + ".adf";
	}

	/* A name becomes a directory and two file names, so it may hold nothing a path would read as a separator. */
	public static boolean validName(final String name) {
		return NAME.matcher(name).matches();
	}

	public static boolean validSource(final String source) {
		return SOURCE.matcher(source).matches();
	}
}
