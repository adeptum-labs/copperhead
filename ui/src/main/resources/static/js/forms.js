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

/* A dialog with text boxes and choices is no form, so the browser sends nothing of its own accord: the fields live in
   a container marked data-form, and the button that submits them names their values as it is pressed. A box left
   unticked and a choice not taken are left out, as a form leaves them out. */
const chosen = (field) => !["checkbox", "radio"].includes(field.type) || field.checked;

Object.assign(window.copperhead, {
	fields: (button) => Object.fromEntries(Array.from(button.closest("[data-form]").querySelectorAll("[data-field]"))
		.filter(chosen).map((field) => [field.name, field.value]))
});
