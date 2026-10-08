#!/bin/sh
# Adds this unpacked Copperhead to the application menu of whoever runs it, or takes it out again with
# --uninstall. The packages do this for themselves; this is for a copy unpacked from the tar.gz.
set -eu

here=$(cd "$(dirname "$0")" && pwd)
applications=${XDG_DATA_HOME:-$HOME/.local/share}/applications
entry=$applications/copperhead.desktop

refresh() {
	if command -v update-desktop-database > /dev/null; then
		update-desktop-database "$applications" || true
	fi
}

# What goes into the right hand side of a sed substitution that uses | as its delimiter.
escaped() {
	printf '%s' "$1" | sed -e 's/[\\&|]/\\&/g'
}

case "${1:-}" in
	--uninstall)
		rm -f "$entry"
		refresh
		echo "Copperhead is no longer in the application menu"
		;;
	"")
		mkdir -p "$applications"
		# The launcher is quoted, as the path it is unpacked to may hold spaces.
		sed -e "s|APPLICATION_NAME|Copperhead|" \
			-e "s|APPLICATION_DESCRIPTION|Copperhead: an Amiga demo and game studio for Linux.|" \
			-e "s|APPLICATION_LAUNCHER|\"$(escaped "$here")/bin/Copperhead\"|" \
			-e "s|APPLICATION_ICON|$(escaped "$here")/lib/Copperhead.png|" \
			-e '/^DESKTOP_MIMES$/d' "$here/lib/Copperhead.desktop.in" > "$entry"
		refresh
		echo "Copperhead is now in the application menu; run '$0 --uninstall' to take it out"
		;;
	*)
		echo "usage: $0 [--uninstall]" >&2
		exit 1
		;;
esac
