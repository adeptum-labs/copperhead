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

/* Menus open on a click and follow the pointer while one is open, as a desktop menu bar does. The bar is drawn
   anew whenever a check mark in it changes, so the handlers live on the document. */
let openMenu = null;

const show = (menu) => {
	if (openMenu && openMenu !== menu) {
		openMenu.querySelector(".menu__list").hidden = true;
	}

	openMenu = menu;

	if (menu) {
		menu.querySelector(".menu__list").hidden = false;
	}
};

document.addEventListener("click", (event) => {
	const button = event.target.closest("[data-menu-button]");

	if (button) {
		const menu = button.closest("[data-menu]");

		show(openMenu === menu ? null : menu);
		return;
	}

	show(null);
});

document.addEventListener("mouseover", (event) => {
	const button = event.target.closest("[data-menu-button]");

	if (openMenu && button) {
		show(button.closest("[data-menu]"));
	}
});

document.addEventListener("keydown", (event) => {
	if (event.key === "Escape") {
		show(null);
	}
});
