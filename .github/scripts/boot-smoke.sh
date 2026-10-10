#!/usr/bin/env bash
# Boots a trackloader disk in FS-UAE on its built-in AROS ROM, on a virtual screen, and passes once the middle of
# the picture shows the copper bars: many colours down one narrow strip, where a screen still waiting for the disk
# shows one or two. AROS takes 12 to 14 seconds to reach the program, so the first look comes after 20. The strip
# leaves out the bottom of the window, where FS-UAE prints its notices.
# Usage: boot-smoke.sh <disk.adf> [screenshot.png]
set -euo pipefail

disk=$(realpath "${1:?usage: boot-smoke.sh <disk.adf> [screenshot.png]}")
shot=$(realpath -m "${2:-boot.png}")
first_look=20
give_up=90
poll=2
least_colours=16
work=$(mktemp -d)
xvfb=""
emulator=""
trap 'kill $emulator $xvfb 2>/dev/null || true; wait 2>/dev/null || true; rm -rf "$work"' EXIT

fail() {
	local message=$1
	shift
	echo "$message" >&2
	for log in "$@"; do
		tail -n 40 "$work/$log" >&2 || true
	done
	exit 1
}

# Xvfb picks a free display itself and writes its number to the descriptor once it listens, so that two runs on
# one machine never meet on the same display.
Xvfb -displayfd 3 -screen 0 800x600x24 3>"$work/display" >"$work/xvfb.log" 2>&1 &
xvfb=$!

for _ in $(seq 50); do
	[ -s "$work/display" ] && break
	sleep 0.2
done
[ -s "$work/display" ] || fail "Xvfb did not start." xvfb.log
screen=:$(tr -d '\n' <"$work/display")

DISPLAY=$screen SDL_VIDEODRIVER=x11 ALSOFT_DRIVERS=null fs-uae --base-dir="$work/fs-uae" \
	--kickstart_file=internal --amiga_model=A500 --floppy_drive_0="$disk" \
	--fullscreen=0 --window_width=800 --window_height=600 >"$work/fs-uae.log" 2>&1 &
emulator=$!

sleep "$first_look"

for ((waited = first_look; waited <= give_up; waited += poll)); do
	kill -0 "$emulator" 2>/dev/null || fail "FS-UAE stopped before ${waited}s." fs-uae.log
	import -display "$screen" -window root "$shot"
	colours=$(convert "$shot" -gravity center -crop 1x60%+0+0 +repage -format %k info:)
	echo "after ${waited}s: ${colours} colours down the middle"

	if [ "$colours" -ge "$least_colours" ]; then
		exit 0
	fi

	sleep "$poll"
done

fail "The disk did not show its picture within ${give_up}s." fs-uae.log fs-uae/Cache/Logs/fs-uae.log.txt
