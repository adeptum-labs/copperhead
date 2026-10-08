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

public record BuildOptions(boolean shrinkler, boolean bootblock, boolean symbols) {
	public static BuildOptions defaults() {
		return new BuildOptions(true, true, false);
	}

	public boolean on(final BuildOption option) {
		return switch (option) {
			case SHRINKLER -> shrinkler;
			case BOOTBLOCK -> bootblock;
			case SYMBOLS -> symbols;
		};
	}

	public BuildOptions toggle(final BuildOption option) {
		return new BuildOptions(shrinkler ^ (option == BuildOption.SHRINKLER),
			bootblock ^ (option == BuildOption.BOOTBLOCK), symbols ^ (option == BuildOption.SYMBOLS));
	}
}
