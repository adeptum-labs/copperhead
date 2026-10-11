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

package org.copperhead.core.project;

import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.copperhead.core.build.BuildOptions;
import org.copperhead.core.build.LoadAddress;
import org.copperhead.core.debug.Breakpoint;

/**
 * What a project directory says about itself in its .chp file. Keys this version does not read ride along
 * untouched, so saving here keeps whatever another version put into the file.
 */
public record ProjectFile(int format, String name, ProjectKind kind, Target target, int loadAddress, String entry,
	boolean crunch, boolean symbols, List<Breakpoint> breakpoints, JsonObject extras) {

	public static final int FORMAT = 1;
	private static final int LOAD_ADDRESS = 0x2_0000;
	private static final String SOURCES = ProjectLayout.SRC + "/";
	private static final String MAIN = SOURCES + "main.s";
	private static final String FORMAT_KEY = "format";
	private static final String NAME = "name";
	private static final String KIND = "kind";
	private static final String TARGET = "target";
	private static final String LOAD = "loadAddress";
	private static final String ENTRY = "entry";
	private static final String BUILD = "build";
	private static final String CRUNCH = "crunch";
	private static final String SYMBOLS = "symbols";
	private static final String BREAKPOINTS = "breakpoints";
	private static final String FILE = "file";
	private static final String LINE = "line";
	private static final List<String> KNOWN = List.of(FORMAT_KEY, NAME, KIND, TARGET, LOAD, ENTRY, BUILD, BREAKPOINTS);
	private static final List<String> BUILD_KEYS = List.of(CRUNCH, SYMBOLS);
	private static final List<String> BREAKPOINT_KEYS = List.of(FILE, LINE);
	private static final JsonProvider JSON = JsonProvider.provider();
	private static final JsonWriterFactory PRETTY = JSON.createWriterFactory(
		Map.of(JsonGenerator.PRETTY_PRINTING, true));

	public ProjectFile {
		if (!ProjectLayout.validName(name)) {
			throw new IllegalArgumentException("not a project name: " + name);
		}

		if (!entry.startsWith(SOURCES) || !ProjectLayout.validSource(entry.substring(SOURCES.length()))) {
			throw new IllegalArgumentException("the entry must be a source in " + SOURCES + ", not " + entry);
		}

		breakpoints = List.copyOf(breakpoints);
		extras = unknown(extras, breakpoints);
	}

	public static ProjectFile create(final String name, final ProjectKind kind, final Target target) {
		return new ProjectFile(FORMAT, name, kind, target, LOAD_ADDRESS, MAIN, true, false, List.of(),
			JsonValue.EMPTY_JSON_OBJECT);
	}

	public static ProjectFile parse(final String json) {
		final JsonObject object;

		try (JsonReader reader = JSON.createReader(new StringReader(json))) {
			object = reader.readObject();
		} catch (final JsonException e) {
			throw new ProjectFormatException("not a project file: " + e.getMessage(), e);
		}

		try {
			final int format = integer(object, FORMAT_KEY, FORMAT, 1);

			if (format > FORMAT) {
				throw new ProjectFormatException("project format " + format
					+ " is newer than this version of Copperhead");
			}

			return read(object, format);
		} catch (final ProjectFormatException e) {
			throw e;
		} catch (final IllegalArgumentException e) {
			throw new ProjectFormatException("not a project file: " + e.getMessage(), e);
		}
	}

	public String toJson() {
		final JsonArrayBuilder marks = JSON.createArrayBuilder();
		final JsonObjectBuilder build = JSON.createObjectBuilder().add(CRUNCH, crunch).add(SYMBOLS, symbols);
		final JsonObjectBuilder json = JSON.createObjectBuilder().add(FORMAT_KEY, format).add(NAME, name)
			.add(KIND, kind.name()).add(TARGET, target.chipset()).add(LOAD, LoadAddress.format(loadAddress))
			.add(ENTRY, entry);
		final StringWriter text = new StringWriter();

		value(extras, BUILD, JsonObject.class).ifPresent(unknown -> unknown.forEach(build::add));
		breakpoints.forEach(mark -> {
			final JsonObjectBuilder written = JSON.createObjectBuilder().add(FILE, mark.file())
				.add(LINE, mark.line());

			ownKeys(value(extras, BREAKPOINTS, JsonArray.class), mark)
				.ifPresent(unknown -> without(unknown, BREAKPOINT_KEYS).forEach(written::add));
			marks.add(written);
		});
		json.add(BUILD, build).add(BREAKPOINTS, marks);
		extras.entrySet().stream().filter(entry -> !KNOWN.contains(entry.getKey()))
			.forEach(entry -> json.add(entry.getKey(), entry.getValue()));

		try (JsonWriter writer = PRETTY.createWriter(text)) {
			writer.writeObject(json.build());
		}

		return text.toString().strip() + "\n";
	}

	public ProjectFile withTarget(final Target target) {
		return new ProjectFile(format, name, kind, target, loadAddress, entry, crunch, symbols, breakpoints, extras);
	}

	public ProjectFile withLoadAddress(final int loadAddress) {
		return new ProjectFile(format, name, kind, target, loadAddress, entry, crunch, symbols, breakpoints, extras);
	}

	public ProjectFile withEntry(final String entry) {
		return new ProjectFile(format, name, kind, target, loadAddress, entry, crunch, symbols, breakpoints, extras);
	}

	public ProjectFile withCrunch(final boolean crunch) {
		return new ProjectFile(format, name, kind, target, loadAddress, entry, crunch, symbols, breakpoints, extras);
	}

	public ProjectFile withSymbols(final boolean symbols) {
		return new ProjectFile(format, name, kind, target, loadAddress, entry, crunch, symbols, breakpoints, extras);
	}

	public ProjectFile withBreakpoints(final List<Breakpoint> breakpoints) {
		return new ProjectFile(format, name, kind, target, loadAddress, entry, crunch, symbols, breakpoints, extras);
	}

	public BuildOptions options() {
		return new BuildOptions(crunch, symbols);
	}

	private static ProjectFile read(final JsonObject json, final int format) {
		final ProjectFile fresh = create(text(json, NAME), kind(text(json, KIND)),
			Target.chipset(text(json, TARGET, Target.A500_OCS.chipset())));
		final JsonObject build = value(json, BUILD, JsonObject.class).orElse(JsonValue.EMPTY_JSON_OBJECT);

		return new ProjectFile(format, fresh.name(), fresh.kind(), fresh.target(),
			LoadAddress.parse(text(json, LOAD, LoadAddress.format(fresh.loadAddress()))),
			text(json, ENTRY, fresh.entry()), flag(build, CRUNCH, fresh.crunch()),
			flag(build, SYMBOLS, fresh.symbols()), breakpoints(json), json);
	}

	/* An absent key takes its default; a key of the wrong type is refused, or a save would overwrite what it held. */
	private static <T extends JsonValue> Optional<T> value(final JsonObject json, final String key,
		final Class<T> type) {
		final JsonValue value = json.get(key);

		if (value != null && !type.isInstance(value)) {
			throw new IllegalArgumentException(key + " is of the wrong type");
		}

		return Optional.ofNullable(type.cast(value));
	}

	private static String text(final JsonObject json, final String key) {
		return value(json, key, JsonString.class).map(JsonString::getString)
			.orElseThrow(() -> new IllegalArgumentException("no " + key + " given"));
	}

	private static String text(final JsonObject json, final String key, final String fallback) {
		return value(json, key, JsonString.class).map(JsonString::getString).orElse(fallback);
	}

	private static boolean flag(final JsonObject json, final String key, final boolean fallback) {
		return switch (json.getOrDefault(key, fallback ? JsonValue.TRUE : JsonValue.FALSE).getValueType()) {
			case TRUE -> true;
			case FALSE -> false;
			default -> throw new IllegalArgumentException(key + " is of the wrong type");
		};
	}

	private static int integer(final JsonObject json, final String key, final int fallback, final int minimum) {
		final int number = value(json, key, JsonNumber.class).map(found -> whole(found, key)).orElse(fallback);

		if (number < minimum) {
			throw new IllegalArgumentException(key + " must be " + minimum + " or more");
		}

		return number;
	}

	private static int whole(final JsonNumber number, final String key) {
		try {
			return number.intValueExact();
		} catch (final ArithmeticException e) {
			throw new IllegalArgumentException(key + " must be a whole number that fits an int", e);
		}
	}

	private static ProjectKind kind(final String kind) {
		return Arrays.stream(ProjectKind.values()).filter(known -> known.name().equals(kind)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("no project is of the kind " + kind));
	}

	private static List<Breakpoint> breakpoints(final JsonObject json) {
		return value(json, BREAKPOINTS, JsonArray.class).orElse(JsonValue.EMPTY_JSON_ARRAY).stream()
			.map(ProjectFile::breakpoint).toList();
	}

	private static Breakpoint breakpoint(final JsonValue mark) {
		if (!(mark instanceof JsonObject json)) {
			throw new IllegalArgumentException("a breakpoint must be an object");
		}

		final String file = text(json, FILE);

		if (!ProjectLayout.validSource(file)) {
			throw new IllegalArgumentException("a breakpoint needs a source in " + SOURCES + ", not " + file);
		}

		return new Breakpoint(file, integer(json, LINE, 0, 1), 0);
	}

	/*
	 * What this version does not read, down to the keys inside the build options and inside each breakpoint, which
	 * are held under their own keys for a save to put back. A breakpoint's keys go with its place in the source, so
	 * they are kept while a breakpoint stays there. A key this version reads is never kept: it would hide the value.
	 */
	private static JsonObject unknown(final JsonObject keys, final List<Breakpoint> marks) {
		final JsonObjectBuilder unknown = JSON.createObjectBuilder(keys);
		final JsonArrayBuilder ownKeys = JSON.createArrayBuilder();
		final Optional<JsonArray> written = keys.get(BREAKPOINTS) instanceof JsonArray array ? Optional.of(array)
			: Optional.empty();

		KNOWN.forEach(unknown::remove);

		if (keys.get(BUILD) instanceof JsonObject build && !without(build, BUILD_KEYS).isEmpty()) {
			unknown.add(BUILD, without(build, BUILD_KEYS));
		}

		marks.stream().distinct().map(mark -> ownKeys(written, mark)).flatMap(Optional::stream)
			.filter(mark -> !without(mark, BREAKPOINT_KEYS).isEmpty()).forEach(ownKeys::add);

		final JsonArray kept = ownKeys.build();

		if (!kept.isEmpty()) {
			unknown.add(BREAKPOINTS, kept);
		}

		return unknown.build();
	}

	/* The object written for a breakpoint at the same place, the first one if a file holds several. */
	private static Optional<JsonObject> ownKeys(final Optional<JsonArray> written, final Breakpoint mark) {
		return written.stream().flatMap(JsonArray::stream).filter(JsonObject.class::isInstance)
			.map(JsonObject.class::cast).filter(json -> json.get(FILE) instanceof JsonString file
				&& file.getString().equals(mark.file()) && json.get(LINE) instanceof JsonNumber line
				&& line.isIntegral() && line.longValue() == mark.line())
			.findFirst();
	}

	private static JsonObject without(final JsonObject json, final List<String> keys) {
		final JsonObjectBuilder left = JSON.createObjectBuilder(json);

		keys.forEach(left::remove);
		return left.build();
	}
}
