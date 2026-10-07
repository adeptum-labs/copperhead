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

package org.copperhead.ui;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import net.jqwik.api.Example;
import org.copperhead.ui.session.ProjectSession;
import org.copperhead.ui.view.Regions;
import org.copperhead.ui.view.Tool;
import org.copperhead.web.action.ActionExtension;
import org.copperhead.web.action.Actions;
import org.copperhead.web.action.Form;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ContainerTest {
	@Example
	public void shouldFindTheControllersAndShareOneSessionBetweenThem() {
		try (SeContainer container = SeContainerInitializer.newInstance().initialize()) {
			final Actions actions = Actions.discovered(
				container.getBeanManager().getExtension(ActionExtension.class)
			);

			assertInstanceOf(Regions.class, actions.invoke("tool", Form.parse("tool=SPRITES")).orElseThrow());
			assertEquals(Tool.SPRITES, container.select(ProjectSession.class).get().state().tool());
		}
	}
}
