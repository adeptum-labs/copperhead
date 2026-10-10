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

/* Chromium owns the window contents, so the only way to reach the frame around it
   is to ask the host that opened it. */
const command = (name) => fetch("/window/" + name, { method: "POST" });

document.addEventListener("click", (event) => {
	const button = event.target.closest("[data-window]");

	if (button) {
		command(button.dataset.window);
	}
});

/* Capturing keeps the release with the element even when the pointer has run ahead of
   the window, which it always does during a fast drag. */
const hold = (element, cursor, start, end) => {
	let held = false;

	element.addEventListener("pointerdown", (event) => {
		if (event.button !== 0 || event.target.closest("button, .menu__list")) {
			return;
		}

		held = true;
		element.setPointerCapture(event.pointerId);
		document.body.classList.add("is-holding");
		document.body.style.cursor = cursor;
		command(start);
	});

	/* A click on a button inside is released here as well, and must not end a hold it never began. */
	const release = () => {
		if (!held) {
			return;
		}

		held = false;
		document.body.classList.remove("is-holding");
		document.body.style.cursor = "";
		command(end);
	};

	element.addEventListener("pointerup", release);
	element.addEventListener("pointercancel", release);
};

const bar = document.querySelector("[data-drag]");

if (bar) {
	hold(bar, "move", "move/start", "move/end");
	bar.addEventListener("dblclick", () => command("maximise"));
}

for (const handle of document.querySelectorAll("[data-resize]")) {
	hold(handle, getComputedStyle(handle).cursor, "resize/start/" + handle.dataset.resize, "resize/end");
}

/* The server keeps the theme with the other preferences and names it on the status bar it sends with every answer,
   so a switch from the title bar, the View menu or the preferences reaches the page the same way. */
document.addEventListener("htmx:oobAfterSwap", (event) => {
	if (event.target.id === "status") {
		document.documentElement.dataset.theme = event.target.dataset.theme;
	}
});
