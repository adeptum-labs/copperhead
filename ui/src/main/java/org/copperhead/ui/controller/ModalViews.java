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

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.copperhead.toolchain.PinnedSource;
import org.copperhead.ui.session.BuildRun.TrustRequest;
import org.copperhead.ui.session.Layout;
import org.copperhead.ui.session.Preferences;
import org.copperhead.ui.session.StudioState;
import org.copperhead.ui.view.ButtonView;
import org.copperhead.ui.view.ChoiceView;
import org.copperhead.ui.view.FieldView;
import org.copperhead.ui.view.Modal;
import org.copperhead.ui.view.ModalView;
import org.copperhead.ui.view.PairView;
import org.copperhead.ui.view.Vals;

final class ModalViews {
	static final String THEME = "theme";
	static final String VASM = PinnedSource.VASM.tool();
	static final String SHRINKLER = PinnedSource.SHRINKLER.tool();

	private static final String FIELDS = Vals.spread("copperhead.fields(this)");
	private static final ModalView CLOSED = new ModalView(Modal.NONE, "", List.of(), List.of());
	private static final List<PairView> SHORTCUTS = List.of(
		new PairView("Play / pause", "F5"),
		new PairView("Step / step over", "F8 · F10"),
		new PairView("Toggle breakpoint on cursor line", "F9"),
		new PairView("Save / save all", "Ctrl+S · Ctrl+Shift+S"),
		new PairView("Build ADF / build & run", "Ctrl+B · Ctrl+Shift+B"),
		new PairView("Switch tool", "Ctrl+1 – Ctrl+5"),
		new PairView("Show / hide timeline", "Ctrl+J"),
		new PairView("Light / dark mode", Menus.LIGHT_DARK),
		new PairView("Preferences", "Ctrl+,"),
		new PairView("Open menu", "click · hover to switch"),
		new PairView("Close menu / dialog", "Esc"));

	private ModalViews() {
	}

	/* The dialogs drawn from what their request brought close when drawn from the state alone, on a fresh page. */
	static ModalView of(final StudioState state) {
		return switch (state.modal()) {
			case NONE, NEW_PROJECT, OPEN_PROJECT, PREFERENCES, DISCARD -> CLOSED;
			case SHORTCUTS -> new ModalView(Modal.SHORTCUTS, "Keyboard shortcuts", SHORTCUTS, List.of());
			case ABOUT -> new ModalView(Modal.ABOUT, "About Copperhead", List.of(), List.of(
				"Copperhead · Amiga demo & game studio",
				String.join(" · ", tool(PinnedSource.VASM), tool(PinnedSource.SHRINKLER), "FS-UAE 3.1 core",
					"OCS/ECS/AGA"),
				state.workspace().map(directory -> "Project: " + directory.chp()).orElse("No project open"),
				"Copyright © 2026 Adam Waldenberg and Adeptum AB · GNU General Public License, version 3"));
			case SETTINGS -> state.workspace().map(directory -> ProjectDialogs.settings(state,
				ProjectDialogs.settingsOf(directory.file()), "")).orElse(CLOSED);
			case TRUST -> state.run().trust().map(ModalViews::trust).orElse(CLOSED);
		};
	}

	/** The person's preferences, as saved or as typed when they were refused. */
	static ModalView preferences(final Map<String, String> values, final String error) {
		return new ModalView(Modal.PREFERENCES, "Preferences", List.of(),
			List.of(error.isEmpty() ? "Leave a tool empty to have Copperhead fetch and compile the version it "
				+ "pins." : error),
			List.of(), List.of(
				new FieldView(THEME, "Theme", values.get(THEME), List.of(
					new ChoiceView(Layout.DARK, "Dark", Layout.DARK.equals(values.get(THEME))),
					new ChoiceView(Layout.LIGHT, "Light", Layout.LIGHT.equals(values.get(THEME))))),
				FieldView.text(VASM, tool(PinnedSource.VASM), values.get(VASM)),
				FieldView.text(SHRINKLER, tool(PinnedSource.SHRINKLER), values.get(SHRINKLER))),
			List.of(new ButtonView("Save", "/action/preferences-save", FIELDS, true),
				new ButtonView("Cancel", "/action/modal-close", null, false)));
	}

	static Map<String, String> preferencesOf(final Preferences preferences) {
		return Map.of(THEME, preferences.layout().theme(),
			VASM, preferences.tool(VASM).map(Object::toString).orElse(""),
			SHRINKLER, preferences.tool(SHRINKLER).map(Object::toString).orElse(""));
	}

	/* Trusting is the choice that can hurt, so it is never the one Enter makes: Cancel has the keys and the accent. */
	private static ModalView trust(final TrustRequest request) {
		final String asked = Vals.of("sha256", request.sha256());

		return new ModalView(Modal.TRUST, "Unexpected " + request.tool() + " source", List.of(
				new PairView("Version", request.version()), new PairView("SHA-256", request.sha256())),
			List.of(fetched(request.tool())
					+ " and its SHA-256 checksum differs from the one Copperhead knows.",
				"Trust it only if you know where this version comes from. Trusting builds with it and "
					+ "remembers this checksum; Cancel stops the build."),
			List.of(), List.of(), List.of(
				new ButtonView("Cancel", "/action/trust-refuse", asked, true, true),
				new ButtonView("Trust this download", "/action/trust-accept", asked, false)));
	}

	/* What came over plain HTTP may have been changed on the way, which weighs on whether to trust it. */
	private static String fetched(final String tool) {
		final boolean plain = Stream.of(PinnedSource.VASM, PinnedSource.SHRINKLER)
			.filter(source -> source.tool().equals(tool))
			.anyMatch(source -> "http".equals(source.uri().getScheme()));

		return "The " + tool + " source was fetched over " + (plain
			? "unencrypted HTTP, so anyone on the network path could have changed it," : "HTTPS,");
	}

	private static String tool(final PinnedSource source) {
		return source.tool() + " " + source.version();
	}
}
