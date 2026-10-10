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

/* Writes the provenance of the editor bundle to standard output: its checksum, the exact packages esbuild put in
   it with their registry hashes, and the licence of each, which the MIT licence asks to travel with the code.
   Usage: node provenance.mjs <bundle> <esbuild metafile> */

import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { basename } from "node:path";

const [bundle, metafile] = process.argv.slice(2);
const lock = JSON.parse(readFileSync("package-lock.json", "utf8")).packages;
const code = readFileSync(bundle);

/* An input such as node_modules/@codemirror/view/dist/index.js belongs to the package that folder holds. */
const packageOf = (input) => /^node_modules\/((?:@[^/]+\/)?[^/]+)\//.exec(input)?.[1];

/* esbuild reads every module it is pointed at, but only those that left code in the output are in the bundle. */
const output = Object.values(JSON.parse(readFileSync(metafile, "utf8")).outputs)[0];
const bundled = [...new Set(Object.entries(output.inputs).filter(([, input]) => input.bytesInOutput > 0)
	.map(([input]) => packageOf(input)).filter(Boolean))].sort();

const licences = new Map();

for (const name of bundled) {
	const text = readFileSync(`node_modules/${name}/LICENSE`, "utf8").trim();

	licences.set(text, [...licences.get(text) ?? [], name]);
}

const width = Math.max(...bundled.map((name) => name.length));
const lines = [
	`${basename(bundle)} · CodeMirror 6 with the 68k Motorola mode of ui/frontend`,
	"",
	"Built rather than fetched at runtime, so that what ships is what was reviewed. ui/frontend/build.sh",
	"rebuilds it from exactly the versions below, which package-lock.json pins.",
	"",
	`  sha256     ${createHash("sha256").update(code).digest("hex")}`,
	`  bytes      ${code.length}`,
	`  esbuild    ${lock["node_modules/esbuild"].version}`,
	"",
	"Packages in the bundle:",
	...bundled.map((name) => {
		const entry = lock[`node_modules/${name}`];

		return `  ${name.padEnd(width)}  ${entry.version.padEnd(8)}  ${entry.license}  ${entry.integrity}`;
	}),
	"",
	"Verify a rebuild before committing it:",
	`  sha256sum ${basename(bundle)}`,
	"",
	"Licences of the packages in the bundle:",
	...[...licences].flatMap(([text, names]) => ["", `--- ${names.join(", ")} ---`, "", text])
];

process.stdout.write(`${lines.join("\n")}\n`);
