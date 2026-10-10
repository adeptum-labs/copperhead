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
import { StringStream } from "@codemirror/language";
import { m68k, m68kParser } from "./m68k.js";

const TAB_SIZE = 8;

/* The tokens of one line the way the editor asks for them, each with its type, the blanks between left out. */
const tokens = (line) => {
	const stream = new StringStream(line, TAB_SIZE, TAB_SIZE);
	const state = m68kParser.startState();
	const found = [];

	while (!stream.eol()) {
		stream.start = stream.pos;

		const type = m68kParser.token(stream, state);

		assert.ok(stream.pos > stream.start, `no progress at column ${stream.start} of ${JSON.stringify(line)}`);

		if (type !== null) {
			found.push([stream.current(), type]);
		}
	}

	return found;
};

const types = (line) => tokens(line).map(([, type]) => type);

test("a word in the first column is a label, with or without its colon", () => {
	for (const word of ["start", "start:", ".loop", "exported::", "1$", "copper_list"]) {
		assert.deepEqual(tokens(word), [[word, "labelName"]], word);
	}
});

test("a label further in needs its colon, and the instruction may follow it", () => {
	assert.deepEqual(tokens("\tloop:\tdbra d0,loop"), [
		["loop:", "labelName"], ["dbra", "keyword"], ["d0", "variableName.standard"], [",", "operator"],
		["loop", "variableName"]
	]);
});

test("a mnemonic keeps its size and ignores case", () => {
	for (const word of ["rts", "bra.s", "MOVE.W", "move.l", "dbra", "bne.b", "moveq", "lea", "tst.l", "Bsr.W"]) {
		assert.deepEqual(tokens(`\t${word}`), [[word, "keyword"]], word);
	}
});

test("a size the 68000 does not have, or an unknown word, is left plain as a macro call", () => {
	assert.deepEqual(types("\tmove.x d0,d1")[0], "macroName");
	assert.deepEqual(tokens("\tbogus d0")[0], ["bogus", "macroName"]);
	assert.deepEqual(tokens("\tWAITBLIT"), [["WAITBLIT", "macroName"]]);
});

test("registers are told from symbols that begin with the same letters", () => {
	for (const register of ["d0", "d7", "a0", "a7", "sp", "pc", "sr", "ccr", "usp", "SP", "A3"]) {
		assert.deepEqual(tokens(`\tmove.l ${register}`)[1], [register, "variableName.standard"], register);
	}

	for (const symbol of ["d8", "a8", "spritedata", "pcx", "d0x", "srcptr"]) {
		assert.deepEqual(tokens(`\tmove.l ${symbol}`)[1], [symbol, "variableName"], symbol);
	}
});

test("an index register carries its size", () => {
	assert.deepEqual(tokens("\tmove.w (a0,d0.w),d1"), [
		["move.w", "keyword"], ["(", "operator"], ["a0", "variableName.standard"], [",", "operator"],
		["d0.w", "variableName.standard"], [")", "operator"], [",", "operator"], ["d1", "variableName.standard"]
	]);
});

test("numbers are hexadecimal, binary, octal or decimal", () => {
	assert.deepEqual(tokens("\tdc.w $1234,%1010,@17,42,0x1F"), [
		["dc.w", "meta"], ["$1234", "number"], [",", "operator"], ["%1010", "number"], [",", "operator"],
		["@17", "number"], [",", "operator"], ["42", "number"], [",", "operator"], ["0x1F", "number"]
	]);
	assert.deepEqual(tokens("\tmove.l #$20000,d0").slice(1, 3), [["#", "operator"], ["$20000", "number"]]);
});

test("strings end at their closing quote or at the end of the line", () => {
	assert.deepEqual(tokens("\tinclude \"hardware/custom.i\""), [
		["include", "meta"], ["\"hardware/custom.i\"", "string"]
	]);
	assert.deepEqual(tokens("\tdc.b 'a;b',0")[1], ["'a;b'", "string"]);
	assert.deepEqual(tokens("\tdc.b \"open"), [["dc.b", "meta"], ["\"open", "string"]]);
});

