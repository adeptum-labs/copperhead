/*
    Copperhead
    Copyright © 2026 Adam Waldenberg

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
const shifted = (key) => key.length === 1 && !/[a-z0-9]/i.test(key);

const combo = (event) => [
	event.ctrlKey && "ctrl",
	event.altKey && "alt",
	event.shiftKey && !shifted(event.key) && "shift",
	event.key.toLowerCase()
].filter(Boolean).join("+");

document.addEventListener("keydown", (event) => {
	if (event.target.closest("input, textarea")) {
		return;
	}

	const control = document.querySelector(`[data-key="${CSS.escape(combo(event))}"]:not([disabled])`);

	/* F5 would reload the page, and Ctrl+J and the others belong to the browser, unless they are taken here. */
	if (control) {
		event.preventDefault();
		control.click();
	}
});
