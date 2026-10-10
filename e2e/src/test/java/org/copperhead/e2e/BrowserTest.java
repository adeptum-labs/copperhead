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

package org.copperhead.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.copperhead.ui.WorkbenchRig;
import org.copperhead.ui.session.Layout;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The studio opened in headless Chromium, the engine the window embeds. The window only ever reaches its host
 * through requests, so a browser pointed at the served page runs the same scripts the window does, without a
 * frame opening on anybody's desktop. Each try gets a home folder of its own, holding the demo project the studio
 * opens and stand-ins for vasm and Shrinkler, so a build runs without fetching anything.
 */
public abstract class BrowserTest {
	/* Chromium reports the 204 answers of the window controls as aborted although the page received them, so
	   only the other failures say anything. */
	private static final String ABORTED = "net::ERR_ABORTED";
	private static final int FIRST_ERROR = 400;
	private static final int AWAIT_TRIES = 50;
	private static final double AWAIT_POLL_MILLIS = 100;

	private static Playwright playwright;
	private static Browser browser;

	private final List<String> troubles = new CopyOnWriteArrayList<>();
	private final List<String> forgiven = new CopyOnWriteArrayList<>();
	private WorkbenchRig rig;
	private BrowserContext context;
	private Page page;

	@BeforeContainer
	public static void launch() {
		playwright = Playwright.create();
		browser = playwright.chromium().launch();
	}

	/* The browser is missing only when Playwright could not launch it, and Playwright only when it could not start;
	   that failure is the one worth reporting. */
	@AfterContainer
	public static void quit() {
		try {
			if (browser != null) {
				browser.close();
			}
		} finally {
			if (playwright != null) {
				playwright.close();
			}
		}
	}

	/* The run moves on in real time, as it does in the window, so the page's playhead and the server's clock are
	   seen to agree rather than assumed to. */
	@BeforeTry
	public void open() throws Exception {
		rig = new WorkbenchRig(InstantSource.system());
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 800));
		page = context.newPage();

		page.onConsoleMessage(message -> {
			if ("error".equals(message.type())) {
				troubles.add(message.text());
			}
		});

		page.onPageError(error -> troubles.add(error.toString()));
		page.onResponse(response -> {
			if (response.status() >= FIRST_ERROR) {
				troubles.add(response.url() + " " + response.status());
			}
		});

		page.onRequestFailed(request -> {
			if (!ABORTED.equals(request.failure())) {
				troubles.add(request.url() + " " + request.failure());
			}
		});

		page.navigate(rig.entrance().toString());
	}

	/* A script that threw or an asset that never arrived would otherwise go unnoticed by any test not looking at
	   that exact spot. */
	@AfterTry
	public void close() throws Exception {
		try {
			if (context != null) {
				context.close();
			}
		} finally {
			if (rig != null) {
				rig.close();
			}
		}

		assertEquals(List.of(), troubles.stream()
			.filter(trouble -> forgiven.stream().noneMatch(trouble::contains)).toList());
	}

	/** For a test that makes the studio fail on purpose: a trouble holding this text is the one it asked for. */
	protected void forgive(final String trouble) {
		forgiven.add(trouble);
	}

	/**
	 * Starts the studio again over the same home folder, as the next launch does. The new server listens on another
	 * port, which to the browser is another site, so nothing the page kept for itself survives: only what the
	 * studio wrote to the home folder does. The page leaves first, so none of its requests is cut off by the stop.
	 */
	protected void restart() throws Exception {
		page.navigate("about:blank");
		rig = rig.restart(InstantSource.system());
		page.navigate(rig.entrance().toString());
	}

	/** Waits for something the page set going on the server, letting the page run meanwhile. */
	protected void await(final String what, final BooleanSupplier done) {
		for (int tries = 0; !done.getAsBoolean(); tries++) {
			assertTrue(tries < AWAIT_TRIES, "Timed out waiting for " + what);
			page.waitForTimeout(AWAIT_POLL_MILLIS);
		}
	}

	/** Waits until the preferences say what the page asked for; they are on disk by then, where a restart reads them. */
	protected void awaitSaved(final Predicate<Layout> saved) {
		await("the preferences to be saved", () -> saved.test(rig.preferences().layout()));
	}

	protected Page page() {
		return page;
	}

	protected WorkbenchRig rig() {
		return rig;
	}
}
