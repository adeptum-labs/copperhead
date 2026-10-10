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

import assert from "node:assert/strict";
import { test } from "node:test";
import { forEachDiagnostic } from "@codemirror/lint";
import {
	breakpointLines, caretLine, createState, dirty, keepMine, loaded, reconcile, setDisk, takeDisk, textOf
} from "./kit.js";

const MAIN = "start:\n\tmove.l #$20000,d0\n\trts\n";

const source = (changes = {}) => ({ name: "main.s", text: MAIN, modified: 100, breakpoints: [], diagnostics: [],
	...changes });

const type = (state, at, text) => state.update({ changes: { from: at, insert: text } }).state;

const messages = (state) => {
	const found = [];

	forEachDiagnostic(state, (message, from, to) => found.push([message.severity, from, to, message.message]));
	return found.sort((first, second) => first[1] - second[1]);
};

test("a text keeps its line endings, CRLF throughout as well as a stray CR", () => {
	for (const text of ["a\nb\n", "a\r\nb\r\n", "a\r\nb\nc\r", "no ending"]) {
		assert.equal(textOf(createState(source({ text }))), text, JSON.stringify(text));
	}
});

test("a text is changed once it differs from what the disk holds, and no longer once the disk holds it", () => {
	const typed = type(createState(source()), 0, "; note\n");

	assert.equal(dirty(createState(source())), false);
	assert.equal(dirty(typed), true);
	assert.equal(dirty(typed.update({ effects: setDisk.of({ text: textOf(typed), modified: 0 }) }).state), false);
});

test("the file as loaded is the text and the time of change the server sent", () => {
	assert.deepEqual(loaded(type(createState(source()), 0, "x")), { text: MAIN, modified: 100 });
});

test("the server's breakpoints are put on their lines, at most one to a line and none past the end", () => {
	assert.deepEqual(breakpointLines(createState(source({ breakpoints: [3, 1, 3, 40] }))), [1, 3, 4]);
});

test("a breakpoint stays with its line as lines are added above it", () => {
	const state = createState(source({ breakpoints: [3] }));

	assert.deepEqual(breakpointLines(type(state, 0, "; one\n; two\n")), [5]);
	assert.deepEqual(breakpointLines(type(state, state.doc.line(3).from, "\n")), [4]);
	assert.deepEqual(breakpointLines(type(state, state.doc.line(3).from, ";")), [3]);
});

test("the server's lists are taken over only when they changed since it last sent them", () => {
	const shifted = type(createState(source({ breakpoints: [2] })), 0, "; new\n");

	assert.deepEqual(breakpointLines(reconcile(shifted, source({ breakpoints: [2] })).state), [3]);
	assert.deepEqual(breakpointLines(reconcile(shifted, source({ breakpoints: [2, 4] })).state), [2, 4]);
});

test("a message marks its line from the first character that is not blank, the last line at most", () => {
	const state = createState(source({
		diagnostics: [
			{ line: 2, severity: "warning", message: "careful" },
			{ line: 1, severity: "note", message: "about" },
			{ line: 9, severity: "error", message: "past the end" }
		]
	}));

	assert.deepEqual(messages(state), [
		["info", 0, 6, "about"], ["warning", 8, 25, "careful"], ["error", 31, 31, "past the end"]
	]);
});

test("messages move with the text, and the same list sent again does not move them back", () => {
	const listed = source({ diagnostics: [{ line: 3, severity: "error", message: "bad" }] });
	const moved = type(createState(listed), 0, "; new\n");

	assert.deepEqual(messages(moved), [["error", 33, 36, "bad"]]);
	assert.deepEqual(messages(reconcile(moved, listed).state), [["error", 33, 36, "bad"]]);
	assert.deepEqual(messages(reconcile(moved, source()).state), []);
});

test("a file the disk still holds keeps the text, its history and its caret", () => {
	const typed = type(createState(source()), 0, "x").update({ selection: { anchor: 9 } }).state;
	const { state, outcome } = reconcile(typed, source({ modified: 200 }));

	assert.equal(outcome, "kept");
	assert.equal(textOf(state), `x${MAIN}`);
	assert.equal(caretLine(state), 2);
	assert.equal(dirty(state), true);
	assert.equal(loaded(state).modified, 200);
});

test("a file the disk now holds as the page does is no longer changed", () => {
	const typed = type(createState(source()), 0, "x");
	const { state, outcome } = reconcile(typed, source({ text: `x${MAIN}`, modified: 200 }));

	assert.equal(outcome, "kept");
	assert.equal(dirty(state), false);
});

test("a file changed on disk under unchanged text is shown as the disk holds it", () => {
	const { state, outcome } = reconcile(createState(source()), source({ text: "other\n", modified: 200 }));

	assert.equal(outcome, "reloaded");
	assert.equal(textOf(state), "other\n");
	assert.equal(dirty(state), false);
});

test("a file changed on disk under changed text is left to the user, who keeps theirs or takes the disk's", () => {
	const typed = type(createState(source()), 0, "; mine\n");
	const { state, outcome } = reconcile(typed, source({ text: "other\n", modified: 200 }));

	assert.equal(outcome, "conflict");
	assert.equal(textOf(state), `; mine\n${MAIN}`);

	const kept = state.update(keepMine(state)).state;
	const taken = state.update(takeDisk(state)).state;

	assert.equal(textOf(kept), `; mine\n${MAIN}`);
	assert.equal(dirty(kept), true);
	assert.equal(loaded(kept).modified, 200);
	assert.equal(reconcile(kept, source({ text: "other\n", modified: 200 })).outcome, "kept");
	assert.equal(textOf(taken), "other\n");
	assert.equal(dirty(taken), false);
});
