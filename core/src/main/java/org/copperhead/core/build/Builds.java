/*
    Copperhead
    Copyright © 2026 Adam Waldenberg

    This program is free software: you can redistribute it and/or modify it under the terms of the
    GNU General Public License as published by the Free Software Foundation, version 3 of the License.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
    even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along with this program.
    If not, see <http://www.gnu.org/licenses/>.
 */

package org.copperhead.core.build;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.copperhead.core.project.Project;
import org.copperhead.core.project.ProjectKind;
import org.copperhead.core.project.SourceFile;
import org.copperhead.core.project.Target;

/**
 * What a build of a sample project reports, written out ahead of time so that every view of the studio can
 * be worked before vasm and Shrinkler are run for real.
 */
public final class Builds {
	private static final BuildWarning SCALED_INDEX = new BuildWarning("plasma.s", 24,
		"scaled index needs 68020 (OCS target)");
	private static final int CRUNCHED = 2911;
	private static final int UNCRUNCHED = 7016;
	private static final Duration TOOK = Duration.ofMillis(900);
	private static final String SOURCE = ".s";

	private Builds() {
	}

	public static BuildReport canned(final Project project, final Target target, final BuildOptions options) {
		final List<BuildWarning> warnings = project.kind() == ProjectKind.DEMO && target == Target.A500_OCS
			? List.of(SCALED_INDEX)
			: List.of();
		final List<BuildLine> log = new ArrayList<>();

		log.add(new BuildLine("vasm -m68000 -Fhunkexe " + String.join(" ", project.sources().stream()
			.map(SourceFile::name).filter(name -> name.endsWith(SOURCE)).toList()), Tone.PLAIN));
		log.add(new BuildLine("  " + project.name() + ".exe  7,016 bytes", Tone.PLAIN));
		warnings.forEach(warning -> log.add(new BuildLine("warn  " + warning.location() + "  " + warning.message(),
			Tone.WARN)));

		if (options.shrinkler()) {
			log.add(new BuildLine("shrinkler -9  ->  2,911 bytes  (60.5%)", Tone.PLAIN));
		}

		log.add(new BuildLine("sprites.dat  ->  1 sprite · 64 bytes", Tone.PLAIN));
		log.add(new BuildLine("adf   " + project.adf() + "  880K · "
			+ (options.symbols() ? "4 files · " + project.name() + ".sym" : "3 files")
			+ " · bootblock checksum ok", Tone.OK));

		return new BuildReport(project.adf(), log, warnings, options.shrinkler() ? CRUNCHED : UNCRUNCHED, TOOK);
	}
}
