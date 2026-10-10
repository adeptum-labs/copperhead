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

/* The editor of the Code tool. Bundled into ui/src/main/resources/static/js/editor.bundle.js, where it defines the
   global CopperheadEditor that static/js/editor.js puts on the page. */

export { caretLine, commands, createState, dirty, loaded, mount, reconcile, reveal, setDisk, textOf } from "./kit.js";
