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
import java.nio.file.Path;
import java.util.List;
import net.jqwik.api.Example;
import org.eclipse.jetty.server.Handler;
import org.copperhead.web.action.Actions;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class WindowHandlerTest extends ServedTest {
	private final RecordingWindow window = new RecordingWindow();

	@Override
	protected Handler routes() {
		return Routes.create(templates(), token(), window, Actions.of(), new Page("Copperhead"));
	}

	@Example
	public void shouldForwardEachCommandToTheHost() throws Exception {
		final Client client = admitted();

		assertEquals(204, client.post("/window/minimise").statusCode());
		assertEquals(204, client.post("/window/maximise").statusCode());
		assertEquals(204, client.post("/window/close").statusCode());
		assertEquals(204, client.post("/window/move/start").statusCode());
		assertEquals(204, client.post("/window/move/end").statusCode());
		assertEquals(List.of("minimise", "maximise", "close", "move/start", "move/end"), window.commands());
	}

	@Example
	public void shouldForwardEachResizableEdgeToTheHost() throws Exception {
		final Client client = admitted();

		assertEquals(204, client.post("/window/resize/start/left").statusCode());
		assertEquals(204, client.post("/window/resize/start/right").statusCode());
		assertEquals(204, client.post("/window/resize/start/bottom").statusCode());
		assertEquals(204, client.post("/window/resize/start/bottom-right").statusCode());
		assertEquals(204, client.post("/window/resize/end").statusCode());
		assertEquals(List.of(
			"resize/start/left", "resize/start/right", "resize/start/bottom", "resize/start/bottom-right",
			"resize/end"
		), window.commands());
	}

	@Example
	public void shouldAnswerWithTheFileTheHostChose() throws Exception {
		final Path disk = Path.of("/mnt/backup/demo.adf");

		window.picking(disk);

		final HttpResponse<String> response = admitted().post("/window/choose-file?title=Pick+a+disk");

		assertEquals(200, response.statusCode());
		assertEquals(disk.toString(), response.body());
		assertEquals(List.of("choose-file:Pick a disk"), window.commands());
	}

	@Example
	public void shouldAnswerWithNothingWhenTheDialogWasDismissed() throws Exception {
		final HttpResponse<String> response = admitted().post("/window/choose-file?title=Pick+a+disk");

		assertEquals(204, response.statusCode());
		assertEquals("", response.body());
	}

	@Example
	public void shouldNotInventCommandsItDoesNotHave() throws Exception {
		admitted().post("/window/selfdestruct");
		admitted().post("/window/resize/start/top");
		assertEquals(List.of(), window.commands());
	}

	@Example
	public void shouldNotActForAnUnknownCaller() throws Exception {
		assertEquals(403, anonymous().post("/window/close").statusCode());
		assertEquals(List.of(), window.commands());
	}

	@Example
	public void shouldAnswerWithWhereTheHostWillSave() throws Exception {
		final Path history = Path.of("/home/ann/history.csv");

		window.picking(history);

		final HttpResponse<String> response = admitted()
			.post("/window/save-file?title=Save+the+history&name=history.csv");

		assertEquals(200, response.statusCode());
		assertEquals(history.toString(), response.body());
		assertEquals(List.of("save-file:Save the history:history.csv"), window.commands());
	}

	@Example
	public void shouldAnswerWithNothingWhenSavingWasCancelled() throws Exception {
		assertEquals(204, admitted().post("/window/save-file?title=Save&name=a.csv").statusCode());
	}
}
