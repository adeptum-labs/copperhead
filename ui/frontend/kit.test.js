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

const LISTS = {
	breakpoints: [2, 3],
	diagnostics: [{ line: 2, severity: "error", message: "bad" }]
};

const marks = (state) => [breakpointLines(state), messages(state).map(([severity, , , message]) => [severity, message])];

test("a file reloaded from the disk keeps its breakpoints and messages when the server sends the same lists", () => {
	const { state, outcome } = reconcile(createState(source(LISTS)),source({ ...LISTS, text: "a\nb\nc\nd\n", modified: 200 }));

	assert.equal(outcome, "reloaded");
	assert.deepEqual(marks(state), [[2, 3], [["error", "bad"]]]);
	assert.deepEqual(messages(state).map(([, from, to]) => [from, to]), [[2, 3]]);
});

test("reconciling the same source again changes nothing", () => {
	const again = source({ ...LISTS, text: "a\nb\nc\nd\n", modified: 200 });
	const first = reconcile(createState(source(LISTS)), again).state;
	const second = reconcile(first, again);

	assert.equal(second.outcome, "kept");
	assert.deepEqual(marks(second.state), marks(first));
	assert.equal(textOf(second.state), "a\nb\nc\nd\n");
});

test("taking the disk's text puts the last lists on it", () => {
	const typed = type(createState(source(LISTS)), 0, "; mine\n");
	const { state } = reconcile(typed, source({ ...LISTS, text: "a\nb\nc\nd\n", modified: 200 }));
	const taken = state.update(takeDisk(state)).state;

	assert.equal(textOf(taken), "a\nb\nc\nd\n");
	assert.deepEqual(marks(taken), [[2, 3], [["error", "bad"]]]);
	assert.deepEqual(messages(taken).map(([, from, to]) => [from, to]), [[2, 3]]);
});

test("keeping my text leaves my edits, breakpoints and messages as they are", () => {
	const typed = type(createState(source(LISTS)), 0, "; mine\n");
	const { state } = reconcile(typed, source({ ...LISTS, text: "other\n", modified: 200 }));
	const kept = state.update(keepMine(state)).state;

	assert.equal(textOf(kept), `; mine\n${MAIN}`);
	assert.deepEqual(marks(kept), marks(state));
	assert.deepEqual(breakpointLines(kept), [3, 4]);
});

test("entries the server got wrong are placed at the first line or the last, and nothing throws", () => {
	const garbage = [null, "x", {}, { line: NaN, message: "nan" }, { line: -4, message: "negative" },
		{ line: 1e9, message: "huge" }, { line: "2", severity: 7, message: "text" }];
	const state = createState(source({ breakpoints: [null, "x", NaN, -3, 1e9, undefined, Infinity], diagnostics: garbage }));

	assert.deepEqual(breakpointLines(state), [1, 4]);
	assert.equal(messages(state).length, garbage.length);
	assert.doesNotThrow(() => reconcile(state, source({ breakpoints: [NaN], diagnostics: garbage, text: "z\n" })));
});

test("a disk that moved from LF to CRLF and back is shown without stray carriage returns", () => {
	const lf = createState(source({ text: "a\nb\n" }));
	const crlf = reconcile(lf, source({ text: "a\r\nb\r\nc\r\n", modified: 200 })).state;

	assert.equal(crlf.doc.lines, 4);
	assert.equal(crlf.doc.line(1).text, "a");
	assert.equal(textOf(crlf), "a\r\nb\r\nc\r\n");
	assert.equal(dirty(crlf), false);

	const typed = type(crlf, 0, "x");
	const edited = type(createState(source({ text: "a\r\nb\r\n" })), 0, "x");
	const back = reconcile(edited, source({ text: "p\nq\n", modified: 300 }));
	const taken = back.state.update(takeDisk(back.state));

	assert.equal(textOf(typed), "xa\r\nb\r\nc\r\n");
	assert.equal(back.outcome, "conflict");
	assert.equal(textOf(taken.state), "p\nq\n");
	assert.equal(taken.state.doc.lines, 3);
	assert.equal(dirty(taken.state), false);
});
