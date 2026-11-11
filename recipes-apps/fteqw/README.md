# FTEQW - the gles client both games play on, and the record of its build

Detail file for `recipes-apps/fteqw/fteqw_git.bb`, kept out of the layer's own
README so that README stays one screen long. It says what the recipe asks of
FTEQW's own makefile, how the seat's two games are launched from here, what the
options that were measured on this board do, and what was tried and rejected
before this engine was chosen.

## What this builds

Pinned `f09ac7cccd2d713ddf881d3f1324703082c2cfe4` with `branch = "master"`,
`GPL-2.0-or-later` checksummed at `engine/LICENSE` md5
`79c2cc4426b9e6f3b9098f02eb47aac1`. The one edit in `files/` is
`0001-use-the-gles2-headers-instead-of-the-desktop-gl-ones.patch`: the only
place in the tree that decides whether the GL layer includes the desktop
`GL/gl*.h` or the ES `GLES2/gl*.h` is the `#if defined(ANDROID)` at
`engine/gl/glquake.h:38`, and the patch extends it to
`#if defined(ANDROID) || defined(FTE_GLES)`. The compile then carries
`-DGLESONLY -DGLSLONLY -DFTE_GLES` on top and takes every GL entry point at
runtime, so the binary never links a desktop GL. An earlier round had drafted
a local `config_gles.h` and a second patch around it; both were dropped once
the branch alone compiled green, and only the one patch above is in `files/`.

FTEQW has no configure script: its Makefile selects a build-config header by
`FTE_CONFIG` (`fteqw` is the makefile's own default, so it is stated rather
than relied on) and `FTE_TARGET` selects the target: `SDL`, not `SDL2` -
`SDL2` drags in the shared-library link set `make makelibs` builds, measured
in that round. `config_fteqw.h` carries `-DLINK_QUAKE3`, so the Quake III
gamecode and botlib are linked in at `engine/Makefile:142` as built-ins rather
than plugins, and that is what lets the OpenArena data run with nothing to
load at runtime. The target run is `gl-rel`, not `glcl-rel`: `glcl-rel`
compiles `-DCLIENTONLY` and that build dies at `sys_sdl.c:1468`, so the
recipe ships a client that carries more than the client needs rather than a
client that does not compile. `PKGCONFIG` stays at the makefile's own
cross-compiling answer (`/bin/false`), so the SDL probe is answered by the
generated wrapper `${WORKDIR}/fteqw-sdl2-config` and the library sets are
handed over by hand: `IMAGELDFLAGS="-lpng -ljpeg -lz"`,
`OGGVORBISLDFLAGS="-lvorbisfile -lvorbis -logg"` (the soundtracks are ogg)
and `FREETYPE_LDFLAGS="-lfreetype"`. The release binary lands at
`engine/release/fteqw-glsdl` (`GL_EXE_NAME` is `EXE_NAME` is `FTE_CONFIG`)
and installs as `/usr/bin/fteqw-gles` (5493000 B, md5
`18142c733a05b92f58b54bee174665e5` on the seat); `do_package_qa` needs
`INSANE_SKIP:${PN} += "already-stripped"` because the makefile strips its
own release build. `ldd -d` on the installed binary answers no `libGL` and
no `libGLEShim` in `NEEDED` - the client speaks only the seat's own
Vivante GCNANOULTRA31_VIP2 V6.4.21 driver.

## Why this engine, and what was rejected first

QuakeSpasm was the first candidate and died on the board itself: its renderer
is desktop fixed-function GL, and this seat has no desktop GL at all - no
X11, no libGL, the only GL on the board is the Vivante's GLES 3.1. FTEQW is
the engine that can be compiled GLES-only at all, so it is what plays.
Quake II was considered and skipped: FTEQW carries the q2 gamecode, but a
Quake II game needs id's paks, and no fully free Q2 asset set exists - the
yamagi Quake II downloads are the client's source plus the CTF/Reckoning/Ground
Zero addon source packages, whose data still needs the proprietary paks -
so there was nothing free-to-use AND free-to-download to pair it with, the
search was stopped and no Q2 recipe was written. OpenArena (the second game
this layer ships, see `../openarena/README.md`) is free both ways and is what
stands in for that third game.

## How the seat's copy is used

