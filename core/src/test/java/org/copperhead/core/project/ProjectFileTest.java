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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.copperhead.core.build.BuildOptions;
import org.copperhead.core.debug.Breakpoint;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProjectFileTest {
	private static final ProjectFile DEMO = ProjectFile.create("nightfall", ProjectKind.DEMO, Target.A500_OCS);

	private static JsonObject object(final Map<String, JsonValue> entries) {
		final JsonObjectBuilder object = Json.createObjectBuilder();

		entries.forEach(object::add);
		return object.build();
	}

	private static String refusal(final String json) {
		return assertThrows(ProjectFormatException.class, () -> ProjectFile.parse(json)).getMessage();
	}

	@Example
	public void shouldStartAProjectAtTheUsualAddressWithShrinklerOn() {
		assertEquals(new ProjectFile(1, "nightfall", ProjectKind.DEMO, Target.A500_OCS, 0x2_0000, "src/main.s", true,
			false, List.of(), JsonValue.EMPTY_JSON_OBJECT), DEMO);
	}

	@Example
	public void shouldWriteEveryKeyThisVersionReads() {
		assertEquals("""
			{
			    "format": 1,
			    "name": "nightfall",
			    "kind": "DEMO",
			    "target": "OCS",
			    "loadAddress": "$20000",
			    "entry": "src/main.s",
			    "build": {
			        "crunch": true,
			        "symbols": false
			    },
			    "breakpoints": [
			        {
			            "file": "main.s",
			            "line": 12
			        }
			    ]
			}
			""", DEMO.withBreakpoints(List.of(new Breakpoint("main.s", 12, 3))).toJson());
	}

	@Property
	public void shouldReadBackWhatItWrites(@ForAll("files") final ProjectFile file) {
		assertEquals(file, ProjectFile.parse(file.toJson()));
	}

	@Provide
	public Arbitrary<ProjectFile> files() {
		final Arbitrary<String> sources = Combinators.combine(Arbitraries.strings().alpha().numeric()
			.withChars('_', '-').ofMinLength(1).ofMaxLength(12), Arbitraries.of(".s", ".i")).as(String::concat);
		final Arbitrary<ProjectFile> created = Combinators.combine(Combinators.combine(
			Arbitraries.strings().alpha().numeric().ofLength(1),
			Arbitraries.strings().alpha().numeric().withChars('_', '-').ofMaxLength(63)).as(String::concat),
			Arbitraries.of(ProjectKind.class), Arbitraries.of(Target.class)).as(ProjectFile::create);
		final Arbitrary<List<Breakpoint>> marks = Combinators.combine(sources,
			Arbitraries.integers().between(1, 100_000)).as((file, line) -> new Breakpoint(file, line, 0)).list()
			.ofMaxSize(4);
		final Arbitrary<JsonObject> extras = Arbitraries.maps(Arbitraries.strings().alpha().ofMinLength(1)
			.ofMaxLength(10), Arbitraries.oneOf(Arbitraries.strings().alpha().withChars(' ', '"', '\\', '\n')
			.ofMaxLength(12).map(Json::createValue), Arbitraries.integers().map(Json::createValue),
			Arbitraries.of(JsonValue.TRUE, JsonValue.FALSE, JsonValue.NULL, JsonValue.EMPTY_JSON_ARRAY)))
			.ofMaxSize(4).map(ProjectFileTest::object);

		return Combinators.combine(created, Arbitraries.integers().between(0x100, 0x3F_FFFF), sources,
			Arbitraries.of(true, false), Arbitraries.of(true, false), marks, extras)
			.as((file, slot, entry, crunch, symbols, breakpoints, unknown) -> new ProjectFile(file.format(),
				file.name(), file.kind(), file.target(), slot * 4, "src/" + entry, crunch, symbols,
				breakpoints, unknown));
	}

	@Example
	public void shouldKeepTheKeysOfALaterVersionThroughASave() {
		final String later = """
			{"format": 1, "name": "nightfall", "kind": "DEMO",
			 "parts": [{"name": "Plasma", "start": 23400}], "events": [], "build": {"crunch": false}}
			""";
		final ProjectFile read = ProjectFile.parse(later);
		final ProjectFile again = ProjectFile.parse(read.withSymbols(true).toJson());

		assertEquals(List.of("parts", "events"), List.copyOf(again.extras().keySet()));
		assertEquals(23_400, again.extras().getJsonArray("parts").getJsonObject(0).getInt("start"));
		assertTrue(again.symbols());
		assertFalse(again.crunch());
	}

	@Example
	public void shouldTakeTheDefaultsOfANewProjectForWhatIsLeftOut() {
		assertEquals(ProjectFile.create("blitzball", ProjectKind.GAME, Target.A500_OCS),
			ProjectFile.parse("{\"name\": \"blitzball\", \"kind\": \"GAME\"}"));
	}

	@Example
	public void shouldReadABreakpointAsNotHitYet() {
		assertEquals(List.of(new Breakpoint("main.s", 5, 0)), ProjectFile.parse("""
			{"name": "nightfall", "kind": "DEMO", "breakpoints": [{"file": "main.s", "line": 5}]}
			""").breakpoints());
	}

	@Example
	public void shouldRefuseAProjectFromANewerVersion() {
		assertEquals("project format 2 is newer than this version of Copperhead",
			refusal("{\"format\": 2, \"name\": \"nightfall\", \"kind\": \"DEMO\"}"));
	}

	@Example
	public void shouldRefuseWhatIsNoProjectFile() {
		assertAll(Stream.of("", "{", "[]", "nightfall", "{\"kind\": \"DEMO\"}", "{\"name\": \"nightfall\"}",
			"{\"name\": 7, \"kind\": \"DEMO\"}", "{\"name\": \"nightfall\", \"kind\": \"MOVIE\"}")
			.map(json -> () -> assertTrue(refusal(json).startsWith("not a project file"), json)));
	}

	@Example
	public void shouldRefuseValuesNoProjectCanHave() {
		assertAll(Stream.of("\"name\": \"../nightfall\", \"kind\": \"DEMO\"",
			"\"name\": \"nightfall\", \"kind\": \"DEMO\", \"target\": \"ECS\"",
			"\"name\": \"nightfall\", \"kind\": \"DEMO\", \"loadAddress\": \"$3FF\"",
			"\"name\": \"nightfall\", \"kind\": \"DEMO\", \"entry\": \"../../.ssh/id_rsa\"",
			"\"name\": \"nightfall\", \"kind\": \"DEMO\", \"build\": true",
			"\"name\": \"nightfall\", \"kind\": \"DEMO\", \"breakpoints\": [{\"file\": \"main.s\"}]",
			"\"name\": \"nightfall\", \"kind\": \"DEMO\", \"breakpoints\": [3]")
			.map(json -> () -> assertTrue(refusal("{" + json + "}").startsWith("not a project file"), json)));
	}

	@Example
	public void shouldRefuseAFormatThatIsNoPositiveInt() {
		assertAll(Stream.of("\"2\"", "4294967297", "0", "-1", "1.5", "true", "null", "[]")
			.map(format -> () -> assertTrue(refusal("{\"format\": " + format + ", \"name\": \"nightfall\", "
				+ "\"kind\": \"DEMO\"}").startsWith("not a project file"), format)));
	}

	@Example
	public void shouldRefuseAKeyOfTheWrongTypeInsteadOfTakingItsDefault() {
		assertAll(Stream.of("\"target\": 5", "\"loadAddress\": 131072", "\"entry\": 7",
			"\"build\": {\"crunch\": \"no\"}", "\"build\": {\"symbols\": 1}", "\"build\": []",
			"\"breakpoints\": {}")
			.map(json -> () -> assertTrue(refusal("{\"name\": \"nightfall\", \"kind\": \"DEMO\", " + json + "}")
				.startsWith("not a project file"), json)));
	}

	@Example
	public void shouldRefuseABreakpointNoSourceLineCanHold() {
		assertAll(Stream.of("{\"file\": \"main.s\", \"line\": 4294967297}", "{\"file\": \"main.s\", \"line\": 1.5}",
			"{\"file\": \"main.s\", \"line\": 0}", "{\"file\": \"main.s\", \"line\": \"3\"}",
			"{\"file\": \"../../x\", \"line\": 3}", "{\"file\": \"src/main.s\", \"line\": 3}",
			"{\"file\": 7, \"line\": 3}")
			.map(mark -> () -> assertTrue(refusal("{\"name\": \"nightfall\", \"kind\": \"DEMO\", "
				+ "\"breakpoints\": [" + mark + "]}").startsWith("not a project file"), mark)));
	}

	@Example
	public void shouldNotShowARawCastFailureForAWrongType() {
		assertFalse(refusal("{\"name\": \"nightfall\", \"kind\": \"DEMO\", \"build\": true}").contains("cast"));
	}

	@Example
	public void shouldChangeOnlyWhatEachWithNames() {
		final ProjectFile changed = DEMO.withTarget(Target.A1200_AGA).withLoadAddress(0x4_0000)
			.withEntry("src/intro.s").withCrunch(false).withSymbols(true)
			.withBreakpoints(List.of(new Breakpoint("intro.s", 3, 0)));

		assertEquals(new ProjectFile(1, "nightfall", ProjectKind.DEMO, Target.A1200_AGA, 0x4_0000, "src/intro.s",
			false, true, List.of(new Breakpoint("intro.s", 3, 0)), JsonValue.EMPTY_JSON_OBJECT), changed);
	}

	@Example
	public void shouldBuildWithTheOptionsItKeeps() {
		assertEquals(new BuildOptions(true, false), DEMO.options());
		assertEquals(new BuildOptions(false, true), DEMO.withCrunch(false).withSymbols(true).options());
	}

	@Example
	public void shouldRefuseAnEntryOutsideTheSources() {
		assertThrows(IllegalArgumentException.class, () -> DEMO.withEntry("main.s"));
		assertThrows(IllegalArgumentException.class, () -> DEMO.withEntry("src/../main.s"));
	}

	@Example
	public void shouldNotLetAnUnknownKeyHideAKnownOne() {
		final ProjectFile file = new ProjectFile(1, "nightfall", ProjectKind.DEMO, Target.A500_OCS, 0x2_0000,
			"src/main.s", true, false, List.of(), object(Map.of("name", Json.createValue("other"))));

		assertEquals(JsonValue.EMPTY_JSON_OBJECT, file.extras());
		assertEquals("nightfall", ProjectFile.parse(file.toJson()).name());
	}
}