test("a semicolon starts a comment wherever it stands", () => {
	assert.deepEqual(tokens("; the whole line"), [["; the whole line", "comment"]]);
	assert.deepEqual(tokens("\t; indented"), [["; indented", "comment"]]);
	assert.deepEqual(tokens("\trts ; done"), [["rts", "keyword"], ["; done", "comment"]]);
	assert.deepEqual(tokens("start: move.l d0,d1;copy").at(-1), [";copy", "comment"]);
});

test("a star is a comment where an instruction could stand, and the location counter among the operands", () => {
	assert.deepEqual(tokens("* banner"), [["* banner", "comment"]]);
	assert.deepEqual(tokens("  * indented banner"), [["* indented banner", "comment"]]);
	assert.deepEqual(tokens("\tdc.w *-start"), [
		["dc.w", "meta"], ["*", "operator"], ["-", "operator"], ["start", "variableName"]
	]);
});

test("whatever follows a blank after the operands is a comment", () => {
	assert.deepEqual(tokens("\tmove.l d0,d1 copies the count"), [
		["move.l", "keyword"], ["d0", "variableName.standard"], [",", "operator"], ["d1", "variableName.standard"],
		["copies the count", "comment"]
	]);
	assert.deepEqual(tokens("\tdc.b \"a b\",0 text").at(-1), ["text", "comment"]);
});

test("directives are told from instructions", () => {
	const directives = ["org $20000", "dc.w 1", "ds.b 16", "dcb.l 4,0", "section code,code_c", "even",
		"include \"a.i\"", "incbin \"a.raw\"", "macro", "endm", "rept 4", "endr", "if 1", "ifd X", "ifeq X", "else",
		"endc", "xdef main", "xref other", "opt o+", "cnop 0,4", "equ 3", "set 4", "code_c", "data_f", "end"];

	for (const line of directives) {
		assert.equal(types(`\t${line}`)[0], "meta", line);
	}

	assert.deepEqual(tokens("WAITBLIT macro"), [["WAITBLIT", "labelName"], ["macro", "meta"]]);
});

test("a symbol is defined with equ, set or an equals sign", () => {
	assert.deepEqual(tokens("LOAD equ $20000"), [["LOAD", "labelName"], ["equ", "meta"], ["$20000", "number"]]);
	assert.deepEqual(tokens("count = 4"), [["count", "labelName"], ["=", "meta"], ["4", "number"]]);
	assert.deepEqual(tokens("count=4"), [["count", "labelName"], ["=", "meta"], ["4", "number"]]);
});

test("a register list keeps its dashes and slashes apart from the registers", () => {
	assert.deepEqual(tokens("\tmovem.l d0-d7/a0-a6,-(sp)").map(([text]) => text), [
		"movem.l", "d0", "-", "d7", "/", "a0", "-", "a6", ",", "-", "(", "sp", ")"
	]);
});

test("macro arguments and local labels among the operands", () => {
	assert.deepEqual(tokens("\tmove.w \\1,\\@"), [
		["move.w", "keyword"], ["\\1", "variableName.special"], [",", "operator"], ["\\@", "variableName.special"]
	]);
	assert.deepEqual(tokens("\tdbra d0,.loop").at(-1), [".loop", "variableName"]);
	assert.deepEqual(tokens("\tbne 1$").at(-1), ["1$", "labelName"]);
});

test("every call takes at least one character, whatever the line holds", () => {
	const alphabet = " \t;*:.,$%@#\"'()-+/\\=!<>_0189abdsxzMOVE.lwå";
	let seed = 7;
	const next = () => {
		seed = (seed * 1103515245 + 12345) % 2147483648;
		return seed;
	};

	for (let line = 0; line < 500; line++) {
		const text = Array.from({ length: next() % 40 }, () => alphabet[next() % alphabet.length]).join("");

		assert.doesNotThrow(() => tokens(text), JSON.stringify(text));
	}
});

test("a line comment is a semicolon, for the editor's comment command", () => {
	assert.equal(m68k.name, "m68k");
	assert.deepEqual(m68kParser.languageData.commentTokens, { line: ";" });
});
