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

package org.copperhead.ui.session;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.With;
import org.copperhead.toolchain.PinnedSource;
import org.copperhead.toolchain.XdgDirs;

/**
 * What the person chose that outlives a project: the layout of the window, the tools to build with, the vasm source
 * they accepted and the projects they opened last. Every change is written at once, whole or not at all, so a crash
 * never leaves half a file; a file that cannot be read is set aside rather than lost, and the studio starts afresh.
 * The file is the person's to edit and anything else may write to it, so nothing in it is believed before it is checked.
 */
@ApplicationScoped
public class Preferences {
	private static final String FILE = "preferences.json";
	private static final String SET_ASIDE = FILE + ".bak";
	private static final String PART = ".part";
	private static final String UNREADABLE = "preferences were unreadable; moved to ";
	private static final String STUCK = "preferences were unreadable and could not be moved aside";
	private static final String CHP = ".chp";
	private static final int KEPT = 8;
	private static final int SPARED = 5;
	private static final int LARGEST = 1024 * 1024;
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
	private static final String THEME = "theme";
	private static final String SPLIT = "split";
	private static final String BOTTOM = "bottom";
	private static final String TOOLS = "tools";
	private static final String TRUSTED = "trustedVasm";
	private static final String RECENT = "recent";
	private static final List<String> NAMED = List.of(PinnedSource.VASM.tool(), PinnedSource.SHRINKLER.tool());

	private final Path file;
	private State state = State.blank();
	private Optional<String> note = Optional.empty();

	public Preferences() {
		this(XdgDirs.system().config().resolve(FILE));
	}

