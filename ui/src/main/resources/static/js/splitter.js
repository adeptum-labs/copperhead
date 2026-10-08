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

/* The panes keep the sizes they were dragged to from one start to the next. Storage can be refused, which only
   costs that memory. */
const SPLIT = { key: "copperhead.split", low: 36, high: 78 };
const BOTTOM = { key: "copperhead.bottom", low: 120, high: 460, share: 0.42 };

const remember = (key, value) => {
	try {
		localStorage.setItem(key, String(value));
	} catch {
		/* kept for this run only */
	}
};

const recall = (key) => {
	try {
		return Number(localStorage.getItem(key));
	} catch {
		return 0;
	}
};

const clamp = (value, low, high) => Math.min(high, Math.max(low, value));

/* The capture is lost when the button is released and when the drag is cancelled, so the split lets go of the
   pointer in both cases. */
const drag = (handle, move) => {
	handle.addEventListener("pointerdown", (event) => {
		handle.setPointerCapture(event.pointerId);
		handle.addEventListener("pointermove", move);
		handle.addEventListener("lostpointercapture", () => handle.removeEventListener("pointermove", move),
			{ once: true });
	});
};

const workspace = document.querySelector("[data-workspace]");

if (workspace) {
	const top = workspace.querySelector(".workspace__top");

	const split = (percent) => {
		const value = clamp(percent, SPLIT.low, SPLIT.high);

		workspace.style.setProperty("--split", `${value}%`);
		return value;
	};

	/* The timeline never takes more than its share of the window, however tall it was dragged. */
	const bottom = (pixels) => {
		const value = clamp(pixels, BOTTOM.low, Math.min(BOTTOM.high, workspace.clientHeight * BOTTOM.share));

		workspace.style.setProperty("--bottom", `${value}px`);
		return value;
	};

	if (recall(SPLIT.key)) {
		split(recall(SPLIT.key));
	}

	if (recall(BOTTOM.key)) {
		bottom(recall(BOTTOM.key));
	}

	drag(workspace.querySelector("[data-splitter=vertical]"), (event) => {
		const box = top.getBoundingClientRect();

		remember(SPLIT.key, split((event.clientX - box.left) / box.width * 100));
	});

	drag(workspace.querySelector("[data-splitter=horizontal]"), (event) => {
		remember(BOTTOM.key, bottom(workspace.getBoundingClientRect().bottom - event.clientY));
	});
}
