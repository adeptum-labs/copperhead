# Copperhead

An Amiga demo and game studio for Linux: write 68000 assembly, paint sprites, shape the palette and the
copper list, step through the debugger, lay parts out on a timeline and build an ADF for an A500 (OCS) or an
A1200 (AGA).

This version is the application skeleton: every view works on sample projects held in memory, and no
toolchain or emulator runs yet.

## Building

Copperhead needs JDK 25.

```bash
mvn install                 # build and test
mvn -pl shell exec:java     # run from the build tree
mvn -Pbrowser install       # also drive the interface in a real browser
mvn -Pinstaller install     # also build the DEB and RPM packages
```

Playwright refuses hosts it does not recognise (LMDE and other Debian derivatives); run
`mvn -Pbrowser install` with `PLAYWRIGHT_HOST_PLATFORM_OVERRIDE=debian12-x64` set there.

## Licence

Copyright © 2026 Adam Waldenberg and Adeptum AB. Licensed under the GNU General Public License, version 3; see `COPYING`.
