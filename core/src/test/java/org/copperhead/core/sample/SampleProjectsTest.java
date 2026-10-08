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

package org.copperhead.core.sample;

import java.time.Duration;
import java.util.List;
import net.jqwik.api.Example;
import org.copperhead.core.project.Part;
import org.copperhead.core.project.Project;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SampleProjectsTest {
	private static final List<Project> PROJECTS = List.of(SampleProjects.nightfall(), SampleProjects.blitzball());

	@Example
	public void shouldStepOnlyThroughLinesOfTheTracedFile() {
		assertAll(PROJECTS.stream().map(project -> () -> {
			final int length = project.source(project.trace().file()).length();

			assertTrue(project.trace().lines().stream().allMatch(line -> line >= 1 && line <= length),
				project.name());
		}));
	}

	@Example
	public void shouldStopAtABreakpointTheRunReaches() {
		assertAll(PROJECTS.stream().map(project -> () ->
			assertTrue(project.trace().lines().contains(project.breakpoint()), project.name())));
	}

	@Example
	public void shouldCoverTheWholeRunWithPartsEndToEnd() {
		assertAll(PROJECTS.stream().map(project -> () -> {
			final List<Part> parts = project.parts();

			assertEquals(Duration.ZERO, parts.getFirst().start(), project.name());
			assertEquals(project.length(), parts.getLast().end(), project.name());

			for (int i = 1; i < parts.size(); i++) {
				assertEquals(parts.get(i - 1).end(), parts.get(i).start(), project.name() + " part " + i);
			}
		}));
	}

	@Example
	public void shouldLeaveOnlyForPartsThatExist() {
		assertAll(PROJECTS.stream().flatMap(project -> project.parts().stream()
			.flatMap(part -> part.exits().stream()).map(exit -> () -> project.part(exit.to()))));
	}

	@Example
	public void shouldFindThePartPlayingAtAMoment() {
		final Project nightfall = SampleProjects.nightfall();

		assertEquals(2, nightfall.partAt(Duration.ofMillis(23_400)));
		assertEquals(0, nightfall.partAt(Duration.ZERO));
		assertEquals(5, nightfall.partAt(nightfall.length()));
	}

	@Example
	public void shouldOpenEitherSampleByName() {
		assertEquals("nightfall.chp", SampleProjects.named("nightfall").file());
		assertEquals("blitzball.chp", SampleProjects.named("blitzball").file());
	}

	@Example
	public void shouldRefuseAProjectItDoesNotHave() {
		assertThrows(IllegalArgumentException.class, () -> SampleProjects.named("nowhere"));
	}

	@Example
	public void shouldNameTheDiskAfterTheProject() {
		assertEquals("blitzball.adf", SampleProjects.blitzball().adf());
	}
}
