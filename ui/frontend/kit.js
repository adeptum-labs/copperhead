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

import { minimalSetup } from "codemirror";
import { indentLess, insertTab, redo, undo } from "@codemirror/commands";
import { indentUnit } from "@codemirror/language";
import { lintGutter, setDiagnostics } from "@codemirror/lint";
import { gotoLine, highlightSelectionMatches, openSearchPanel, search, searchKeymap } from "@codemirror/search";
import { EditorState, RangeSet, StateEffect, StateField } from "@codemirror/state";
import {
	EditorView, GutterMarker, gutter, highlightActiveLine, highlightActiveLineGutter, keymap, lineNumbers, showPanel
} from "@codemirror/view";
import { m68kSupport } from "./m68k.js";

/* One source as the page holds it between the times the server draws the Code tool anew: the text with its undo
   history, its breakpoints and messages, and what the disk held when the page last read or wrote it. All of it
   lives in the editor state, so a state put away and taken out again under a new view is the buffer itself. A
   source comes as {name, text, modified, breakpoints: [line], diagnostics: [{line, severity, message}]}. */

const TAB_SIZE = 8;

const SEVERITIES = [["error", /^err/i], ["warning", /^warn/i]];

const NO_HOOKS = { toggle: () => false };

const last = (effects, type, value) => effects.reduce((held, effect) => effect.is(type) ? effect.value : held, value);

const clamp = (doc, line) => Math.min(Math.max(line, 1), doc.lines);

/* A file saved with CRLF throughout is split on CRLF and written back with it. Any other mix is split on LF only, so a
   stray CR stays in its line and goes back to the disk where it was. */
const separatorOf = (text) => text.includes("\r\n") && !/(^|[^\r])\n/.test(text) ? "\r\n" : "\n";

export const textOf = (state) => state.sliceDoc();

export const setDisk = StateEffect.define();

const disk = StateField.define({
	create: () => ({ text: "", modified: 0 }),
	update: (held, transaction) => last(transaction.effects, setDisk, held)
});

export const dirty = (state) => textOf(state) !== state.field(disk).text;

/* What the disk held of the file as the page last read or wrote it, its time of change included. */
export const loaded = (state) => state.field(disk);

/* The lists the server sent the last time, so a list it did not change does not undo what the page did since. */
const setServed = StateEffect.define();

const served = StateField.define({
	create: () => ({ breakpoints: "[]", diagnostics: "[]" }),
	update: (held, transaction) => last(transaction.effects, setServed, held)
});

const setBreakpoints = StateEffect.define();

/* A marker follows the text after it, so a line pushed down by Enter pressed at its start keeps its breakpoint. */
class BreakpointMarker extends GutterMarker {
	startSide = 1;

	endSide = 1;

	toDOM() {
		const dot = document.createElement("span");

		dot.className = "code__dot code__dot--on";
		return dot;
	}
}

const MARKER = new BreakpointMarker();

const linesOf = (markers, doc) => {
	const lines = new Set();

	for (const cursor = markers.iter(); cursor.value !== null; cursor.next()) {
		lines.add(doc.lineAt(cursor.from).number);
	}

	return [...lines].sort((first, second) => first - second);
};

const markersAt = (doc, lines) => RangeSet.of([...new Set(lines.map((line) => clamp(doc, line)))]
	.map((line) => MARKER.range(doc.line(line).from)), true);

/* Positions are mapped through every edit, so a breakpoint stays with its line as lines come and go above it, until
   the server sends a list of its own. */
const breakpoints = StateField.define({
	create: () => RangeSet.empty,
	update: (markers, transaction) => transaction.effects.reduce((next, effect) => effect.is(setBreakpoints)
		? markersAt(transaction.state.doc, effect.value) : next, markers.map(transaction.changes))
});

export const breakpointLines = (state) => linesOf(state.field(breakpoints), state.doc);

export const caretLine = (state) => state.doc.lineAt(state.selection.main.head).number;

const severityOf = (name) => SEVERITIES.find(([, pattern]) => pattern.test(name ?? ""))?.[0] ?? "info";

/* The assembler names only the line, so a message marks it from its first character that is not blank. */
const diagnosticAt = (doc) => ({ line, severity, message }) => {
	const at = doc.line(clamp(doc, line));
	const from = at.from + at.text.length - at.text.trimStart().length;

	return { from, to: Math.max(from, at.to), severity: severityOf(severity), message };
};

const marked = (state, source) => {
	const lists = {
		breakpoints: JSON.stringify(source.breakpoints ?? []),
		diagnostics: JSON.stringify(source.diagnostics ?? [])
	};
	const before = state.field(served);
	const effects = [setServed.of(lists)];

	if (lists.breakpoints !== before.breakpoints) {
		effects.push(setBreakpoints.of(source.breakpoints ?? []));
	}

	const listed = state.update({ effects }).state;

	return lists.diagnostics === before.diagnostics ? listed
		: listed.update(setDiagnostics(listed, (source.diagnostics ?? []).map(diagnosticAt(listed.doc)))).state;
};

