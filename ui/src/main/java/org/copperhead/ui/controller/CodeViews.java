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

package org.copperhead.ui.controller;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import java.util.List;
import java.util.stream.Stream;
import org.copperhead.core.build.BuildWarning;
import org.copperhead.core.project.ProjectLayout;
import org.copperhead.toolchain.ProjectDirectory;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.CodeView;
import org.copperhead.ui.view.RecentView;
import org.copperhead.ui.view.TabView;
import org.copperhead.ui.view.ToolView;
import org.copperhead.ui.view.Vals;
import org.copperhead.ui.view.WelcomeView;

/**
 * The code tool: a tab per source of the project and the editor, or the welcome while no project is open. The
 * editor's text reaches the page as JSON inside a script element, so a source holding a closing script tag is
 * written with its angle brackets escaped, which JSON reads back as the same text.
 */
final class CodeViews {
	private static final String ERROR = "error";
	private static final String WARNING = "warning";

	private CodeViews() {
	}

	static ToolView tool(final StudioState state) {
		return state.workspace().map(directory -> editor(state, directory)).orElseGet(() -> welcome(state));
	}

	private static ToolView editor(final StudioState state, final ProjectDirectory directory) {
		final List<String> sources = Workspaces.sources(directory);
		final int marks = state.breakpoints().size();

		return new ToolView(sources.stream().map(name -> new TabView(name, "/action/file-open",
				Vals.of("name", name), name.equals(state.file()), false)).toList(),
			"68000 · UTF-8 · " + marks + (marks == 1 ? " breakpoint" : " breakpoints"),
			new CodeView(source(state, directory, sources)));
	}

	private static ToolView welcome(final StudioState state) {
		return new ToolView(List.of(ToolViews.title("Welcome")), "no project open", new WelcomeView(
			state.environment().recent().stream().map(chp -> new RecentView(chp.getFileName().toString(),
				chp.getParent().toString(), Vals.of(ProjectDialogs.PATH, chp))).toList()));
	}

	private static String source(final StudioState state, final ProjectDirectory directory, final List<String> sources) {
		final JsonArrayBuilder files = Json.createArrayBuilder();
		final JsonArrayBuilder breakpoints = Json.createArrayBuilder();
		final JsonArrayBuilder diagnostics = Json.createArrayBuilder();

		sources.forEach(name -> files.add(Json.createObjectBuilder().add("name", name)
			.add("text", Workspaces.read(() -> directory.read(name)))
			.add("modified", Workspaces.read(() -> directory.modified(name)))));
		state.breakpoints().forEach(mark -> breakpoints.add(Json.createObjectBuilder().add("file", mark.file())
			.add("line", mark.line())));
		state.build().stream().flatMap(report -> Stream.concat(
				report.errors().stream().map(error -> diagnostic(error, ERROR)),
				report.warnings().stream().map(warning -> diagnostic(warning, WARNING))))
			.filter(diagnostic -> ProjectLayout.validSource(diagnostic.file()) && diagnostic.line() > 0)
			.forEach(diagnostic -> diagnostics.add(Json.createObjectBuilder().add("file", diagnostic.file())
				.add("line", diagnostic.line()).add("severity", diagnostic.severity())
				.add("message", diagnostic.message())));

		return Json.createObjectBuilder().add("project", directory.dir().toString()).add("active", state.file())
			.add("cursor", state.cursor()).add("files", files).add("breakpoints", breakpoints)
			.add("diagnostics", diagnostics).build().toString().replace("<", "\\u003c");
	}

	private static Diagnostic diagnostic(final BuildWarning message, final String severity) {
		return new Diagnostic(StudioState.sourceName(message.file()), message.line(), severity, message.message());
	}

	private record Diagnostic(String file, int line, String severity, String message) {
	}
}
