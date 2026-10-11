# Copperhead

An Amiga demo and game studio for Linux: write 68000 assembly, paint sprites, shape the palette and the
copper list, step through the debugger, lay parts out on a timeline and build an ADF for an A500 (OCS) or an
A1200 (AGA).

A project is a folder on disk: its sources are edited in the Code tool and Build ADF turns them into a bootable
trackloader disk with vasm and Shrinkler. The sprite, palette, debugger, emulator and timeline tools still show
sample data. Build & run builds the disk and then only sets the emulator pane to running; the disk is not yet
loaded into an emulator.

## Building

Copperhead needs JDK 25.

```bash
mvn install                 # build and test
mvn -pl shell exec:java     # run from the build tree
mvn -Pbrowser install       # also drive the interface in a real browser
mvn -Pinstaller install     # also build the DEB and RPM packages (needs the platform's packaging tools, e.g. fakeroot and rpm on Debian)
mvn -Ptoolchain verify -pl toolchain -am   # fetch and compile the real vasm and Shrinkler, build both templates
```

Playwright refuses hosts it does not recognise (LMDE and other Debian derivatives); run
`mvn -Pbrowser install` with `PLAYWRIGHT_HOST_PLATFORM_OVERRIDE=debian12-x64` set there.

The source editor is CodeMirror 6 with a mode for Motorola 68000 assembly. It is built once into
`ui/src/main/resources/static/js/editor.bundle.js` and committed together with its provenance, so the Maven build
needs no Node. After changing `ui/frontend/` or one of its pinned versions, rebuild it with Node and npm (CI uses
Node 24):

```bash
ui/frontend/build.sh        # npm ci, the tokenizer and editor tests, esbuild, the provenance file
```

## The toolchain

vasm and Shrinkler are not bundled. The first build fetches their sources, checks them against SHA-256 hashes
pinned in Copperhead, compiles them with the system's `make`, `cc` and `c++` in a temporary folder below
`$XDG_CACHE_HOME/copperhead` that is removed afterwards, and keeps the programs under
`$XDG_DATA_HOME/copperhead/tools` for every later build:

| Tool | Source | Kept at |
|---|---|---|
| vasm 2.0f | `http://sun.hasenbraten.de/vasm/release/vasm.tar.gz` | `~/.local/share/copperhead/tools/vasm/<sha256>/vasmm68k_mot` |
| Shrinkler 4.7 | `https://codeload.github.com/askeksa/Shrinkler/tar.gz/refs/tags/v4.7` | `~/.local/share/copperhead/tools/shrinkler/<sha256>/Shrinkler` |

The Build tool's Toolchain card shows where each tool was found and fetches both on request. A program named in
Edit → Preferences… is used as it is; otherwise the store comes first and then `PATH`. When the compilers are
missing the build says what to install (`sudo apt install build-essential` on Debian and Ubuntu,
`sudo dnf install gcc gcc-c++ make` on Fedora); Copperhead never runs `sudo` itself.

vasm's release archive is replaced in place whenever vasm is updated. When its hash no longer matches, the build
pauses and shows the version the archive holds and its hash; accepting stores that hash as trusted, refusing stops
the build. Shrinkler is fetched from a tagged release, so a different hash is refused outright.

## Projects

A project is a folder named after the project. `<name>.chp` is its project file, `src/` holds the assembly
sources (`*.s`, and `*.i` for includes), `gfx/` the graphics, and Build ADF writes the disk to `build/<name>.adf`.

## Preferences

Copperhead keeps its preferences in `~/.config/copperhead/preferences.json`: the theme, the split between the
tool and the emulator (`split`, percent), the height of the timeline (`bottom`, pixels), the paths chosen for the
tools, a trusted vasm hash and the projects opened lately, the newest first:

```json
{
	"theme": "dark",
	"split": 56,
	"bottom": 250,
	"tools": { "vasm": "/usr/local/bin/vasmm68k_mot" },
	"trustedVasm": "…",
	"recent": ["/home/you/amiga/demo/demo.chp"]
}
```

The file is written whole and moved into place, so it is never left half written. A file that cannot be read is
moved aside to `preferences.json.bak`, and the studio starts from the defaults and says so on the status bar.

Every folder follows the XDG base directories: `XDG_CONFIG_HOME`, `XDG_DATA_HOME` and `XDG_CACHE_HOME` take the
place of `~/.config`, `~/.local/share` and `~/.cache` when they name absolute paths. The browser engine keeps its
profile in `<cache>/copperhead/cef`, where `<cache>` is `$XDG_CACHE_HOME` or `~/.cache`; a run from the build tree keeps the engine itself in `~/.copperhead/jcef`.

## Continuous integration

GitHub Actions runs two workflows on Temurin 25:

- `build.yml`, on pushes to `master` and on pull requests:
  - `build`: `mvn -B install -Pbrowser`, then rebuilds the editor bundle and fails when it differs from the
    committed one.
  - `toolchain`: `mvn -B verify -Ptoolchain -pl toolchain -am`, which fetches and compiles the real vasm and
    Shrinkler and builds both templates into disks.
  - `boot`: boots the demo disks from the `toolchain` job in FS-UAE on its built-in AROS ROM under Xvfb, and
    checks the screenshot for the copper bars (`.github/scripts/boot-smoke.sh`).
- `installers.yml`, on pushes to `master`, on `v*` tags and when started by hand:
  - `package`: builds the DEB, the RPM and the portable folder, and checks that the packaged Java runtime serves
    the studio's files.
  - `install`: installs each package in a fresh container (Debian 12 and 13, Ubuntu 22.04 and 24.04, Fedora 42,
    and the portable folder on Debian 12) and runs `desktop/src/test/ci/installed-check.sh`: the menu entry, the
    window, the page refused without its token, the bundled browser engine, the welcome pane read back by OCR, the
    window class, quitting when the window closes, a clean log, a reinstall and the removal. On Ubuntu 24.04 and
    Fedora 42 it runs once more on a Wayland desktop (Weston with Xwayland), where the page has to be drawn
    inside Copperhead's own window.

The default `mvn install` needs neither Docker nor the network beyond Maven's own downloads. The profiles above and
the workflows do fetch things (vasm and Shrinkler, the Playwright browser), but the Maven build never uses Docker.

## Licence

Copyright © 2026 Adam Waldenberg and Adeptum AB. Licensed under the GNU General Public License, version 3; see `COPYING`.

The editor bundle contains CodeMirror and its companion packages, which are under the MIT licence; their notices
are in `ui/src/main/resources/static/js/editor.bundle.js.provenance`.
