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

const choice = (label, kind, chosen) => {
	const button = document.createElement("button");

	button.type = "button";
	button.className = `button ${kind}`;
	button.textContent = label;
	button.addEventListener("click", chosen);
	return button;
};

/* Closing with text that is not saved asks first, as switching projects does. The text goes to the server the way
   Save all sends it, and the window closes only once every file is written. */
const askBeforeClosing = (unsaved) => {
	const dialog = document.createElement("dialog");
	const card = document.createElement("section");
	const title = document.createElement("h2");
	const line = document.createElement("p");
	const buttons = document.createElement("div");
	const saveAndClose = async (event) => {
		const button = event.currentTarget;
		const refused = () => {
			line.textContent = "The files could not be saved, so the window stays open.";
			button.disabled = false;
		};

		button.disabled = true;

		try {
			const saved = await fetch("/action/file-save-all", { method: "POST", body: new URLSearchParams(unsaved) });

			if (saved.ok) {
				command("close");
			} else {
				refused();
			}
		} catch {
			refused();
		}
	};

	dialog.className = "closing";
	dialog.setAttribute("aria-labelledby", "closing-title");
	card.className = "modal__card";
	title.id = "closing-title";
	title.className = "modal__title";
	title.textContent = "Unsaved changes";
	line.className = "modal__line";
	line.textContent = `Changes to ${unsaved.files.split(",").join(", ")} are not saved.`;
	buttons.className = "modal__buttons";
	buttons.append(choice("Cancel", "", () => dialog.close()),
		choice("Close without saving", "button--danger", () => command("close")),
		choice("Save all and close", "button--accent button--strong", saveAndClose));
	card.append(title, line, buttons);
	dialog.append(card);
	dialog.addEventListener("close", () => dialog.remove());
	document.body.append(dialog);
	dialog.showModal();
};

document.addEventListener("click", (event) => {
	const button = event.target.closest("[data-window]");
	const unsaved = button?.dataset.window === "close" && window.copperhead.editor?.buffers({});

	if (unsaved?.files) {
		askBeforeClosing(unsaved);
	} else if (button) {
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
