#!/usr/bin/env bash
# Installs a Copperhead package the way a person would, in the fresh container a CI job runs this in, and lets the
# installed Copperhead do what it is for: open its window, serve its page only to the browser it opened, draw the
# welcome pane with the engine it bundles, and quit when its window is closed. On a Wayland desktop it has to draw
# the page inside its own window. It runs as root and changes the container for good.
# Usage: installed-check.sh <copperhead_*.deb | copperhead-*.rpm | Copperhead-portable.tar.gz> [x11|wayland]
set -euo pipefail

# It installs and removes packages and adds a user, which is only fine in a container thrown away afterwards. CI=true
# alone proves nothing, as developers export it too, so a container has to be seen as well, unless the person
# running it vouches for the machine.
in_container() {
	[ -f /.dockerenv ] || [ -f /run/.containerenv ] || [ -n "${container:-}" ]
}

if [ "${COPPERHEAD_INSTALLED_CHECK:-}" != 1 ] && { [ "${CI:-}" != true ] || ! in_container; }; then
	echo "Refusing to run: it installs and removes packages and adds a user, which only a CI container may have." >&2
	echo "In a throwaway container, set COPPERHEAD_INSTALLED_CHECK=1 to run it anyway." >&2
	exit 2
fi

if [ "$(id -u)" != 0 ]; then
	echo "Refusing to run: it has to be root, as it is in a CI container." >&2
	exit 2
fi

package=$(realpath "${1:?usage: installed-check.sh <package|portable-tar> [x11|wayland]}")
desktop=${2:-x11}
here=$(dirname "$(realpath "$0")")

user=copperhead
log=/tmp/copperhead.log
title=Copperhead
class=org-copperhead-shell-Copperhead
first_page="new project"
packaged=/opt/copperhead
portable="/opt/with space/Copperhead"
broken_engine='UnsatisfiedLinkError|did not initialize|CefInitializationException'
wait_seconds=120
poll_seconds=2

# shellcheck source=/dev/null
. /etc/os-release

fail() {
	echo "FAILED: $*" >&2
	echo "--- Copperhead log ---" >&2
	cat "$log" >&2 2>/dev/null || true
	exit 1
}

# Whatever was started, however far the checks got, goes with the script. Everything it starts runs as the user it
# created, Weston's Xwayland and the engine's helpers included, so that user's processes are all it ever stops.
stop_what_was_started() {
	pkill -u "$user" 2>/dev/null || true
}

# A semicolon, not &&: a command line that ends in & would otherwise put the cd in the background with it.
start_as_user() {
	runuser -u "$user" -- bash -ec "cd ~; $1"
}

as_user() {
	runuser -u "$user" -- timeout 60 bash -ec "cd ~; $1"
}

# Runs the script as the user until it succeeds, the way a person would look again a moment later.
await() {
	local deadline=$((SECONDS + wait_seconds))

	until as_user "$2" >/tmp/await.out 2>&1; do
		if ((SECONDS >= deadline)); then
			cat /tmp/await.out >&2
			fail "timed out waiting for $1"
		fi

		sleep "$poll_seconds"
	done
}

detect_family() {
	case " $ID ${ID_LIKE:-} " in
		*" debian "* | *" ubuntu "*) echo debian ;;
		*" fedora "* | *" rhel "*) echo fedora ;;
		*) fail "no setup for $ID" ;;
	esac
}

# Only what the checks need. The libraries Copperhead runs on have to come in through the package, which is what a
# person installing it depends on.
install_tools() {
	if [ "$family" = debian ]; then
		export DEBIAN_FRONTEND=noninteractive
		apt-get update -qq
		apt-get install -y -qq xvfb xauth procps curl xdotool imagemagick tesseract-ocr python3-xlib >/dev/null
	else
		dnf install -y -q xorg-x11-server-Xvfb xorg-x11-xauth procps-ng curl which xdotool ImageMagick tesseract \
			tesseract-langpack-eng python3-xlib util-linux shadow-utils tar gzip >/dev/null
	fi
}

