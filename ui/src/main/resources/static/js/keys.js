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

/* Every shortcut is written on the control it presses, as data-key, so the menu that shows a shortcut and the
   key that works it cannot drift apart. */

/* A symbol that needs Shift on one layout and not on another, such as "/" (Shift+7 on a Swedish keyboard),
   is named by the symbol alone, so the same shortcut works on both. Letters and digits keep their Shift. */
const shifted = (key) => key.length === 1 && !/[\p{L}\p{N}]/u.test(key);

/* On a layout whose digit row types other characters until Shift is held, such as French AZERTY or Czech, Ctrl and
   the key marked 2 arrives as "é" or "ě". The key's place on the keyboard names the digit there, without Shift. */
const placedDigit = (event) => {
	const digit = /^Digit(\d)$/.exec(event.code ?? "")?.[1];

	return digit !== undefined && digit !== event.key ? digit : null;
};

/* Some events carry no key, such as an autofill that fires a bare keydown; they name no shortcut. */
const combo = (event) => {
	if (typeof event.key !== "string") {
		return "";
	}

	const digit = placedDigit(event);

	return [
		event.ctrlKey && "ctrl",
		event.altKey && "alt",
		event.shiftKey && digit === null && !shifted(event.key) && "shift",
		(digit ?? event.key).toLowerCase()
	].filter(Boolean).join("+");
};

/* Inside the editor the keys are the editor's, apart from the studio's own shortcuts: saving, building, the tools,
   the preferences and the function keys. */
const STUDIO_KEYS = /^(ctrl\+(shift\+)?[sb]|ctrl\+[1-5,]|(shift\+)?f\d{1,2})$/;

const editing = (event) => event.target.closest(".cm-editor") !== null;

/* An open dialog takes the keys, so nothing behind it acts, and Esc still reaches its close button. */
const keyed = () => {
	const modal = document.getElementById("modal");

	return modal && !modal.hidden ? modal : document;
};

document.addEventListener("keydown", (event) => {
	const name = combo(event);

	/* A key the editor acted on already, such as Ctrl+Z or Ctrl+F, is not acted on twice, and the question asked
	   before the window closes keeps its keys to itself. */
	if (!name || event.defaultPrevented || event.target.closest("input, textarea, dialog")
		|| editing(event) && !STUDIO_KEYS.test(name)) {
		return;
	}

	const control = keyed().querySelector(`[data-key="${CSS.escape(name)}"]:not([disabled])`);

	/* F5 would reload the page, and Ctrl+J and the others belong to the browser, unless they are taken here. */
	if (control) {
		event.preventDefault();
		control.click();
	}
});
