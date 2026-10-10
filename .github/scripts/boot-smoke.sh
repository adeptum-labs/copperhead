#!/usr/bin/env bash
# Boots a trackloader disk in FS-UAE on its built-in AROS ROM, on a virtual screen, and passes once the middle of
# the picture shows the copper bars: many colours down one narrow strip, where a screen still waiting for the disk
# shows one or two. AROS takes 12 to 14 seconds to reach the program, so the first look comes after 20. The strip
# leaves out the bottom of the window, where FS-UAE prints its notices.
# Usage: boot-smoke.sh <disk.adf> [screenshot.png]
set -euo pipefail

disk=$(realpath "$1")
shot=$(realpath -m "${2:-boot.png}")
screen=:42
first_look=20
give_up=90
poll=2
least_colours=16
work=$(mktemp -d)

Xvfb "$screen" -screen 0 800x600x24 >"$work/xvfb.log" 2>&1 &
xvfb=$!
emulator=""
trap 'kill $emulator $xvfb 2>/dev/null || true' EXIT

for _ in $(seq 50); do
	[ -e "/tmp/.X11-unix/X${screen#:}" ] && break
	sleep 0.2
done

DISPLAY=$screen SDL_VIDEODRIVER=x11 SDL_AUDIODRIVER=dummy fs-uae --base-dir="$work/fs-uae" \
	--kickstart_file=internal --amiga_model=A500 --floppy_drive_0="$disk" \
	--fullscreen=0 --window_width=800 --window_height=600 >"$work/fs-uae.log" 2>&1 &
emulator=$!

sleep "$first_look"

for ((waited = first_look; waited <= give_up; waited += poll)); do
	import -display "$screen" -window root "$shot"
	colours=$(convert "$shot" -gravity center -crop 1x60%+0+0 +repage -format %k info:)
	echo "after ${waited}s: ${colours} colours down the middle"

	if [ "$colours" -ge "$least_colours" ]; then
		exit 0
	fi

	sleep "$poll"
done

echo "The disk did not show its picture within ${give_up}s." >&2
tail -n 40 "$work/fs-uae/Cache/Logs/fs-uae.log.txt" >&2 || true
exit 1
