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

/* The panes keep the sizes they were dragged to from one start to the next: once a drag ends the server keeps them
   with the other preferences, and draws the next page with them. */
const SPLIT = { low: 36, high: 78 };
const BOTTOM = { low: 120, high: 460, share: 0.42 };

const clamp = (value, low, high) => Math.min(high, Math.max(low, value));

/* The capture is lost when the button is released and when the drag is cancelled, so the split lets go of the
   pointer and remembers where it ended in both cases. */
const drag = (handle, move, done) => {
	handle.addEventListener("pointerdown", (event) => {
		handle.setPointerCapture(event.pointerId);
		handle.addEventListener("pointermove", move);
		handle.addEventListener("lostpointercapture", () => {
			handle.removeEventListener("pointermove", move);
			done();
		}, { once: true });
	});
};

const workspace = document.querySelector("[data-workspace]");

if (workspace) {
	const top = workspace.querySelector(".workspace__top");
	const sizes = {
		split: parseFloat(workspace.style.getPropertyValue("--split")),
		bottom: parseFloat(workspace.style.getPropertyValue("--bottom"))
	};

	const remember = () => fetch("/action/layout", {
		method: "POST",
		body: new URLSearchParams({ split: Math.round(sizes.split), bottom: Math.round(sizes.bottom) })
	});

	drag(workspace.querySelector("[data-splitter=vertical]"), (event) => {
		const box = top.getBoundingClientRect();

		sizes.split = clamp((event.clientX - box.left) / box.width * 100, SPLIT.low, SPLIT.high);
		workspace.style.setProperty("--split", `${sizes.split}%`);
	}, remember);

	/* The timeline never takes more than its share of the window, however tall it was dragged. */
	drag(workspace.querySelector("[data-splitter=horizontal]"), (event) => {
		const tallest = Math.min(BOTTOM.high, workspace.clientHeight * BOTTOM.share);

		sizes.bottom = clamp(workspace.getBoundingClientRect().bottom - event.clientY, BOTTOM.low, tallest);
		workspace.style.setProperty("--bottom", `${sizes.bottom}px`);
	}, remember);
}
