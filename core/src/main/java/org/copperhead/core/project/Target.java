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

import java.util.Arrays;

/** The machine a build is made for, and what its chipset gives a demo to work with. */
public enum Target {
	A500_OCS("A500", "OCS", 32, 0x8_0000, "212K", "812K", "512K chip + 512K slow"),
	A1200_AGA("A1200", "AGA", 256, 0x20_0000, "1.2M", "812K / 2M", "2M chip");

	private final String machine;
	private final String chipset;
	private final int colours;
	private final int chipEnd;
	private final String chipFree;
	private final String chipUsed;
	private final String memory;

	Target(final String machine, final String chipset, final int colours, final int chipEnd, final String chipFree,
		final String chipUsed, final String memory) {

		this.machine = machine;
		this.chipset = chipset;
		this.colours = colours;
		this.chipEnd = chipEnd;
		this.chipFree = chipFree;
		this.chipUsed = chipUsed;
		this.memory = memory;
	}

	public static Target chipset(final String chipset) {
		return Arrays.stream(values()).filter(target -> target.chipset.equals(chipset)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("No target has the chipset " + chipset));
	}

	public String machine() {
		return machine;
	}

	public String chipset() {
		return chipset;
	}

	public int colours() {
		return colours;
	}

	/** The first address past chip RAM: a trackloaded program and its crunched copy must both end below it. */
	public int chipEnd() {
		return chipEnd;
	}

	public String chipFree() {
		return chipFree;
	}

	public String chipUsed() {
		return chipUsed;
	}

	public String memory() {
		return memory;
	}

	public String label() {
		return machine + " · " + chipset;
	}
}
