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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Shell scripts standing in for vasm and Shrinkler, so a build goes through every step without the real tools.
 * The vasm stand-in writes a small program, or a bootblock when that is what it assembles; a line of the source
 * holding {@link #ERROR} fails it with an error on that line, one holding {@link #WARNING} warns of it, and one
 * holding {@link #SLOW} keeps it busy long enough to be cancelled, for as many seconds as follow it on the line.
 * The Shrinkler stand-in copies what it is given.
 */
public record StubTools(Path vasm, Path shrinkler) {
	public static final String ERROR = "BOGUS";
	public static final String WARNING = "WOBBLY";
	public static final String SLOW = "SLOWLY";

	private static final String VASM = """
		#!/bin/sh
		out=
		source=
		while [ $# -gt 0 ]; do
			case "$1" in
				-o) out="$2"; shift ;;
				-L) shift ;;
				-*) ;;
				*) source="$1" ;;
			esac
			shift
		done
		case "$source" in
			*bootblock.s) printf 'DOS\\000' > "$out"; exit 0 ;;
		esac
		if grep -q SLOWLY "$source"; then
			delay=$(sed -n 's/.*SLOWLY *//p' "$source" | head -n 1)
			sleep "${delay:-30}"
		fi
		line=$(grep -n BOGUS "$source" | head -n 1 | cut -d: -f1)
		if [ -n "$line" ]; then
			echo "error 2 in line $line of \\"$source\\": unknown mnemonic <BOGUS>" >&2
			echo ">	BOGUS" >&2
			exit 1
		fi
		line=$(grep -n WOBBLY "$source" | head -n 1 | cut -d: -f1)
		if [ -n "$line" ]; then
			echo "warning 62 in line $line of \\"$source\\": WOBBLY is not an instruction" >&2
		fi
		printf 'program' > "$out"
		""";
	private static final String SHRINKLER = """
		#!/bin/sh
		while [ $# -gt 2 ]; do shift; done
		cp "$1" "$2"
		echo "Final file size: $(wc -c < "$2" | tr -d ' ')"
		""";

	public static StubTools write(final Path folder) throws IOException {
		Files.createDirectories(folder);
		return new StubTools(script(folder.resolve("vasm"), VASM), script(folder.resolve("shrinkler"), SHRINKLER));
	}

	private static Path script(final Path path, final String text) throws IOException {
		Files.writeString(path, text);
		Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
		return path;
	}
}
