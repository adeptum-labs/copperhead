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

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import java.util.stream.Stream;
import net.jqwik.api.Example;
import org.copperhead.ui.session.ProjectSession;
import org.copperhead.ui.view.Modal;
import org.copperhead.ui.view.Regions;
import org.copperhead.ui.view.Tool;
import org.copperhead.web.action.ActionExtension;
import org.copperhead.web.action.Actions;
import org.copperhead.web.action.Form;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContainerTest {
	@Example
	public void shouldFindTheControllersAndShareOneSessionBetweenThem() {
		try (SeContainer container = SeContainerInitializer.newInstance().initialize()) {
			final Actions actions = discovered(container);

			assertInstanceOf(Regions.class, actions.invoke("tool", Form.parse("tool=SPRITES")).orElseThrow());
			assertEquals(Tool.SPRITES, container.select(ProjectSession.class).get().state().tool());
		}
	}

	/**
	 * The session is built from the preferences, which the Maven build keeps inside its target folder. The build
	 * service is created only when first used, so the tool card is opened to run its production constructor; what
	 * its shutdown does on close leaves nothing to observe while no build runs.
	 */
	@Example
	public void shouldWireProjectsEditingAndBuildsIntoTheSameSession() {
		try (SeContainer container = SeContainerInitializer.newInstance().initialize()) {
			final Actions actions = discovered(container);

			assertTrue(Stream.of("project-new", "project-open", "project-recent", "project-create", "browse",
				"file-open", "file-save", "file-save-all", "source-check", "build", "build-run",
				"build-progress", "build-cancel", "trust-accept", "trust-refuse", "toolchain-fetch",
				"preferences-save", "layout", "theme").allMatch(actions::knows));
			assertInstanceOf(Regions.class,
				actions.invoke("modal", Form.parse("kind=PREFERENCES")).orElseThrow());
			assertEquals(Modal.PREFERENCES, container.select(ProjectSession.class).get().state().modal());
			assertInstanceOf(Regions.class, actions.invoke("tool", Form.parse("tool=BUILD")).orElseThrow());
			assertFalse(container.select(ProjectSession.class).get().state().environment().tools().isEmpty());
		}
	}

	private static Actions discovered(final SeContainer container) {
		return Actions.discovered(container.getBeanManager().getExtension(ActionExtension.class));
	}
}
