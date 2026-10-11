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

package org.copperhead.toolchain;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

/** An HTTP server on the loopback interface that serves fixed bytes by path, standing in for the tools' hosts. */
final class LocalServer implements AutoCloseable {
	private static final int OK = 200;
	private static final int NO_BODY = -1;
	private static final int CHUNK = 1 << 16;

	private final HttpServer server;
	private final HttpClient client;
	private final HttpClient proxied;

	LocalServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.start();
		client = HttpClient.newHttpClient();
		proxied = HttpClient.newBuilder().proxy(ProxySelector.of(server.getAddress())).build();
	}

	LocalServer serve(final String path, final byte[] body) {
		server.createContext(path, exchange -> {
			exchange.sendResponseHeaders(OK, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		return this;
	}

	/** Claims a body of the length given and sends none of it for a while. */
	LocalServer claim(final String path, final long length, final Duration silence) {
		server.createContext(path, exchange -> {
			try {
				exchange.sendResponseHeaders(OK, length);
				exchange.getResponseBody().flush();
				Thread.sleep(silence);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				exchange.close();
			}
		});
		return this;
	}

	/** Sends a body that names no length and never ends, until the client hangs up. */
	LocalServer endless(final String path) {
		server.createContext(path, exchange -> {
			final byte[] chunk = new byte[CHUNK];

			exchange.sendResponseHeaders(OK, 0);
			try (OutputStream out = exchange.getResponseBody()) {
				while (!Thread.currentThread().isInterrupted()) {
					out.write(chunk);
				}
			} catch (final IOException e) {
				exchange.close();
			}
		});
		return this;
	}

	LocalServer stall(final String path, final Duration delay) {
		server.createContext(path, exchange -> {
			try {
				Thread.sleep(delay);
				exchange.sendResponseHeaders(OK, NO_BODY);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				exchange.close();
			}
		});
		return this;
	}

	URI uri(final String path) {
		final InetSocketAddress address = server.getAddress();

		return URI.create("http://" + address.getAddress().getHostAddress() + ":" + address.getPort() + path);
	}

	HttpClient client() {
		return client;
	}

	/* Every plain http request goes through this server, which answers it by path, so a build that fetches from
	   the real vasm address still never leaves the machine. */
	HttpClient proxy() {
		return proxied;
	}

	@Override
	public void close() {
		client.shutdownNow();
		proxied.shutdownNow();
		server.stop(0);
	}
}
