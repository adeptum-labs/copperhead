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

package org.copperhead.desktop;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.awt.AWTException;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.copperhead.shell.BrowserWindow;

/**
 * The installed Copperhead with its window open to a debugging connection, so a test can work it the way a
 * person would.
 */
final class RunningCopperhead implements AutoCloseable {
	private static final int PORT = 9333;
	private static final String ADDRESS = "http://127.0.0.1:" + PORT;
	private static final Duration START = Duration.ofMinutes(3);
	private static final Duration STOP = Duration.ofMinutes(1);
	private static final Duration POLL = Duration.ofSeconds(1);
	private static final int OK = 200;

	/** What Playwright said to the browser of the latest start, written next to the logs. */
	static final String PROTOCOL = "playwright.protocol";

	private final Process process;
	private final Playwright playwright;
	private final Page page;
	private final Path threads;
	private final List<ProcessHandle> family = new ArrayList<>();

	private RunningCopperhead(final Process process, final Playwright playwright, final Page page,
		final Path threads) {

		this.process = process;
		this.playwright = playwright;
		this.page = page;
		this.threads = threads;
	}

	/** Starts the launcher. */
	static RunningCopperhead start(final Path launcher, final Path log) throws IOException, InterruptedException {
		final ProcessBuilder builder = new ProcessBuilder(launcher.toString()).redirectErrorStream(true)
			.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));

		builder.environment().put("JAVA_TOOL_OPTIONS", "-D" + BrowserWindow.DEBUGGING_PORT + "=" + PORT);

		final Process process = builder.start();
		final Path threads = log.resolveSibling(log.getFileName() + ".threads");

		try {
			awaitDebugging(process);

			/* Playwright only attaches to the browser Copperhead runs, so it needs none of its own. What it says
			   to that browser is kept beside the log, the only way to see where an attach that never ends stops.
			   Its driver keeps the environment of the first start, so each start writes the same file afresh. */
			final Playwright playwright = Playwright.create(new Playwright.CreateOptions()
				.setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1", "DEBUG", "pw:protocol", "DEBUG_FILE",
					log.resolveSibling(PROTOCOL).toString())));

			final Browser browser = playwright.chromium().connectOverCDP(ADDRESS);

			final Path screen = log.resolveSibling(log.getFileName() + ".png");

			return new RunningCopperhead(process, playwright, awaitPage(browser, screen), threads);
		} catch (IOException | InterruptedException | RuntimeException e) {
			if (process.isAlive()) {
				dumpThreads(process, threads);
			}

			end(process, process.descendants().toList());
			throw e;
		}
	}

	Page page() {
		return page;
	}

	/* Closed from the page, the way a person closes it, so quitting is tested too. The click is dispatched by the
	   page because the window, and the connection with it, goes away while Playwright would still be waiting. */
	boolean quit() throws IOException, InterruptedException {
		family.addAll(process.descendants().toList());
		page.evaluate("document.querySelector('[data-window=close]').click()");

		if (process.waitFor(STOP)) {
			return true;
		}

		dumpThreads(process, threads);
		return false;
	}

	/* A Copperhead that is stuck, opening or quitting, says where through its threads. The launcher runs Java
	   inside its own process. */
	private static void dumpThreads(final Process process, final Path file)
		throws IOException, InterruptedException {

		final Path jcmd = Path.of(System.getProperty("java.home"), "bin", "jcmd");

		new ProcessBuilder(jcmd.toString(), Long.toString(process.pid()), "Thread.print")
			.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(file.toFile()))
			.start().waitFor(STOP);
	}

	void screenshot(final Path file) {
		page.screenshot(new Page.ScreenshotOptions().setPath(file));
	}

	@Override
	public void close() {
		try {
			family.addAll(process.descendants().toList());
			end(process, family);
		} finally {
			playwright.close();
		}
	}

	/* Each process is waited for, so the next start does not race a dying one for its ports. */
	private static void end(final Process process, final List<ProcessHandle> family) {
		final List<ProcessHandle> all = Stream.concat(Stream.of(process.toHandle()), family.stream()).toList();

		all.forEach(ProcessHandle::destroyForcibly);
		all.forEach(RunningCopperhead::awaitEnd);
	}

	private static void awaitEnd(final ProcessHandle handle) {
		try {
			handle.onExit().get(STOP.toSeconds(), TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException | TimeoutException e) {
			throw new IllegalStateException("Process " + handle.pid() + " did not end", e);
		}
	}

	private static void awaitDebugging(final Process process) throws IOException, InterruptedException {
		final HttpClient http = HttpClient.newHttpClient();
		final HttpRequest request = HttpRequest.newBuilder(URI.create(ADDRESS + "/json/version")).build();
		final Instant deadline = Instant.now().plus(START);

		while (Instant.now().isBefore(deadline)) {
			if (!process.isAlive()) {
				throw new IOException("Copperhead stopped with exit " + process.exitValue()
					+ " before its window opened");
			}

			try {
				if (http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == OK) {
					return;
				}
			} catch (IOException _) {
				/* Nothing listens until the browser engine is up. */
			}

			Thread.sleep(POLL.toMillis());
		}

		throw new IOException("No debugging connection on " + ADDRESS + " within " + START);
	}

	/* Without a page there is nothing for the browser to take a picture of, so the screen is taken instead. */
	private static Page awaitPage(final Browser browser, final Path screen) throws IOException, InterruptedException {
		final Instant deadline = Instant.now().plus(START);

		while (Instant.now().isBefore(deadline)) {
			final Optional<Page> page = browser.contexts().stream().flatMap(context -> context.pages().stream())
				.filter(candidate -> candidate.url().startsWith("http://127.0.0.1")).findFirst();

			if (page.isPresent()) {
				return page.get();
			}

			Thread.sleep(POLL.toMillis());
		}

		final String pages = browser.contexts().stream().flatMap(context -> context.pages().stream())
			.map(Page::url).toList().toString();
		final String targets = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(ADDRESS
			+ "/json/list")).build(), HttpResponse.BodyHandlers.ofString()).body();

		String picture = "the screen is in " + screen;

		try {
			final Rectangle bounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

			ImageIO.write(new Robot().createScreenCapture(bounds), "png", screen.toFile());
		} catch (AWTException | HeadlessException e) {
			picture = "no picture of the screen: " + e;
		}

		throw new IOException("The window showed no page of Copperhead within " + START + "; Playwright saw " + pages
			+ " and the browser lists " + targets + "; " + picture);
	}
}
