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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Downloads a source archive into a folder and hashes what arrived; whether to trust it is the caller's call. */
public final class Fetcher {
	private static final int OK = 200;

	private final HttpClient client;
	private final Duration timeout;

	public Fetcher(final HttpClient client, final Duration timeout) {
		this.client = client;
		this.timeout = timeout;
	}

	public Fetched fetch(final URI uri, final Path directory) throws IOException, InterruptedException {
		final Path file = Files.createTempFile(directory, "fetched", ".tar.gz");
		final CompletableFuture<HttpResponse<Path>> response = client.sendAsync(HttpRequest.newBuilder(uri).build(),
			HttpResponse.BodyHandlers.ofFile(file));

		try {
			return new Fetched(arrived(uri, response), sha256(file));
		} catch (final IOException | InterruptedException e) {
			response.cancel(true);
			Files.deleteIfExists(file);
			throw e;
		}
	}

	/* The timeout covers the whole download, not only the wait for the first byte: a server that stalls halfway
	   would otherwise hold the build forever. */
	private Path arrived(final URI uri, final CompletableFuture<HttpResponse<Path>> response)
		throws IOException, InterruptedException {

		try {
			final HttpResponse<Path> answer = response.get(timeout.toMillis(), TimeUnit.MILLISECONDS);

			if (answer.statusCode() != OK) {
				throw new IOException(uri + " answered " + answer.statusCode());
			}
			return answer.body();
		} catch (final TimeoutException e) {
			throw new IOException(uri + " did not arrive within " + timeout.toSeconds() + " s", e);
		} catch (final ExecutionException e) {
			throw new IOException(uri + " could not be fetched: " + e.getCause().getMessage(),
				e.getCause());
		}
	}

	private static String sha256(final Path file) throws IOException {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");

			return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException("Every Java runtime has SHA-256", e);
		}
	}

	public record Fetched(Path file, String sha256) {
	}
}
