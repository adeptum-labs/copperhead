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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

/**
 * Shell scripts that stand in for vasm and Shrinkler. They take the real tools' arguments, write output files of a
 * chosen size where the real ones would, print what the real ones print, and note every call in
 * {@code <script>.calls} beside themselves.
 */
public final class StubTools {
	private static final String NOTE_CALL = "#!/bin/sh\necho \"$*\" >> \"$0.calls\"\n";
	/* The file after -o, and the last argument, which is the source. */
	private static final String ARGUMENTS = "out=\nprev=\nfor arg; do\n\tif [ \"$prev\" = -o ]; then out=$arg; fi\n"
		+ "\tprev=$arg\ndone\n";

	private StubTools() {
	}

	/** An assembler that writes {@code program} bytes for a program and a boot block of {@code boot} bytes. */
	public static Path vasm(final Path dir, final int program, final int boot) throws IOException {
		return script(dir, "vasm", vasmBody(program, boot, ""));
	}

	/** An assembler that also prints {@code stderr} on its error stream, as vasm prints its warnings. */
	public static Path vasm(final Path dir, final int program, final int boot, final String stderr)
		throws IOException {

		return script(dir, "vasm", vasmBody(program, boot, stderr));
	}

	/** An assembler that writes a program of {@code bytes} bytes that takes no room on the disk. */
	public static Path sparseVasm(final Path dir, final long bytes) throws IOException {
		return script(dir, "vasm", NOTE_CALL + ARGUMENTS + "truncate -s " + bytes + " \"$out\"\n");
	}

	public static Path failingVasm(final Path dir, final String stderr) throws IOException {
		return script(dir, "vasm", NOTE_CALL + "printf '%s\\n' '" + stderr + "' >&2\nexit 1\n");
	}

	/** An assembler that succeeds without writing anything. */
	public static Path silentVasm(final Path dir) throws IOException {
		return script(dir, "vasm", NOTE_CALL);
	}

	/** An assembler that reports {@code stderr} and then never finishes. */
	public static Path failingThenSleeping(final Path dir, final String stderr) throws IOException {
		return script(dir, "vasm", NOTE_CALL + "printf '%s\\n' '" + stderr + "' >&2\nsleep 60 &\nwait\n");
	}

	/** An assembler that also writes a file of its own beside the output it was asked for. */
	public static Path straying(final Path dir) throws IOException {
		return script(dir, "vasm", vasmBody(2048, 300, "") + "touch \"$(dirname \"$out\")/stray\"\n");
	}

	public static Path shrinkler(final Path dir, final int bytes) throws IOException {
		return script(dir, "Shrinkler", NOTE_CALL + "for arg; do :; done\n"
			+ "head -c " + bytes + " /dev/zero > \"$arg\"\n"
			+ "echo 'Shrinkler executable file compressor by Blueberry'\necho\n"
			+ "echo 'Final file size: " + bytes + "'\n");
	}

	public static Path failingShrinkler(final Path dir) throws IOException {
		return script(dir, "Shrinkler", NOTE_CALL + "echo 'Error while reading file build/main.bin'\nexit 1\n");
	}

	/** A tool that never finishes; it notes its own process and its child's in {@code <name>.pid}. */
	public static Path sleeping(final Path dir, final String name) throws IOException {
		return script(dir, name, NOTE_CALL + "sleep 60 &\necho $$ $! > \"$0.pid\"\nwait\n");
	}

	public static String vasmBody(final int program, final int boot, final String stderr) {
		return NOTE_CALL + ARGUMENTS + "case \"$arg\" in\n"
			+ "*bootblock.s) printf 'DOS\\000' > \"$out\"; head -c " + boot + " /dev/zero >> \"$out\" ;;\n"
			+ "*) head -c " + program + " /dev/zero > \"$out\" ;;\nesac\n"
			+ (stderr.isEmpty() ? "" : "printf '%s\\n' '" + stderr + "' >&2\n");
	}

	public static List<String> calls(final Path script) throws IOException {
		final Path calls = script.resolveSibling(script.getFileName() + ".calls");

		return Files.exists(calls) ? Files.readAllLines(calls) : List.of();
	}

	private static Path script(final Path dir, final String name, final String body) throws IOException {
		final Path script = Files.createDirectories(dir).resolve(name);

		Files.writeString(script, body);
		Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
		return script;
	}
}
