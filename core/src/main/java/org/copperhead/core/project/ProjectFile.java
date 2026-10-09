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

import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
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
		extras = unknown(extras);
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

		final int format = object.getInt(FORMAT_KEY, FORMAT);

		if (format > FORMAT) {
			throw new ProjectFormatException("project format " + format
				+ " is newer than this version of Copperhead");
		}

		try {
			return read(object, format);
		} catch (final ClassCastException | IllegalArgumentException e) {
			throw new ProjectFormatException("not a project file: " + e.getMessage(), e);
		}
	}

	public String toJson() {
		final JsonArrayBuilder marks = JSON.createArrayBuilder();
		final JsonObjectBuilder json = JSON.createObjectBuilder().add(FORMAT_KEY, format).add(NAME, name)
			.add(KIND, kind.name()).add(TARGET, target.chipset()).add(LOAD, LoadAddress.format(loadAddress))
			.add(ENTRY, entry).add(BUILD, JSON.createObjectBuilder().add(CRUNCH, crunch).add(SYMBOLS, symbols));
		final StringWriter text = new StringWriter();

		breakpoints.forEach(mark -> marks.add(JSON.createObjectBuilder().add(FILE, mark.file())
			.add(LINE, mark.line())));
		json.add(BREAKPOINTS, marks);
		extras.forEach(json::add);

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

	private static ProjectFile read(final JsonObject json, final int format) {
		final ProjectFile fresh = create(text(json, NAME), kind(text(json, KIND)),
			Target.chipset(json.getString(TARGET, Target.A500_OCS.chipset())));
		final JsonObject build = json.containsKey(BUILD) ? json.getJsonObject(BUILD) : JsonValue.EMPTY_JSON_OBJECT;

		return new ProjectFile(format, fresh.name(), fresh.kind(), fresh.target(),
			LoadAddress.parse(json.getString(LOAD, LoadAddress.format(fresh.loadAddress()))),
			json.getString(ENTRY, fresh.entry()), build.getBoolean(CRUNCH, fresh.crunch()),
			build.getBoolean(SYMBOLS, fresh.symbols()), breakpoints(json), json);
	}

	private static String text(final JsonObject json, final String key) {
		if (json.get(key) instanceof JsonString text) {
			return text.getString();
		}

		throw new IllegalArgumentException("no " + key + " given");
	}

	private static ProjectKind kind(final String kind) {
		return Arrays.stream(ProjectKind.values()).filter(known -> known.name().equals(kind)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("no project is of the kind " + kind));
	}

	private static List<Breakpoint> breakpoints(final JsonObject json) {
		if (!json.containsKey(BREAKPOINTS)) {
			return List.of();
		}

		return json.getJsonArray(BREAKPOINTS).getValuesAs(JsonObject.class).stream()
			.map(mark -> new Breakpoint(text(mark, FILE), line(mark), 0)).toList();
	}

	private static int line(final JsonObject mark) {
		final int line = mark.getInt(LINE, 0);

		if (line < 1) {
			throw new IllegalArgumentException("a breakpoint needs a line from 1 on");
		}

		return line;
	}

	private static JsonObject unknown(final JsonObject keys) {
		final JsonObjectBuilder unknown = JSON.createObjectBuilder(keys);

		KNOWN.forEach(unknown::remove);
		return unknown.build();
	}
}
