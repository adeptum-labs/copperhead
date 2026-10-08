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
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.copperhead.ui.WorkbenchRig;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The studio opened in headless Chromium, the engine the window embeds. The window only ever reaches its host
 * through requests, so a browser pointed at the served page runs the same scripts the window does, without a
 * frame opening on anybody's desktop.
 */
public abstract class BrowserTest {
	/* Chromium reports the 204 answers of the window controls as aborted although the page received them, so
	   only the other failures say anything. */
	private static final String ABORTED = "net::ERR_ABORTED";
	private static final int FIRST_ERROR = 400;

	private static Playwright playwright;
	private static Browser browser;

	private final List<String> troubles = new CopyOnWriteArrayList<>();
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

		assertEquals(List.of(), troubles);
	}

	protected Page page() {
		return page;
	}
}