The seat needs the usual Wayland/SDL environment: `XDG_RUNTIME_DIR=/home/weston`,
`WAYLAND_DISPLAY=wayland-1`, `SDL_VIDEODRIVER=wayland`,
`SDL_AUDIODRIVER=alsa`. Then `/usr/bin/fteqw` (766 B, md5
`d5c014e5c333f11205b674182184c699`, the launcher the recipe installs and the
seat carries) runs the default pair `-basedir /usr/share/librequake -game id1
-quake`: the LibreQuake payload this layer installs, and `-quake` says which
protocol that tree speaks. Its `FTEQW_ARGS` default is the set measured here:

| Set | Measured effect on this board |
| --- | --- |
| `vid_gl_context_es 1` | ask the driver for a GL ES 3.1 context: left alone the Vivante answers an "OpenGL ES-CM 1.1" context and the renderer refuses it |
| `vid_fullscreen 2` | keep the window at the seat's desktop size; 0/1 were not used here |
| `pr_ssqc_memsize 134217728` | the QCC arena for the LibreQuake payload's progs (`server/pr_cmds.c` names it `pr_ssqc_memsize`, `pr_fixbrokenqccarrays` beside it); the Quake III gamecode has no QCC arrays, so the OpenArena launcher does not carry it |

Override with `FTEQW_BASEDIR` / `FTEQW_GAME` / `FTEQW_ARGS`, or pass your own
`-basedir`, `-game` and `-set` arguments on the command line, which win.
OpenArena speaks a different protocol and gets its own launcher,
`/usr/bin/openarena`, from its own recipe. The home/config subtree follows the
game: `-game id1 -quake` writes under `~/.local/share/quake/fte/`, `-game
baseoa -quake3` under `~/.local/share/quake3/baseoa/`, which is also where
each game's `+screenshot` lands (`fte-<stamp>-0.png`).

## What the seat did with it, measured Nov 11 2026

The two rounds the seat ran while this round's README was written:

```
/usr/bin/fteqw +wait 3 +map lqdm3        (LibreQuake, the launcher's own defaults)
  current map : lqdm3 (Hyperborea)   entities : 60/60/131072 (mem: 3.2%)
  models 50/16384   sounds 79/4096   gamedir : qw
  top over the run:  VIRT 620080 KB  RES  55672 KB  SHR 10832 KB  %CPU 0.0 -> 16.7
  free -m while it ran: ~240-277 MB used of 1768 MB
  rendered-world screenshot ~/.local/share/quake/fte/screenshots/fte-20261111025415-0.png 128171 B

/usr/bin/openarena +wait 3 +map oa_dm6          (LibreQuake's launcher defaults; OA takes its map as arguments)
  SpawnServer: oa_dm6   AAS initialized.
  top over the run:  VIRT 851264 KB  RES 120272 KB  SHR 16616 KB  %CPU 25.0 -> 61.5
  free -m while it ran: ~590 MB used of 1768 MB
  screenshot ~/.local/share/quake3/baseoa/screenshots/fte-20261111041046-0.png 1587779 B
```

The busybox `top` truncates the process name to `fteqw-g+`, so a `ps` filter
on the game never matches on this board; `top -b -n 1` does.

## Pitfalls measured in this round

- `wait` delays one frame and ignores its argument (`engine/common/cmd.c:206`
  `Cmd_Wait_f` reads the number but the command waits a single `rframe`), so
  an argument list that chains waits to hold the game open does not hold it:
  900-1200 `+wait` chains die at the `Out of memory` in
  `qclib/initlib.c:532` (the `pr_ssqc_memsize` arena is malloc'd into the
  command-queue arena and remalloc'd whole every time the queue grows), and
  short queues load the map 8/8.
- FTE exits when its console queue finishes because it was not started from a
  tty; `ssh -tt` keeps it alive, and typed console commands take NO `+`
  prefix - only command-line queued ones do.
- `-set con_logfile <name>` creates the file and leaves it at 0 bytes: this
  build's console text does not go there, so console answers were read from
  a pty instead. And `screenshot` typed into that pty answers
  `Unknown command "screenshot"` even though `client/cl_screen.c:3671`
  registers it - that command belongs to the GUI console, and the C-side
  stdin console does not carry it.
- The weston compositor never grants the screenshooter authority on this
  seat (`weston_compositor_add_screenshot_authority` is never called), so
  `weston-screenshooter` always answers "unauthorized": the picture proofs
  of this whole layer are FTE's own screenshots, and one `savecmd`-side shot
  of a running game is as far as the board gets.
- A `-basedir /usr/share -game lq1` pair with a `/usr/share/lq1` symlink was
  an earlier round's hack: the launcher now mounts the payload directly
  (`-basedir /usr/share/librequake -game id1`) and no symlink is anywhere.
