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

/* A stand-in for the emulator's picture: copper bars rolling down a PAL screen while the machine runs. The
   pane is drawn anew on every change to the machine, so the canvas is looked up on each frame; a still picture is
   drawn once on each canvas that arrives. */
const BARS = 6;
const HALF_BAR = 8;
const reduced = matchMedia("(prefers-reduced-motion: reduce)");
let shown = 0;
let last = performance.now();
let drawn = null;

const draw = (canvas, time) => {
	const context = canvas.getContext("2d");
	const { width, height } = canvas;

	context.fillStyle = "#000";
	context.fillRect(0, 0, width, height);

	for (let bar = 0; bar < BARS; bar++) {
		const centre = height / 2 + Math.sin(time / 700 + bar * 0.6) * height * 0.32;

		for (let line = -HALF_BAR; line <= HALF_BAR; line++) {
			context.fillStyle = `hsl(${20 + bar * 50} 85% ${70 - Math.abs(line) * 6}%)`;
			context.fillRect(0, Math.round(centre + line), width, 1);
		}
	}

	context.fillStyle = "rgba(0, 0, 0, .25)";

	for (let y = 0; y < height; y += 2) {
		context.fillRect(0, y, width, 1);
	}
};

const tick = (now) => {
	const canvas = document.querySelector("[data-emulator]");
	const moving = canvas !== null && canvas.dataset.running === "true" && !reduced.matches;

	if (moving) {
		shown += now - last;
	}

	last = now;

	if (canvas !== null && (moving || canvas !== drawn)) {
		draw(canvas, shown);
		drawn = canvas;
	}

	requestAnimationFrame(tick);
};

requestAnimationFrame(tick);
