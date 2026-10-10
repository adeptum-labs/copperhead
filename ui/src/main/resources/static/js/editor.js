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

/* The server draws the Code tool anew whenever the project changes, sending every source of the project with it,
   but the text being typed, its undo history and its place on screen outlive that: the page keeps an editor state
   for each source and puts a new editor over the active one each time the tool comes back. The server hears of
   the text only through copperhead.editor, which the controls that save, build or switch projects send. */
(() => {
	const kit = CopperheadEditor;
	const SAVE = "/action/file-save";
	const WRITERS = new Set(["/action/file-save-all", "/action/build", "/action/build-run"]);
	const REPORT_MILLIS = 400;

	const buffers = new Map();
	/* Lines asked for in the gutter, oldest first: a request waits its turn, and the caret may move meanwhile. */
	const asked = [];
	let shown = null;
	let project = null;
	/* Where the server holds the cursor: where it last drew it, or heard it was. */
	let reported = { name: null, line: 0 };
	let reporting = 0;

	const host = () => document.querySelector("#tool [data-editor-host]");

	const press = (control) => host()?.querySelector(`[data-editor-control=${control}]`)?.click();

	const caret = () => kit.caretLine(shown.view.state);

	const unsaved = () => [...buffers.values()].filter((buffer) => kit.dirty(buffer.state));

	/* The tabs are the server's, so the page lights the mark of whichever tab names a source with unsaved text. */
	const paintTabs = () => {
		const names = new Set(unsaved().map((buffer) => buffer.name));

		for (const tab of document.querySelectorAll("#tool .tool__tab")) {
			tab.querySelector(".tool__mark")?.classList.toggle("tool__mark--on",
				names.has(tab.firstElementChild?.textContent.trim()));
		}
	};

	const apply = (buffer, spec) => {
		if (shown?.buffer === buffer) {
			shown.view.dispatch(spec);
		} else {
			buffer.state = buffer.state.update(spec).state;
		}
	};

	/* The server learns where the caret rests once it stops moving, for F9 and for the next time it draws the tool. */
	const report = () => {
		clearTimeout(reporting);
		reporting = setTimeout(() => {
			if (shown && reported.name === shown.buffer.name && caret() !== reported.line) {
				reported.line = caret();
				press("cursor");
			}
		}, REPORT_MILLIS);
	};

	const hooks = {
		toggle: (view, line) => {
			asked.push(line);
			press("breakpoint");
			return true;
		}
	};

	/* Where the text was scrolled to and whether it had the keys can only be read while the editor is on the page. */
	const detach = () => {
		if (shown?.view.dom.isConnected) {
			shown.buffer.scroll = shown.view.scrollSnapshot();
			shown.buffer.focused = shown.view.hasFocus;
		}

		shown?.view.destroy();
		shown = null;
	};

	const sources = (sent) => sent.files.map((file) => ({
		...file,
		breakpoints: sent.breakpoints.filter((mark) => mark.file === file.name).map((mark) => mark.line),
		diagnostics: sent.diagnostics.filter((message) => message.file === file.name)
	}));

	/* Takes every source the server sent into the buffer the page holds for it, and says which ones are new. */
	const take = (sent) => {
		const fresh = new Set();

		if (sent.project !== project) {
			buffers.clear();
			project = sent.project;
		}

		for (const name of buffers.keys()) {
			if (!sent.files.some((file) => file.name === name)) {
				buffers.delete(name);
			}
		}

		for (const source of sources(sent)) {
			const known = buffers.get(source.name);

			if (known) {
				known.state = kit.reconcile(known.state, source).state;
			} else {
				buffers.set(source.name, { name: source.name, state: kit.createState(source, hooks), scroll: null });
				fresh.add(source.name);
			}
		}

		return fresh;
	};

	/* The caret goes where the server puts it on a source shown for the first time; otherwise it stays where it was,
	   and the server hears of that instead. */
	const place = (sent, first) => {
		if (first) {
			kit.reveal(shown.view, sent.cursor);
		}

		reported = { name: sent.active, line: sent.cursor };
		report();
	};

	const mount = (root) => {
		const sent = JSON.parse(root.querySelector("script[data-source]").textContent);
		const fresh = take(sent);
		const buffer = buffers.get(sent.active);

		root.dataset.mounted = "true";
		asked.length = 0;
		shown = {
			buffer,
			view: kit.mount(root.querySelector("[data-editor]"), buffer.state, (state) => {
				buffer.state = state;
				paintTabs();
				report();
			})
		};

		if (buffer.scroll) {
			shown.view.dispatch({ effects: buffer.scroll });
		}

		place(sent, fresh.has(sent.active));

		if (buffer.focused) {
			shown.view.focus();
		}

		paintTabs();
	};

	const attach = () => {
		const root = host();

		if (shown && !shown.view.dom.isConnected) {
			detach();
		}

		if (root && !root.dataset.mounted) {
			mount(root);
		}
	};

	/* A file changed by another program while the window was away is looked at again when the window comes back. */
	const check = () => {
		if (shown) {
			press("check");
		}
	};

	/* What the controls send, worked out as each request leaves, so text typed meanwhile goes with it. */
	Object.assign(window.copperhead, {
		editor: {
			buffer: () => shown ? { name: shown.buffer.name, text: kit.textOf(shown.buffer.state) } : {},
			buffers: (extra = {}) => {
				const list = unsaved();

				return list.length === 0 ? { ...extra } : {
					...extra,
					files: list.map((buffer) => buffer.name).join(","),
					...Object.fromEntries(list.map((buffer) => [buffer.name, kit.textOf(buffer.state)]))
				};
			},
			line: () => shown ? { line: asked.shift() ?? caret() } : {},
			cursor: () => shown ? { line: caret() } : {},
			check: () => shown ? { name: shown.buffer.name, modified: kit.loaded(shown.buffer.state).modified } : {}
		}
	});

	/* The names and texts a request carried that the server wrote to disk: a save, a save of them all, a build, or a
	   switch the person chose to save first. */
	const written = (path, parameters) => {
		if (path === SAVE) {
			return parameters.has("text") ? [[parameters.get("name"), parameters.get("text")]] : [];
		}

		const saving = WRITERS.has(path) || parameters.get("save") === "true";

		return saving && parameters.has("files")
			? parameters.get("files").split(",").map((name) => [name, parameters.get(name)]) : [];
	};

	/* Once the server has written a text the disk holds it, whether or not the Code tool comes back to say so. */
	document.addEventListener("htmx:afterRequest", (event) => {
		const { successful, requestConfig } = event.detail;

		if (!successful) {
			return;
		}

		for (const [name, text] of written(requestConfig.path, requestConfig.parameters)) {
			const buffer = buffers.get(name);

			if (buffer) {
				apply(buffer, { effects: kit.setDisk.of({ text, modified: kit.loaded(buffer.state).modified }) });
			}
		}

		paintTabs();
	});

	document.addEventListener("htmx:oobBeforeSwap", (event) => {
		if (shown && event.detail.target.contains(shown.view.dom)) {
			detach();
		}
	});

	document.addEventListener("htmx:load", attach);

	/* The Edit menu's items name the editor's command they run. */
	document.addEventListener("click", (event) => {
		const item = event.target.closest(".menu__item[data-editor]");

		if (item && shown) {
			shown.view.focus();
			kit.commands[item.dataset.editor]?.(shown.view);
		}
	});

	window.addEventListener("focus", check);
	document.addEventListener("visibilitychange", () => {
		if (document.visibilityState === "visible") {
			check();
		}
	});
})();
