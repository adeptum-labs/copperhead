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
	const REPORT_MILLIS = 400;

	const buffers = new Map();
	/* Lines asked for in the gutter, oldest first: a request waits its turn, and the caret may move meanwhile. */
	const asked = [];
	let shown = null;
	/* The folder of the project the buffers were typed in, which the server compares with the one it has open. */
	let project = null;
	/* The source and caret line the person last had, kept while the editor is off the page, since a request that
	   waited for the redraw of the Code tool is worked out then. */
	let active = null;
	let caretAt = 1;
	/* Where the server holds the cursor: where it last drew it, or heard it was. */
	let reported = { name: null, line: 0 };
	let reporting = 0;

	const host = () => document.querySelector("#tool [data-editor-host]");

	const press = (control) => host()?.querySelector(`[data-editor-control=${control}]`)?.click();

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
			if (shown && reported.name === shown.buffer.name && caretAt !== reported.line) {
				reported.line = caretAt;
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

	/* Another project's buffers are no use here, and sent along with a build or a save they would overwrite a file of
	   this one that happens to be named alike. */
	const leave = (folder) => {
		buffers.clear();
		active = null;
		project = folder;
	};

	/* Takes every source the server sent into the buffer the page holds for it, and says which ones are new. A source
	   gone from the disk keeps its buffer while it holds text the disk lacks: dropping it would lose that text. */
	const take = (sent) => {
		const fresh = new Set();

		if (sent.project !== project) {
			leave(sent.project);
		}

		for (const [name, buffer] of buffers) {
			buffer.missing = !sent.files.some((file) => file.name === name);

			if (buffer.missing && !kit.dirty(buffer.state)) {
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
	const place = (sent, buffer, first) => {
		if (first) {
			kit.reveal(shown.view, sent.cursor);
		}

		reported = { name: buffer.name, line: buffer.name === sent.active ? sent.cursor : caretAt };
		report();
	};

	/* The tabs are the server's and hold no tab for a source it no longer lists, so the editor says so itself. */
	const noteMissing = (root, buffer) => {
		const note = document.createElement("div");

		note.className = "code__missing";
		note.setAttribute("role", "status");
		note.textContent = `${buffer.name} is no longer on disk. Save writes it again.`;
		root.prepend(note);
	};

	/* The server's active source is shown unless a source it no longer lists took its place; with none left to show
	   the editor stays empty. */
	const mount = (root) => {
		const sent = JSON.parse(root.querySelector("script[data-source]").textContent);
		const fresh = take(sent);
		const buffer = buffers.get(sent.active) ?? buffers.values().next().value;

		if (buffer) {
			active = buffer.name;
			caretAt = kit.caretLine(buffer.state);
			shown = {
				buffer,
				view: kit.mount(root.querySelector("[data-editor]"), buffer.state, (state) => {
					buffer.state = state;
					caretAt = kit.caretLine(state);
					paintTabs();
					report();
				})
			};

			asked.length = 0;

			if (buffer.scroll) {
				shown.view.dispatch({ effects: buffer.scroll });
			}

			place(sent, buffer, fresh.has(buffer.name) && buffer.name === sent.active);

			if (buffer.missing) {
				noteMissing(root, buffer);
			}

			if (buffer.focused) {
				shown.view.focus();
			}
		}

		root.dataset.mounted = "true";
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

	/* A file changed by another program while the window was away is looked at again when the window comes back. A
	   source the disk no longer holds has nothing to be looked at. */
	const check = () => {
		if (shown && !shown.buffer.missing) {
			press("check");
		}
	};

	const held = () => buffers.get(active);

	/* What the controls send, worked out as each request leaves, so text typed meanwhile goes with it. The getters
	   answer while the editor is off the page too: a request that waited for the Code tool to be drawn anew is
	   worked out in between. */
	Object.assign(window.copperhead, {
		editor: {
			buffer: () => held() ? { name: active, text: kit.textOf(held().state), project } : {},
			buffers: (extra = {}) => {
				const list = unsaved();

				return list.length === 0 ? { ...extra } : {
					...extra,
					project,
					files: list.map((buffer) => buffer.name).join(","),
					...Object.fromEntries(list.map((buffer) => [buffer.name, kit.textOf(buffer.state)]))
				};
			},
			line: () => held() ? { line: asked.shift() ?? caretAt } : {},
			cursor: () => held() ? { line: caretAt } : {},
			check: () => held() ? { name: active, modified: kit.loaded(held().state).modified } : {}
		}
	});

	/* The text a request carried for a source, if it carried one. */
	const carried = (path, parameters, name) => {
		if (path === SAVE) {
			return parameters.get("name") === name ? parameters.get("text") : null;
		}

		return (parameters.get("files") ?? "").split(",").includes(name) ? parameters.get(name) : null;
	};

	/* The answer says which sources the server wrote, so a write the disk refused is never taken for a save. */
	const confirmed = (status) => (status?.dataset.saved ?? "").split(",").filter((name) => name !== "");

	/* Once the server has written a text the disk holds it, whether or not the Code tool comes back to say so. This is
	   taken before the answer is drawn, so a Code tool drawn with it finds the text the disk holds and leaves what was
	   typed since the request left as it is. An answer from another project than the one the buffers belong to takes
	   them away, whichever tool is shown. */
	document.addEventListener("htmx:beforeSwap", (event) => {
		const { shouldSwap, xhr, requestConfig } = event.detail;

		if (!shouldSwap || !xhr.responseText) {
			return;
		}

		const answer = new DOMParser().parseFromString(xhr.responseText, "text/html");
		const status = answer.getElementById("status");
		const folder = status?.dataset.project;

		if (folder !== undefined && folder !== project) {
			leave(folder);
		}

		for (const name of confirmed(status)) {
			const buffer = buffers.get(name);
			const text = carried(requestConfig.path, requestConfig.parameters, name);

			if (buffer && text !== null) {
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

	/* The text lives in the page only, so leaving the page asks first while any is unsaved. */
	window.addEventListener("beforeunload", (event) => {
		if (unsaved().length > 0) {
			event.preventDefault();
		}
	});

	window.addEventListener("focus", check);
	document.addEventListener("visibilitychange", () => {
		if (document.visibilityState === "visible") {
			check();
		}
	});
})();
