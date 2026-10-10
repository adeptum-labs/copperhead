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

package org.copperhead.toolchain;

/**
 * The tool's host served an archive other than the pinned one. vasm replaces its release archive in place, so this
 * is usually a new release; the user decides whether to trust it, by its version and the hash that arrived.
 */
public final class HashMismatch extends ToolchainException {
	private final String tool;
	private final String version;
	private final String found;

	public HashMismatch(final String tool, final String version, final String found) {
		super(tool + " " + version + " arrived with sha-256 " + found + ", not the pinned one");
		this.tool = tool;
		this.version = version;
		this.found = found;
	}

	public String tool() {
		return tool;
	}

	public String version() {
		return version;
	}

	public String found() {
		return found;
	}
}
