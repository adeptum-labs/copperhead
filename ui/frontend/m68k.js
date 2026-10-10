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

import { HighlightStyle, StreamLanguage, syntaxHighlighting } from "@codemirror/language";
import { tags } from "@lezer/highlight";

/* Motorola syntax as vasm reads it: a word in the first column is a label, the next word the instruction or
   directive, the word after that its operands, and whatever follows another blank is a comment. */
const LABEL_FIELD = 0;
const INSTRUCTION_FIELD = 1;
const OPERAND_FIELD = 2;
const COMMENT_FIELD = 3;

const CONDITIONS = "t f hi ls cc hs cs lo ne eq vc vs pl mi ge lt gt le".split(" ");

const INSTRUCTIONS = new Set([
	...`abcd add adda addi addq addx and andi asl asr bchg bclr bfchg bfclr bfexts bfextu bfffo bfins bfset bftst
		bkpt bra bset bsr btst callm cas cas2 chk chk2 clr cmp cmp2 cmpa cmpi cmpm dbra divs divsl divu divul eor eori
		exg ext extb illegal jmp jsr lea link lsl lsr move movea movec movem movep moveq moves muls mulu nbcd neg negx
		nop not or ori pack pea reset rol ror roxl roxr rtd rte rtm rtr rts sbcd stop sub suba subi subq subx swap tas
		trap trapv tst unlk unpk`.split(/\s+/),
	...CONDITIONS.flatMap((condition) => [`b${condition}`, `db${condition}`, `s${condition}`, `trap${condition}`])
]);

const DIRECTIVES = new Set(`align bss bss_c bss_f cnop code code_c code_f data data_c data_f dc dcb ds else elseif end
	endc endif endm endr equ equr even fail idnt if ifb ifc ifd ifeq ifge ifgt ifle iflt ifnb ifnc ifnd ifne incbin incdir
	include list macro mexit nolist odd offset opt org output printt printv public rept rorg rs rsreset rsset section
	set so text ttl xdef xref`.split(/\s+/));

const SIZES = new Set(["b", "w", "l", "s"]);

const LABEL = /^(?:[A-Za-z_.][\w.$]*|\d+\$):{0,2}/;
const INDENTED_LABEL = /^(?:[A-Za-z_.][\w.$]*|\d+\$)::?/;
const WORD = /^[A-Za-z_]\w*(?:\.\w+)?/;

/* Tried in order: a local label such as 1$ before the number it starts with, a register before the symbol. */
const OPERANDS = [
	[/^"[^"]*"?|^'[^']*'?/, "string"],
	[/^\\(?:[\d@]|<[^>]*>)/, "variableName.special"],
	[/^\d+\$/, "labelName"],
	[/^(?:\$[\da-f]+|%[01]+|@[0-7]+|0x[\da-f]+|\d+)/i, "number"],
	[/^(?:[da][0-7]|sp|pc|sr|ccr|usp)(?:\.[wl])?(?![\w$.])/i, "variableName.standard"],
	[/^\.?[A-Za-z_][\w.$]*/, "variableName"],
	[/^[#,()+\-*/%&|^~<>=!:[\]{}]/, "operator"]
];

/* A size other than .b, .w, .l or .s, or a word that is neither an instruction nor a directive, is left plain: a
   macro call, or a typing error the colour then gives away. */
const instructionType = (word) => {
	const [name, size] = word.toLowerCase().split(".");

	if (size !== undefined && !SIZES.has(size)) {
		return "macroName";
	}

	if (INSTRUCTIONS.has(name)) {
		return "keyword";
	}

	return DIRECTIVES.has(name) ? "meta" : "macroName";
};

const label = (stream, state) => {
	state.field = INSTRUCTION_FIELD;

	if (stream.match(LABEL)) {
		return "labelName";
	}

	stream.next();
	return null;
};

/* A label further in than the first column needs its colon, and the instruction may follow it on the line. */
const instruction = (stream, state) => {
	if (stream.match(INDENTED_LABEL)) {
		return "labelName";
	}

	state.field = OPERAND_FIELD;

	if (stream.eat("=")) {
		return "meta";
	}

	if (stream.match(WORD)) {
		return instructionType(stream.current());
	}

	stream.next();
	return null;
};

const operand = (stream, state) => {
	state.operands = true;

	for (const [pattern, type] of OPERANDS) {
		if (stream.match(pattern)) {
			return type;
		}
	}

	stream.next();
	return null;
};

const blank = (state) => {
	if (state.field === LABEL_FIELD) {
		state.field = INSTRUCTION_FIELD;
	} else if (state.field === OPERAND_FIELD && state.operands) {
		state.field = COMMENT_FIELD;
	}
};

/* A star where an instruction could stand starts a comment; among the operands it is the location counter. */
const commentStarts = (stream, state) => stream.peek() === ";" || state.field === COMMENT_FIELD
	|| stream.peek() === "*" && state.field !== OPERAND_FIELD;

/* Every call takes at least one character, or the editor would wait on that line for ever. */
export const m68kParser = {
	name: "m68k",
	startState: () => ({ field: LABEL_FIELD, operands: false }),
	copyState: (state) => ({ ...state }),
	languageData: { commentTokens: { line: ";" } },
	token: (stream, state) => {
		if (stream.sol()) {
			state.field = LABEL_FIELD;
			state.operands = false;
		}

		if (stream.eatSpace()) {
			blank(state);
			return null;
		}

		if (commentStarts(stream, state)) {
			stream.skipToEnd();
			return "comment";
		}

		if (state.field === LABEL_FIELD) {
			return label(stream, state);
		}

		return state.field === INSTRUCTION_FIELD ? instruction(stream, state) : operand(stream, state);
	}
};

export const m68k = StreamLanguage.define(m68kParser);

/* The colours are the page's own, so the text follows the theme the studio is switched to. */
export const m68kHighlight = HighlightStyle.define([
	{ tag: tags.labelName, color: "var(--lbl)" },
	{ tag: [tags.keyword, tags.meta], color: "var(--kw)" },
	{ tag: [tags.number, tags.string], color: "var(--num)" },
	{ tag: tags.comment, color: "var(--cm)" }
]);

export const m68kSupport = [m68k, syntaxHighlighting(m68kHighlight)];
