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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.copperhead.core.build.BuildReport;
import org.copperhead.core.build.BuildWarning;
import org.copperhead.toolchain.BuildEvent;
import org.copperhead.toolchain.PinnedSource;
import org.copperhead.ui.session.BuildService.Job;
import org.copperhead.ui.session.BuildService.Jobs;
import org.copperhead.ui.view.Tool;
import static org.copperhead.ui.Flows.finished;
import static org.copperhead.ui.Flows.polledUntil;
import static org.copperhead.ui.Flows.rail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where the tools of the build come from, fetching the missing ones, and a vasm source Copperhead does not pin. */
public class ToolchainFlowTest {
	private static final String SHA256 = "5f".repeat(32);
	private static final String CARD = "#tool .build__tool";
	private static final String FETCH = "#tool [hx-post=\"/action/toolchain-fetch\"]";

	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
	private WorkbenchRig rig;

	@BeforeTry
	public void serve() throws Exception {
		rig = new WorkbenchRig(now::get);
	}

	@AfterTry
	public void stop() throws Exception {
		rig.close();
	}

	private static String named(final PinnedSource source) {
		return source.tool() + " " + source.version();
	}

	/* Builds the way the toolchain does when the vasm it fetched is not the pinned one: it asks, and only builds
	   with an accepted source; a refused one ends the build failed. */
	private static Jobs asking(final Jobs jobs) {
		return (request, trust, sink) -> {
			final Job job = jobs.create(request, trust, sink);

			return new Job() {
				@Override
				public void run() {
					try {
						if (trust.accept("vasm", "2.0g", SHA256)) {
							job.run();
							return;
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}

					sink.accept(new BuildEvent.Finished(new BuildReport("", List.of(), List.of(),
						List.of(new BuildWarning("", 0, "vasm source refused")), 0, Duration.ZERO)));
				}

				@Override
				public void cancel() {
					job.cancel();
				}
			};
		};
	}

	private Screen asked() throws Exception {
		rig.close();
		rig = WorkbenchRig.building(now::get, ToolchainFlowTest::asking);

		return polledUntil(Screen.open(rig).trigger("[data-key=\"ctrl+b\"]", Map.of()),
			screen -> !screen.document().select("#modal .modal__card").isEmpty());
	}

	@Example
	public void shouldShowWhereEachToolOfTheBuildIsFound() throws Exception {
		final Screen screen = Screen.open(rig).click(rail(Tool.BUILD));

		assertEquals(List.of(named(PinnedSource.VASM), named(PinnedSource.SHRINKLER)),
			screen.document().select(CARD + "-name").eachText());
		assertEquals(List.of("found", "found"), screen.document().select(CARD + "-state").eachText());
		assertEquals(rig.preferences().tool("vasm").orElseThrow().toString(), screen.find(CARD + "-where").text());
		assertTrue(screen.find(FETCH).hasAttr("disabled"));
	}

	@Example
	public void shouldOfferToFetchAToolFoundNowhere() throws Exception {
		rig.preferences().tool("vasm", Optional.empty());

		final Screen screen = Screen.open(rig).click(rail(Tool.BUILD));

		assertEquals(List.of("will fetch", "found"), screen.document().select(CARD + "-state").eachText());
		assertFalse(screen.find(FETCH).hasAttr("disabled"));
	}

	@Example
	public void shouldSayWhatAFetchNeedsBeforeItCanCompile() throws Exception {
		rig.preferences().tool("vasm", Optional.empty());

		final Screen screen = finished(Screen.open(rig).click(rail(Tool.BUILD)).click(FETCH));

		assertTrue(screen.document().select("#tool .build__line--err").text()
			.contains("sudo apt install build-essential"));
		assertEquals("will fetch", screen.find(CARD + "-state").text());
	}

	@Example
	public void shouldAskBeforeBuildingWithAVasmItDoesNotPin() throws Exception {
		final Screen screen = asked();

		assertEquals("Unexpected vasm source", screen.find("#modal .modal__title").text());
		assertEquals(List.of("2.0g", SHA256), screen.document().select("#modal dd").eachText());
		screen.click("#modal .modal__buttons .button:containsOwn(Accept)");
		assertTrue(screen.find("#modal").hasAttr("hidden"));
		assertEquals(Optional.of(SHA256), rig.preferences().trustedVasm());
		assertEquals("Built demo.adf · 7 bytes", finished(screen).find("#status .status__note").text());
	}

	@Example
	public void shouldBuildNothingWithARefusedVasm() throws Exception {
		final Screen screen = finished(asked().click("#modal .modal__buttons .button:containsOwn(Cancel)"));

		assertEquals("build failed · 1 error", screen.find("#status .status__error").text());
		assertEquals(Optional.empty(), rig.preferences().trustedVasm());
	}

	@Example
	public void shouldRefuseTheVasmWhenTheQuestionIsClosed() throws Exception {
		final Screen screen = finished(asked().press("escape"));

		assertTrue(screen.find("#modal").hasAttr("hidden"));
		assertEquals("build failed · 1 error", screen.find("#status .status__error").text());
		assertEquals(Optional.empty(), rig.preferences().trustedVasm());
	}
}
