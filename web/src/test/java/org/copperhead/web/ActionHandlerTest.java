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

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Example;
import org.eclipse.jetty.server.Handler;
import org.copperhead.web.action.Action;
import org.copperhead.web.action.Actions;
import org.copperhead.web.action.Form;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ActionHandlerTest extends ServedTest {
	private final Machine machine = new Machine();

	public class Machine {
		private final List<String> disks = new ArrayList<>();

		@Action("pause-machine")
		public void onClickPauseMachine() {
			disks.clear();
		}

		@Action("insert-disk")
		public Status onClickInsertDisk(final Form form) {
			disks.add(form.get("disk"));
			return new Status(form.get("disk"));
		}

		@Action("break-machine")
		public void onClickBreakMachine() {
			throw new IllegalStateException("The machine said no");
		}

		@Action("misreport-machine")
		public String onClickMisreportMachine() {
			return "not a view";
		}

		public List<String> disks() {
			return disks;
		}
	}

	@Override
	protected Handler routes() {
		return Routes.create(templates(), token(), WindowControl.NONE, Actions.of(machine), new Page("Copperhead"));
	}

	@Example
	public void shouldCallTheAnnotatedMethod() throws Exception {
		assertEquals(204, admitted().post("/action/pause-machine").statusCode());
	}

	@Example
	public void shouldHandTheFormToTheMethod() throws Exception {
		final HttpResponse<String> response = admitted()
			.submit("/action/insert-disk", "disk=demo+disk%21");

		assertEquals(200, response.statusCode());
		assertEquals(List.of("demo disk!"), machine.disks());
	}

	@Example
	public void shouldRenderTheViewThatWasReturned() throws Exception {
		final HttpResponse<String> response = admitted()
			.submit("/action/insert-disk", "disk=x");

		assertTrue(response.body().contains("Copperhead has a disk"), response.body());
		assertTrue(!response.body().contains("<!DOCTYPE html>"),
			"a fragment should be returned rather than the whole page: " + response.body()
		);
	}

	@Example
	public void shouldRefuseAnActionItDoesNotHave() throws Exception {
		assertEquals(404, admitted().post("/action/format-machine").statusCode());
	}

	@Example
	public void shouldNotActForAnUnknownCaller() throws Exception {
		assertEquals(403, anonymous().post("/action/pause-machine").statusCode());
	}

	@Example
	public void shouldReportAFailedActionRatherThanPretendItWorked() throws Exception {
		assertEquals(500, admitted().post("/action/break-machine").statusCode());
	}

	@Example
	public void shouldReportAnActionThatAnswersWithSomethingOtherThanAView() throws Exception {
		assertEquals(500, admitted().post("/action/misreport-machine").statusCode());
	}
}