	public Preferences(final Path file) {
		this.file = file;

		if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
			read();
		}
	}

	public synchronized Layout layout() {
		return state.layout();
	}

	public synchronized void layout(final Layout changed) {
		save(state.withLayout(changed));
	}

	public synchronized Optional<Path> tool(final String tool) {
		return Optional.ofNullable(state.tools().get(named(tool)));
	}

	public synchronized void tool(final String tool, final Optional<Path> path) {
		final Map<String, Path> changed = new HashMap<>(state.tools());

		path.map(Preferences::absolute).ifPresentOrElse(chosen -> changed.put(named(tool), chosen),
			() -> changed.remove(named(tool)));
		save(state.withTools(changed));
	}

	public synchronized Optional<String> trustedVasm() {
		return state.trustedVasm();
	}

	public synchronized void trustedVasm(final String sha256) {
		save(state.withTrustedVasm(Optional.of(checksum(sha256.toLowerCase(Locale.ROOT)))));
	}

	/** The projects opened last, newest first; one moved or deleted since is left out. */
	public synchronized List<Path> recent() {
		return state.recent().stream().filter(Files::isRegularFile).toList();
	}

	public synchronized void used(final Path chp) {
		final Path opened = chp.toAbsolutePath().normalize();

		final Stream<Path> others = recent().stream().filter(path -> !path.equals(opened));

		save(state.withRecent(Stream.concat(Stream.of(opened), others).limit(KEPT).toList()));
	}

	/** Says once that the file could not be read, so the person learns why their choices are gone. */
	public synchronized Optional<String> note() {
		final Optional<String> once = note;

		note = Optional.empty();
		return once;
	}

	private static String named(final String tool) {
		if (!NAMED.contains(tool)) {
			throw new IllegalArgumentException("No tool is called " + tool);
		}

		return tool;
	}

	/* A relative path would mean a different file whenever the studio is started from another folder. */
	private static Path absolute(final Path path) {
		if (!path.isAbsolute()) {
			throw new IllegalArgumentException("Not an absolute path: " + path);
		}

		return path.normalize();
	}

	/* The checksum is later written into a command and a path, so only what a SHA-256 can be passes. */
	private static String checksum(final String sha256) {
		if (!SHA256.matcher(sha256).matches()) {
			throw new IllegalArgumentException("Not a SHA-256 checksum: " + sha256);
		}

		return sha256;
	}

	private void save(final State next) {
		try {
			replace(pretty(next.toJson()));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		state = next;
	}

	private void read() {
		try {
			state = State.of(Json.createReader(new ByteArrayInputStream(bounded())).readObject());
		} catch (IOException | JsonException | ClassCastException | IllegalArgumentException
			| ArithmeticException e) {
			setAside();
		}
	}

	/* Anything but a plain file of a sane size is refused unopened: opening a pipe would wait for a writer forever. */
	private byte[] bounded() throws IOException {
		if (!Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile()) {
			throw new IOException("Not a plain file: " + file);
		}

		try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
			final byte[] bytes = in.readNBytes(LARGEST + 1);

			if (bytes.length > LARGEST) {
				throw new IOException("Larger than " + LARGEST + " bytes: " + file);
			}

			return bytes;
		}
	}

	private void setAside() {
		state = State.blank();

		try {
			final Path aside = free();

			Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
			note = Optional.of(UNREADABLE + aside.getFileName());
		} catch (IOException e) {
			note = Optional.of(STUCK);
		}
	}

	/* The first file set aside may be the only trace of what was lost, so a second one goes beside it, not over it.
	   Only the last of the few spared names is ever overwritten, which keeps the folder from growing without end. */
	private Path free() {
		Path aside = file.resolveSibling(SET_ASIDE);

		for (int number = 1; number <= SPARED && Files.exists(aside, LinkOption.NOFOLLOW_LINKS); number++) {
			aside = file.resolveSibling(SET_ASIDE + "." + number);
		}

		return aside;
	}

	/* Written beside the file and moved over it, so a reader finds the old preferences or the new, never a mix.
	   The staging file gets a name nobody can guess and is created new, so a link planted there is not followed. */
	private void replace(final String text) throws IOException {
		Files.createDirectories(file.getParent());

		final Path part = Files.createTempFile(file.getParent(), FILE, PART);

		try {
			Files.writeString(part, text);
			Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(part);
		}
	}

	private static String pretty(final JsonValue value) {
		final StringWriter text = new StringWriter();

		Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true)).createWriter(text).write(value);
		return text + System.lineSeparator();
	}

	/* Whole or nothing: a change is built as a new state and only becomes the current one once it is on disk. */
	@With
	private record State(Layout layout, Map<String, Path> tools, Optional<String> trustedVasm, List<Path> recent) {
		State {
			tools = Map.copyOf(tools);
			recent = List.copyOf(recent);
		}

		static State blank() {
			return new State(Layout.defaults(), Map.of(), Optional.empty(), List.of());
		}

		/* A missing key takes its default; one of the wrong type is a damaged file, not a reason to guess. */
		static State of(final JsonObject saved) {
			final Layout defaults = Layout.defaults();
			final Layout layout = new Layout(
				saved.containsKey(THEME) ? saved.getJsonString(THEME).getString() : defaults.theme(),
				number(saved, SPLIT, defaults.split()), number(saved, BOTTOM, defaults.bottom()));
			final Map<String, Path> tools = new HashMap<>();
			final Optional<String> trusted = Optional.ofNullable(saved.containsKey(TRUSTED)
				? checksum(saved.getJsonString(TRUSTED).getString()) : null);

			if (saved.containsKey(TOOLS)) {
				saved.getJsonObject(TOOLS).forEach((tool, path) ->
					tools.put(tool, absolute(Path.of(((JsonString) path).getString()))));
				tools.keySet().retainAll(NAMED);
			}

			return new State(layout, tools, trusted, opened(saved));
		}

		private static int number(final JsonObject saved, final String key, final int fallback) {
			return saved.containsKey(key) ? saved.getJsonNumber(key).intValueExact() : fallback;
		}

		/* Every entry is checked, however many there are, before the newest few are kept; the size of the file
		   bounds the work. */
		private static List<Path> opened(final JsonObject saved) {
			if (!saved.containsKey(RECENT)) {
				return List.of();
			}

			return saved.getJsonArray(RECENT).getValuesAs(JsonString.class).stream()
				.map(path -> absolute(Path.of(path.getString()))).distinct()
				.filter(path -> path.toString().endsWith(CHP)).limit(KEPT).toList();
		}

		JsonObject toJson() {
			final JsonObjectBuilder named = Json.createObjectBuilder();
			final JsonArrayBuilder opened = Json.createArrayBuilder();

			new TreeMap<>(tools).forEach((tool, path) -> named.add(tool, path.toString()));
			recent.forEach(path -> opened.add(path.toString()));

			final JsonObjectBuilder json = Json.createObjectBuilder().add(THEME, layout.theme())
				.add(SPLIT, layout.split()).add(BOTTOM, layout.bottom()).add(TOOLS, named)
				.add(RECENT, opened);

			trustedVasm.ifPresent(sha256 -> json.add(TRUSTED, sha256));
			return json.build();
		}
	}
}
