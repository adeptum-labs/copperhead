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

/* The playhead runs here while the machine runs. The server keeps the same time, so each time the timeline is
   drawn anew the page takes up the position it carries and plays on from there. */
let anchor = { at: 0, since: performance.now(), running: false, length: 1 };

const clock = (millis) => {
	const seconds = millis / 1000;

	return `${Math.floor(seconds / 60)}:${(seconds % 60).toFixed(2).padStart(5, "0")}`;
};

const position = () => anchor.running
	? (anchor.at + performance.now() - anchor.since) % anchor.length
	: anchor.at;

const sync = () => {
	const timeline = document.getElementById("timeline");

	if (timeline) {
		anchor = {
			at: Number(timeline.dataset.position),
			since: performance.now(),
			running: timeline.dataset.running === "true",
			length: Number(timeline.dataset.length)
		};
	}
};

const paint = () => {
	const timeline = document.getElementById("timeline");

	if (timeline) {
		const at = position();
		const shown = timeline.querySelector("[data-clock]");

		timeline.style.setProperty("--at", String(at / anchor.length));

		if (shown) {
			shown.textContent = clock(at);
		}
	}

	requestAnimationFrame(paint);
};

/* The ruler is asked where on it the click fell, as a moment of the run. */
window.copperhead = {
	seekAt: (event) => {
		const ruler = event.target.closest(".timeline__ruler").getBoundingClientRect();

		return Math.round(Math.min(1, Math.max(0, (event.clientX - ruler.left) / ruler.width)) * anchor.length);
	}
};

document.addEventListener("htmx:load", (event) => {
	if (event.detail.elt.id === "timeline") {
		sync();
	}
});

sync();
requestAnimationFrame(paint);
