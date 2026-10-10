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
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.copperhead.toolchain.PinnedSource;
import org.copperhead.toolchain.XdgDirs;

/**
 * What the person chose that outlives a project: the layout of the window, the tools to build with, the vasm source
 * they accepted and the projects they opened last. Every change is written at once, whole or not at all, so a crash
 * never leaves half a file; a file that cannot be read is set aside rather than lost, and the studio starts afresh.
 */
@ApplicationScoped
public class Preferences {
	private static final String FILE = "preferences.json";
	private static final String SET_ASIDE = FILE + ".bak";
	private static final String PART = ".part";
	private static final String UNREADABLE = "preferences were unreadable; moved to " + SET_ASIDE;
	private static final String STUCK = "preferences were unreadable and could not be moved aside";
	private static final String CHP = ".chp";
	private static final int KEPT = 8;
	private static final String THEME = "theme";
	private static final String SPLIT = "split";
	private static final String BOTTOM = "bottom";
	private static final String TOOLS = "tools";
	private static final String TRUSTED = "trustedVasm";
	private static final String RECENT = "recent";
	private static final List<String> NAMED = List.of(PinnedSource.VASM.tool(), PinnedSource.SHRINKLER.tool());

	private final Path file;
	private final Map<String, Path> tools = new TreeMap<>();
	private Layout layout = Layout.defaults();
	private Optional<String> trustedVasm = Optional.empty();
	private List<Path> recent = List.of();
	private Optional<String> note = Optional.empty();

	public Preferences() {
		this(XdgDirs.system().config().resolve(FILE));
	}

	public Preferences(final Path file) {
		this.file = file;

		if (Files.exists(file)) {
			read();
		}
	}

	public synchronized Layout layout() {
		return layout;
	}

	public synchronized void layout(final Layout changed) {
		layout = changed;
		write();
	}

	public synchronized Optional<Path> tool(final String tool) {
		return Optional.ofNullable(tools.get(named(tool)));
	}

	public synchronized void tool(final String tool, final Optional<Path> path) {
		path.ifPresentOrElse(chosen -> tools.put(named(tool), chosen), () -> tools.remove(named(tool)));
		write();
	}

	public synchronized Optional<String> trustedVasm() {
		return trustedVasm;
	}

	public synchronized void trustedVasm(final String sha256) {
		trustedVasm = Optional.of(sha256);
		write();
	}

	/** The projects opened last, newest first; one moved or deleted since is left out. */
	public synchronized List<Path> recent() {
		return recent.stream().filter(Files::isRegularFile).toList();
	}

	public synchronized void used(final Path chp) {
		final Path opened = chp.toAbsolutePath().normalize();

		recent = Stream.concat(Stream.of(opened), recent().stream().filter(path -> !path.equals(opened)))
			.limit(KEPT).toList();
		write();
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

	private void read() {
		try {
			final JsonObject saved = Json.createReader(new StringReader(Files.readString(file))).readObject();
			final JsonObject paths = saved.getJsonObject(TOOLS);
			final Layout defaults = Layout.defaults();

			layout = new Layout(saved.getString(THEME, defaults.theme()), saved.getInt(SPLIT, defaults.split()),
				saved.getInt(BOTTOM, defaults.bottom()));

			if (paths != null) {
				paths.forEach((tool, path) -> tools.put(tool, Path.of(((JsonString) path).getString())));
				tools.keySet().retainAll(NAMED);
			}

			trustedVasm = Optional.ofNullable(saved.getString(TRUSTED, null));
			recent = Optional.ofNullable(saved.getJsonArray(RECENT)).stream()
				.flatMap(list -> list.getValuesAs(JsonString.class).stream())
				.map(path -> Path.of(path.getString())).filter(path -> path.toString().endsWith(CHP))
				.toList();
		} catch (IOException | JsonException | ClassCastException | IllegalArgumentException e) {
			setAside();
		}
	}

	private void setAside() {
		layout = Layout.defaults();
		tools.clear();
		trustedVasm = Optional.empty();
		recent = List.of();

		try {
			Files.move(file, file.resolveSibling(SET_ASIDE), StandardCopyOption.REPLACE_EXISTING);
			note = Optional.of(UNREADABLE);
		} catch (IOException e) {
			note = Optional.of(STUCK);
		}
	}

	private void write() {
		final JsonObjectBuilder named = Json.createObjectBuilder();
		final JsonArrayBuilder opened = Json.createArrayBuilder();

		tools.forEach((tool, path) -> named.add(tool, path.toString()));
		recent.forEach(path -> opened.add(path.toString()));

		final JsonObjectBuilder saved = Json.createObjectBuilder().add(THEME, layout.theme())
			.add(SPLIT, layout.split()).add(BOTTOM, layout.bottom()).add(TOOLS, named).add(RECENT, opened);

		trustedVasm.ifPresent(sha256 -> saved.add(TRUSTED, sha256));

		try {
			replace(pretty(saved.build()));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/* Written beside the file and moved over it, so a reader finds the old preferences or the new, never a mix. */
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
}
