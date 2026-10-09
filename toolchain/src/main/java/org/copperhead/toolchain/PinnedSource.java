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

import java.net.URI;
import java.util.List;

/**
 * Where a tool's source is fetched from, the SHA-256 of the archive that was reviewed, and how to build it.
 * {@code binary} is the built program's path relative to the archive's top directory.
 */
public record PinnedSource(String tool, String version, URI uri, String sha256, List<String> makeArgs, String binary) {
	public static final PinnedSource VASM = new PinnedSource("vasm", "2.0f",
		URI.create("http://sun.hasenbraten.de/vasm/release/vasm.tar.gz"),
		"c84b2de1cbb87831795fe64a85c5d9a7002a766e3a7c30b0a2d7d5e99d878f49", List.of("CPU=m68k", "SYNTAX=mot"),
		"vasmm68k_mot");
	public static final PinnedSource SHRINKLER = new PinnedSource("shrinkler", "4.7",
		URI.create("https://codeload.github.com/askeksa/Shrinkler/tar.gz/refs/tags/v4.7"),
		"513ac06190e6a5dcf3af8e5a7db2cd8f4ab22bba2ae7a4eeaae3616c450bd3a4", List.of(), "build/native/Shrinkler");

	public PinnedSource {
		makeArgs = List.copyOf(makeArgs);
	}

	public String executable() {
		return binary.substring(binary.lastIndexOf('/') + 1);
	}
}
