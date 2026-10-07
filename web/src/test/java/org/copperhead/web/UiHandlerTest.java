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

package org.copperhead.web;

import java.util.concurrent.atomic.AtomicInteger;
import net.jqwik.api.Example;
import org.eclipse.jetty.server.Handler;
import org.copperhead.web.action.Actions;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UiHandlerTest extends ServedTest {
	private final AtomicInteger visits = new AtomicInteger();

	@Override
	protected Handler routes() {
		return Routes.create(templates(), token(), WindowControl.NONE, Actions.of(),
			() -> new Page("Visit " + visits.incrementAndGet())
		);
	}

	/* What the page opens on can change while Copperhead runs, such as once another project is opened. */
	@Example
	public void shouldWorkOutThePageAgainForEveryRequest() throws Exception {
		final Client client = admitted();

		assertTrue(client.get("/").body().contains("Visit 2"));
		assertTrue(client.get("/").body().contains("Visit 3"));
	}
}
