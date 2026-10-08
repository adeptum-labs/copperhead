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

/* The playhead runs here while the machine runs. The server keeps the same time, so each time the timeline is
   drawn anew the page takes up the position it carries and plays on from there. The anchor remembers the node it
   was read from, so a node swapped in is never painted with the time of the one it replaced. */
let anchor = { element: null, at: 0, since: performance.now(), running: false, length: 1 };

/* A PAL frame lasts 20 ms; a ProTracker module at speed 6 plays a row every 15 / BPM seconds, 64 to a pattern. */
const FRAME_MILLIS = 20;
const ROW_MILLIS_AT_ONE_BPM = 15000;
const PATTERN_ROWS = 64;

/* Each readout truncates exactly as the server writes the same moment, so a paused page shows the text it was sent. */
const clock = (millis) => {
	const centis = Math.floor(millis / 10);
	const seconds = String(Math.floor(centis / 100) % 60).padStart(2, "0");

	return `${Math.floor(centis / 6000)}:${seconds}.${String(centis % 100).padStart(2, "0")}`;
};

const frame = (millis) => String(Math.floor(millis / FRAME_MILLIS));

const hex = (value) => value.toString(16).toUpperCase().padStart(2, "0");

const pattern = (millis) => {
	const rows = Math.floor(millis * anchor.bpm / ROW_MILLIS_AT_ONE_BPM);

	return `pos ${hex(Math.floor(rows / PATTERN_ROWS))} row ${hex(rows % PATTERN_ROWS)}`;
};

/* How far the run got since the anchor, before it wraps round at the end. */
const travelled = () => anchor.at + performance.now() - anchor.since;

const position = () => anchor.running ? travelled() % anchor.length : anchor.at;

const sync = (timeline) => {
	anchor = {
		element: timeline,
		at: Number(timeline.dataset.position),
		since: performance.now(),
		running: timeline.dataset.running === "true",
		length: Number(timeline.dataset.length),
		next: Number(timeline.dataset.next),
		bpm: Number(timeline.dataset.bpm),
		asked: false
	};
};

const write = (selector, text) => {
	for (const readout of document.querySelectorAll(selector)) {
		if (readout.textContent !== text) {
			readout.textContent = text;
		}
	}
};

/* The playing part names itself in the status bar, on the emulator's screen and on the timeline, and only the
   server knows the parts, so once the playhead runs past the part's end the run is drawn anew. That is asked once
   for each timeline drawn: the answer brings a new one, with the next end to wait for. */
const crossed = () => anchor.running && !anchor.asked && travelled() >= anchor.next;

const paint = () => {
	const timeline = document.getElementById("timeline");

	if (timeline) {
		if (timeline !== anchor.element) {
			sync(timeline);
		}

		const at = position();

		timeline.style.setProperty("--at", String(at / anchor.length));
		write("[data-clock]", clock(at));
		write("[data-frame]", frame(at));
		write("[data-pattern]", pattern(at));

		if (crossed()) {
			anchor.asked = true;
			timeline.querySelector("[data-boundary]").click();
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

requestAnimationFrame(paint);