# GNOME on Wayland is the default session of Ubuntu, Fedora and the distributions built on them. GTK 4 comes with
# every GNOME desktop, and the browser engine loads it there when it is left to run on Wayland.
install_wayland() {
	if [ "$family" = debian ]; then
		apt-get install -y -qq weston xwayland libgtk-4-1 >/dev/null
	else
		dnf install -y -q weston xorg-x11-server-Xwayland gtk4 >/dev/null
	fi

	mkdir -p -m 1777 /tmp/.X11-unix
}

# A portable folder brings no dependencies of its own, so they are installed the way its reader would.
install_libraries() {
	apt-get install -y -qq libgtk-3-0 libnss3 libgbm1 libasound2 libxss1 libxtst6 libxi6 libcups2 \
		libatk-bridge2.0-0 libxkbcommon0 >/dev/null
}

install_copperhead() {
	case "$package" in
		*.deb) apt-get install -y -qq "$package" >/dev/null ;;
		*.rpm) dnf install -y -q "$package" >/dev/null ;;
		*.tar.gz)
			install_libraries
			mkdir -p "$(dirname "$portable")"
			tar --no-same-owner -xzf "$package" -C "$(dirname "$portable")"
			;;
		*) fail "not a package: $package" ;;
	esac
}

reinstall_copperhead() {
	case "$package" in
		*.deb) apt-get install -y -qq --reinstall "$package" >/dev/null ;;
		*.rpm) dnf reinstall -y -q "$package" >/dev/null ;;
	esac

	test -x "$home/bin/Copperhead" || fail "no launcher after installing the same package again"
}

# A container has no desktop menu to register the entry with, which is why the install does not insist on it, so it
# is the entry the package ships that is looked at: filed under Development, opening the launcher and naming the
# class of the window, so that a dock can tell which window belongs to it.
check_menu_entry() {
	local entry=$packaged/lib/copperhead-Copperhead.desktop

	grep -q '^Categories=Development;IDE;$' "$entry" || fail "menu entry not under Development"
	grep -q "^Exec=$packaged/bin/Copperhead\$" "$entry" || fail "menu entry does not open the launcher"
	grep -q "^Icon=$packaged/lib/Copperhead.png\$" "$entry" || fail "menu entry names no icon"
	grep -q "^StartupWMClass=$class\$" "$entry" || fail "menu entry names no window class"
	test -f "$packaged/lib/Copperhead.png" || fail "no icon"
}

start_on_x11() {
	start_as_user "setsid Xvfb :99 -screen 0 1600x1000x24 >/dev/null 2>&1 &
		sleep 2
		DISPLAY=:99 setsid nohup '$home/bin/Copperhead' >$log 2>&1 &"
}

# The window itself is an X11 one even on Wayland, drawn through Xwayland, so the checks look for it there.
start_on_wayland() {
	local current_desktop=GNOME

	if [ "$ID" = ubuntu ]; then
		current_desktop=ubuntu:GNOME
	fi

	start_as_user "export XDG_RUNTIME_DIR=/tmp/runtime-$user
		mkdir -p -m 0700 \"\$XDG_RUNTIME_DIR\"
		setsid weston --backend=headless --renderer=pixman --xwayland --socket=wayland-1 \
			--width=1600 --height=1000 >/tmp/weston.log 2>&1 &
		for second in \$(seq 30); do ls /tmp/.X11-unix/X* >/dev/null 2>&1 && break; sleep 1; done
		ls /tmp/.X11-unix/X* >/dev/null 2>&1 || { cat /tmp/weston.log; exit 1; }
		$screen XDG_SESSION_TYPE=wayland WAYLAND_DISPLAY=wayland-1 XDG_CURRENT_DESKTOP=$current_desktop \
			JAVA_TOOL_OPTIONS=-Dcopperhead.remote-debugging-port=9222 setsid nohup '$home/bin/Copperhead' >$log 2>&1 &"
}