const setConflict = StateEffect.define();

const settled = (state) => {
	const { text, modified } = state.field(conflict);

	return [setDisk.of({ text, modified }), setConflict.of(null)];
};

/* The disk took a new text while the page held changes of its own: which one stays is the user's choice. */
export const takeDisk = (state) => ({
	changes: { from: 0, to: state.doc.length, insert: state.field(conflict).text },
	effects: settled(state)
});

export const keepMine = (state) => ({ effects: settled(state) });

const button = (label, action) => {
	const control = document.createElement("button");

	control.type = "button";
	control.className = "button button--mini";
	control.textContent = label;
	control.addEventListener("click", action);
	return control;
};

const conflictPanel = (changed) => (view) => {
	const dom = document.createElement("div");
	const note = document.createElement("span");

	dom.className = "cm-conflict";
	dom.setAttribute("role", "alert");
	note.textContent = `${changed.name} was changed on disk.`;
	dom.append(note, button("Reload", () => view.dispatch(takeDisk(view.state))),
		button("Keep mine", () => view.dispatch(keepMine(view.state))));
	return { dom, top: true };
};

const conflict = StateField.define({
	create: () => null,
	update: (held, transaction) => last(transaction.effects, setConflict, held),
	provide: (field) => showPanel.from(field, (changed) => changed && conflictPanel(changed))
});

/* A breakpoint is asked for from its own gutter or the line numbers, and the page decides through the hooks what that
   means to the server; F9 belongs to the studio's menu, which sends the caret's line. */
const editing = (hooks) => {
	const toggle = (view, block) => hooks.toggle(view, view.state.doc.lineAt(block.from).number);

	return [
		keymap.of([
			{ key: "Mod-g", run: gotoLine, preventDefault: true },
			{ key: "Tab", run: insertTab, shift: indentLess }
		]),
		gutter({
			class: "cm-breakpoint-gutter",
			markers: (view) => view.state.field(breakpoints),
			renderEmptyElements: true,
			domEventHandlers: { mousedown: toggle }
		}),
		lineNumbers({ domEventHandlers: { mousedown: toggle } }),
		lintGutter(),
		highlightActiveLineGutter(),
		highlightActiveLine(),
		highlightSelectionMatches(),
		search({ top: true }),
		keymap.of(searchKeymap),
		minimalSetup,
		m68kSupport,
		EditorState.tabSize.of(TAB_SIZE),
		indentUnit.of("\t"),
		EditorView.contentAttributes.of({ spellcheck: "false", autocapitalize: "off", "aria-label": "Source" })
	];
};

export const createState = (source, hooks = NO_HOOKS) => marked(EditorState.create({
	doc: source.text,
	extensions: [
		EditorState.lineSeparator.of(separatorOf(source.text)),
		disk.init(() => ({ text: source.text, modified: source.modified })),
		served,
		breakpoints,
		conflict,
		editing(hooks)
	]
}), source);

/* Takes what the server sent for a file into the state the page holds for it. The text on screen stays the user's
   until the disk holds something else:
     kept       the disk holds what it held, or what the page holds now
     reloaded   the disk changed and nothing on screen was, so the disk's text is shown; undo brings the old one back
     conflict   both changed, and a bar above the text asks which one stays */
export const reconcile = (state, source) => {
	const held = state.field(disk);
	const caughtUp = { effects: [setDisk.of({ text: source.text, modified: source.modified }), setConflict.of(null)] };

	if (source.text === held.text || source.text === textOf(state)) {
		return { state: marked(state.update(caughtUp).state, source), outcome: "kept" };
	}

	if (!dirty(state)) {
		const reloaded = state.update({ ...caughtUp, changes: { from: 0, to: state.doc.length, insert: source.text } });

		return { state: marked(reloaded.state, source), outcome: "reloaded" };
	}

	return { state: marked(state.update({ effects: setConflict.of(source) }).state, source), outcome: "conflict" };
};

export const mount = (parent, state, changed) => new EditorView({
	state,
	parent,
	dispatchTransactions: (transactions, view) => {
		view.update(transactions);
		changed(view.state);
	}
});

export const reveal = (view, line) => {
	const at = view.state.doc.line(clamp(view.state.doc, line));

	view.dispatch({ selection: { anchor: at.from }, effects: EditorView.scrollIntoView(at.from, { y: "center" }) });
};

/* What the Edit menu's items do to the text on screen, by the names the items carry. */
export const commands = { undo, redo, find: openSearchPanel, "goto-line": gotoLine };
