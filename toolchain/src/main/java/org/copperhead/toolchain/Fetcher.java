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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Downloads a source archive into a folder and hashes what arrived; whether to trust it is the caller's call. */
public final class Fetcher {
	/* The archives fetched are far smaller. vasm comes over plain HTTP, so anyone on the way could otherwise send
	   bytes until the disk is full. */
	private static final int LIMIT_MB = 64;
	public static final long LIMIT = (long) LIMIT_MB << 20;

	private static final int OK = 200;
	private static final String CONTENT_LENGTH = "Content-Length";

	private final HttpClient client;
	private final Duration timeout;

	public Fetcher(final HttpClient client, final Duration timeout) {
		this.client = client;
		this.timeout = timeout;
	}

	public Fetched fetch(final URI uri, final Path directory) throws IOException, InterruptedException {
		final Path file = Files.createTempFile(directory, "fetched", ".tar.gz");
		final CompletableFuture<HttpResponse<Path>> response = client.sendAsync(HttpRequest.newBuilder(uri).build(),
			info -> new Limited(HttpResponse.BodySubscribers.ofFile(file),
				info.headers().firstValueAsLong(CONTENT_LENGTH).orElse(0)));

		try {
			return new Fetched(arrived(uri, response), sha256(file));
		} catch (final IOException | InterruptedException | RuntimeException | Error e) {
			response.cancel(true);
			removed(file, e);
			throw e;
		}
	}

	/* A failed cleanup is attached to the failure that made it necessary instead of replacing it. */
	private static void removed(final Path file, final Throwable failure) {
		try {
			Files.deleteIfExists(file);
		} catch (final IOException e) {
			failure.addSuppressed(e);
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

			try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
				in.transferTo(OutputStream.nullOutputStream());
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException("Every Java runtime has SHA-256", e);
		}
	}

	public record Fetched(Path file, String sha256) {
	}

	/*
	 * Writes the body to its file until it grows past the limit, and then stops the download. A server can claim a
	 * size it does not keep to, or claim none, so the bytes are counted as they come; one that claims too many is
	 * stopped before the first byte.
	 */
	private static final class Limited implements HttpResponse.BodySubscriber<Path> {
		private final HttpResponse.BodySubscriber<Path> file;
		private final long declared;
		private Flow.Subscription subscription;
		private long received;
		private boolean stopped;

		Limited(final HttpResponse.BodySubscriber<Path> file, final long declared) {
			this.file = file;
			this.declared = declared;
		}

		@Override
		public CompletionStage<Path> getBody() {
			return file.getBody();
		}

		@Override
		public void onSubscribe(final Flow.Subscription given) {
			subscription = given;
			file.onSubscribe(given);

			if (declared > LIMIT) {
				stop();
			}
		}

		@Override
		public void onNext(final List<ByteBuffer> items) {
			if (stopped) {
				return;
			}
			received += items.stream().mapToLong(ByteBuffer::remaining).sum();

			if (received > LIMIT) {
				stop();
				return;
			}
			file.onNext(items);
		}

		@Override
		public void onError(final Throwable failure) {
			if (!stopped) {
				file.onError(failure);
			}
		}

		@Override
		public void onComplete() {
			if (!stopped) {
				file.onComplete();
			}
		}

		private void stop() {
			stopped = true;
			subscription.cancel();
			file.onError(new IOException("the download is larger than the " + LIMIT_MB
				+ " MB Copperhead accepts"));
		}
	}
}