address() {
	await "Copperhead serving its interface" "grep -q 'serving its interface at http://' $log"
	grep -o 'serving its interface at http://[^ ]*' "$log" | head -1 | sed 's/^serving its interface at //; s#/*$##'
}

check_refuses_requests_without_the_token() {
	local status

	status=$(as_user "curl -s --max-time 10 -o /dev/null -w '%{http_code}' '$1/' || true")
	[ "$status" = 403 ] || fail "the page without the token answered $status"
}

check_runs_the_bundled_engine() {
	await "the browser engine running from $home/lib/app/jcef" "pgrep -af '[j]cef_helper' | grep -qF '$home/lib/app/jcef'"
	as_user "test ! -e ~/.copperhead/jcef" || fail "a second browser engine was downloaded"
}

check_shows_the_welcome_pane() {
	await "the window" "$screen xdotool search --name '^$title\$'"
	await "the welcome pane drawn in the window" \
		"$screen import -window root -resize 200% png:- | tesseract stdin stdout 2>/dev/null | grep -qiF '$first_page'"
}

# The helpers of the engine only exist once the page is up, so the platform they were told to draw on is read after it.
check_draws_inside_its_own_window() {
	await "the welcome pane drawn inside the window" "window=\$($screen xdotool search --name '^$title\$' | head -1)
		$screen import -window \"\$window\" -resize 200% png:- | tesseract stdin stdout 2>/dev/null | grep -qiF '$first_page'"
	as_user "pgrep -af '[j]cef_helper' | grep -o -- '--ozone-platform=[a-z0-9]*' | sort -u || true"
}

check_window_class() {
	await "a window of the class $class" "$screen xdotool search --class '^$class\$'"
}

# The launcher runs Java inside its own process, so what there is to look for is the launcher's name.
check_quits_when_the_window_closes() {
	as_user "pgrep -x Copperhead >/dev/null" || fail "Copperhead was not running before its window was closed"
	as_user "$screen python3 /tmp/close-window.py $title" || fail "no window to close"
	await "Copperhead quitting after its window was closed" "! pgrep -x Copperhead >/dev/null"
}

check_clean_log() {
	if grep -Eq "$broken_engine" "$log"; then
		fail "the engine reported a fault"
	fi
}

check_removal_leaves_nothing_behind() {
	case "$package" in
		*.deb) apt-get remove -y -qq copperhead >/dev/null ;;
		*.rpm) dnf remove -y -q copperhead >/dev/null ;;
	esac

	test ! -e "$packaged/bin/Copperhead" || fail "the program stayed after the removal"
	test ! -e "$packaged/lib/copperhead-Copperhead.desktop" || fail "the menu entry stayed after the removal"
}

case "$package" in
	*.tar.gz) home=$portable ;;
	*) home=$packaged ;;
esac

if [ "$desktop" = wayland ]; then
	screen='DISPLAY=:$(ls /tmp/.X11-unix | sed s/^X//)'
else
	screen=DISPLAY=:99
fi

family=$(detect_family) || exit 1

install_tools

if [ "$desktop" = wayland ]; then
	install_wayland
fi

install_copperhead
cp "$here/close-window.py" /tmp/close-window.py
useradd -m "$user"
trap stop_what_was_started EXIT

if [ "$desktop" = wayland ]; then
	start_on_wayland
	address >/dev/null
	check_draws_inside_its_own_window
	check_clean_log
	echo "Copperhead draws inside its own window on Wayland ($PRETTY_NAME)."
	exit 0
fi

if [ "$home" = "$packaged" ]; then
	check_menu_entry
fi

start_on_x11
serving=$(address)
check_refuses_requests_without_the_token "$serving"
check_runs_the_bundled_engine
check_shows_the_welcome_pane

if [ "$home" = "$packaged" ]; then
	check_window_class
fi

check_quits_when_the_window_closes
check_clean_log
reinstall_copperhead

if [ "$home" = "$packaged" ]; then
	check_removal_leaves_nothing_behind
fi

echo "Copperhead installs, runs and goes again on $PRETTY_NAME ($(basename "$package"))."
