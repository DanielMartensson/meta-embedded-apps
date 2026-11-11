# FTEQW - the gles client both games play on, and the record of its build

Detail file for `recipes-apps/fteqw/fteqw_git.bb`, kept out of the layer's own
README so that README stays one screen long. It says what the recipe asks of
FTEQW's own makefile, how the seat's two games are launched from here, what the
options that were measured on this board do, and what was tried and rejected
before this engine was chosen.

## What this builds

Pinned `f09ac7cccd2d713ddf881d3f1324703082c2cfe4` with `branch = "master"`,
`GPL-2.0-or-later` checksummed at `engine/LICENSE` md5
`79c2cc4426b9e6f3b9098f02eb47aac1`. Two edits sit in `files/`, both applied:

- `0001-use-the-gles2-headers-instead-of-the-desktop-gl-ones.patch` (1243 B,
  md5 `e498f0bc7c78991f38268c0ead90def1`): the only place in the tree that
  decides whether the GL layer includes the desktop `GL/gl*.h` or the ES
  `GLES2/gl*.h` is the `#if defined(ANDROID)` at
  `engine/gl/glquake.h:38`, and the patch extends it to
  `#if defined(ANDROID) || defined(FTE_GLES)`. The compile then carries
  `-DGLESONLY -DGLSLONLY -DFTE_GLES` on top and takes every GL entry point at
  runtime, so the binary never links a desktop GL. An earlier round had drafted
  a local `config_gles.h` and a second patch around it; both were dropped once
  the branch alone compiled green.
- `0002-quake3-real-time-accept-null.patch` (1791 B, md5
  `6701ec38f1fe9186fdd9169304c0ef95`): the OpenArena payload's `qagame.qvm`
  calls the `G_REAL_TIME` game trap with a null `qtime` at several sites
  (`OP_CONST 0; OP_ARG 8; OP_CONST -42; OP_CALL`), and that is legal - ioquake3's
  `VM_ArgPtr(0)` answers NULL, its traps do not validate at all, and
  `Com_RealTime(NULL)` returns `time(NULL)` without touching the struct. FTE's
  `Q3VM_GetRealtime` already has the same `if (qtime)` guard, but the
  `VALIDATEPOINTER` macro at `plugins/quake3/svq3_game.c:837` rejects the null
  before the trap body runs, so the game died with
  `Call to game trap 41 passes invalid pointer` right after the UI VM started.
  The patch lets the null through and keeps the range check for everything else,
  which is what ioquake3 effectively does. It is an engine-side fix, so it lives
  here rather than in the OpenArena recipe, and it is what the second game needs.

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
and `FREETYPE_LDFLAGS="-lfreetype"`. `STRIP` is handed over as OE's own cross
strip, which is what makes the makefile take its `_out-rel` branch: GNU
`strip` does not read an aarch64 object. The release binary lands at
`engine/release/fteqw-glsdl` (`GL_EXE_NAME` is `EXE_NAME` is `FTE_CONFIG`, and
`GL_EXE_NAME` at makefile line 1145 is one directory above `EXE_NAME`) and
installs as `/usr/bin/fteqw-gles` - 5493000 B, md5
`3328dc12cf97264b24f86da0342916d4` on the seat, unchanged since the 0002 patch
landed; `do_package_qa` needs `INSANE_SKIP:${PN} += "already-stripped"` because
the makefile strips its own release build. `ldd -d` on the installed binary
answers no `libGL` and no `libGLEShim` in `NEEDED` - the client speaks only the
seat's own Vivante GCNANOULTRA31_VIP2 V6.4.21 driver, and the seat has no
desktop GL at all.

This round's deb is `fteqw_0.0+git0+f09ac7cccd-r0.01_arm64.deb`, 1920812 B, md5
`29a198a9f8bf8653d4f2540c8d49cd52`, and `dpkg -i` on the seat answered
`Unpacking fteqw (0.0+git0+f09ac7cccd-r0.01) over (0.0+git0+f09ac7cccd-r0.01)`
because the launcher changed and the binary did not: `/usr/bin/fteqw-gles` kept
its md5 across the install, so only the launcher's defaults moved.

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

`jdolan/quetoo-data` was asked about and rejected the same way. Its licence
(CC-BY-SA-4.0) would have been fine, but the payload is Quetoo's, not
FTEQW's: the release `v1.0.73` data is a 1.15 GB zip (1.09 GB in the deb)
against 403 MB free on the seat, and the content does not open even with room.
A per-map pk3 holds `maps/<name>.bsp` plus Quetoo's own `maps/<name>.mf`; the
BSP header is `IBSP` version 84, which is not one of the magics this engine
registers (`47` RTCW, `46` Quake III, `38` Quake II, `69` Quake2World, in
`engine/gl/gl_model.c`), so the seat answers `Unrecognised model format IBSP`
and then `Mod_NumForName: maps/rage.bsp not found or couldn't load`. The pk3
itself is not a zip FTEQW reads either (`appears to be missing its central
directory`, `engine/common/fs_zip.c`), and the assets are `.mat`/`.mf` with no
Quake III `.shader` and no `qagame.qvm`, so there is no gamecode to run after
any conversion. This layer does not build the Quetoo engine, and the data is
only for that engine, so no recipe was written and nothing from it is
installed.

## How the seat's copy is used

The seat needs the usual Wayland/SDL environment: `XDG_RUNTIME_DIR=/home/weston`
(which is where this image's weston socket lives, so keep it rather than
replace it), `WAYLAND_DISPLAY=wayland-1`, `SDL_VIDEODRIVER=wayland`,
`SDL_AUDIODRIVER=alsa`. Then `/usr/bin/fteqw` (1244 B, md5
`1286cd7dcdda11ed182781bbd1ffc814`, the launcher this recipe's
`do_install:append` generates and the seat carries) runs the default pair
`-basedir /usr/share/librequake -game id1 -quake`: the LibreQuake payload this
layer installs, and `-quake` says which protocol that tree speaks. Its
`FTEQW_ARGS` default is the set measured here:

| Set | Measured effect on this board |
| --- | --- |
| `vid_gl_context_es 1` | ask the driver for a GL ES 3.1 context: left alone the Vivante answers an "OpenGL ES-CM 1.1" context and the renderer refuses it, so nothing draws |
| `vid_fullscreen 1` + `vid_width 1024` + `vid_height 576` | let the window fill the seat's 1920x1080 output while the 3d scene is drawn into a smaller surface the compositor scales up. This is the one setting that decides how smooth the game plays - see the ladder below |
| `pr_ssqc_memsize 134217728` | the QCC arena for the LibreQuake payload's `progs.dat`: the engine mallocs it whole and re-mallocs it as the progs grow, and LibreQuake's sprite sequence needs 128 MB where the default is 32 MB (`server/pr_cmds.c` names it, `pr_fixbrokenqccarrays` beside it). The Quake III gamecode has no QCC arrays, so the OpenArena launcher does not carry it |
| `pr_csqc_memsize 64m` | sizes the client-side VM (`client/pr_csqc.c:69`, fed to the same `PR_Configure`). Its own default is -1; 64m is what the chained-`+wait` queue needed, see the pitfall below |

The seat's GPU is fill-rate bound, so what decides the framerate is the area of
the surface the seat actually allocates, and that is not always the size asked
for: a 16:9 size the seat does not answer is padded up to the next 4:3 one.
The whole ladder, measured on the seat on Nov 11 2026 over the steady 20 s of
`lqdm3` by counting the wayland frame commits of the game's own surface (and
read off the same log against the windowed `640x480` case) - the `damage()`
column is the surface the seat allocated:

| Ask | Surface the seat allocates | LibreQuake `lqdm3` steady fps |
| --- | --- | --- |
| `vid_fullscreen 2` (1920x1080) | 1920x1080 | 14.0 |
| 1600x900 | 1600x900 | 21.6 |
| 1280x720 | 1280x720 | 32.5, 32.6, 32.8 |
| 1152x648 | 1152x864 | 29.6 - the pad costs more than the round size above it |
| 1024x576 (shipped) | 1024x768 | 37.0, 37.0, 37.0 |
| 960x540 | 1024x768 | 36.9, 37.2 - same surface, so the wider 1024x576 is the better ask |
| 896x504 | 1024x768 | 36.9 - same surface again, a free size to ask but no free fps |
| 768x432 | 800x600 | 56.9, 57.1 |
| 640x360 | 640x480 | 63.0, 63.7, 64.3 |
| `vid_fullscreen 0` at 640x480 | 640x480 | 64.1 - costs the same as the fullscreen ask, leaves a centred window |

OpenArena follows the same curve on the same client; its own numbers, with a
much wider run-to-run spread, are in `../openarena/README.md`. The launcher
ships 1024x576: at 37 fps LibreQuake is already within a whisker of the ~64 fps
this seat's own repaint cadence puts a ceiling on, so asking below it buys
almost nothing, and 768x432 and 640x360 are one `FTEQW_ARGS` away for the
blockier, faster tiers, 1280x720 for the sharper slower one. What does NOT
help is the quality cvars: `r_vertexlight 1`, `r_dynamic 0`, `gl_picmip 2`,
`GL_LINEAR_MIPMAP_NEAREST`, `r_fastsky 1`, `gl_texture_anisotropy 0` and
`r_warp 1` each moved `lqdm3` by under 2 fps at 1080p on their own (14.0 to
15.3-16.4), and the only two that buy anything at all do it by not drawing the
lightmap at all (`r_drawflat 1` at 24.6, `r_lightmap 1` at 28.2).

Override with `FTEQW_BASEDIR` / `FTEQW_GAME` / `FTEQW_ARGS`, or pass your own
`-basedir`, `-game` and `-set` arguments on the command line, which win.
OpenArena speaks a different protocol and gets its own launcher,
`/usr/bin/openarena`, from its own recipe. The home/config subtree follows the
game: `-game id1 -quake` writes under `~/.local/share/quake/fte/`, `-game
baseoa -quake3` under `~/.local/share/quake3/baseoa/`, which is also where
each game's `+screenshot` lands (`fte-<stamp>-0.png`) and where a bare
`+screenshot <name> +quit` writes a menu frame - `t3.png` read back as
1024x768 was the proof of the padded surface above.

## What the seat did with it, measured Nov 11 2026

Two kinds of number are below. The fps numbers count the `wl_surface@19.commit()`
lines of the game's own surface in a `WAYLAND_DEBUG=1` log over the steady 20 s
that starts 12 s into the run - the game prints no fps of its own here because
`-set show_fps 0` is what keeps the console quiet, and `cl_showfps` does not
survive the queue. The timestamps in that log are milliseconds, which is the bug
an earlier round met when the same count answered 12284 fps.

The runs below use only what the shipped launchers carry:

```
/usr/bin/fteqw +wait 3 +map lqdm3                (LibreQuake, launcher defaults only)
  console over the running game:
    current map : lqdm3 (Hyperborea)
    entities : 60/60/131072 (mem: 3.2%)   models 50/16384   sounds 79/4096
    gamedir : qw
  log proof:  Setting mode 1024*576 32bpp OpenGLES2+
              wl_surface@19.damage(0, 0, 1024, 768)
              xdg_toplevel@26.set_fullscreen(wl_output@13)
              "LQ Player joined the server"
              lock_pointer x2, relative_pointer_v1 relative_motion x4
              Initing default SDL audio device.
  fps: 37.0 steady over the measured 20 s
  cost while it ran (from /proc, 6 s apart): 85 % of one core, rss 146984 kB
  ps beside it: VSZ 1041076 KB  RSS 147104 KB  %CPU 94.0
  free -m while it ran: 1768 total, 464 used, 1304 available

/usr/bin/openarena +wait 3 +map oa_dm6           (OpenArena, launcher defaults only)
  console: SpawnServer: oa_dm6   AAS initialized.   "Player entered the game"
  log proof:  Setting mode 1024*576 32bpp OpenGLES2+
              wl_surface@19.damage(0, 0, 1024, 768)
              xdg_toplevel@26.set_fullscreen(wl_output@13)
              lock_pointer x1, relative_pointer_v1 relative_motion x2
              Initing default SDL audio device.
  fps: 53.5 steady over the measured 20 s (OA swings far more run to run -
       see the range in ../openarena/README.md)
  cost while it ran (from /proc, 6 s apart): 77 % of one core, rss 161688 kB
  ps beside it: VSZ 894736 KB  RSS 161836 KB  %CPU 95.0
  free -m while it ran: 1768 total, 571 used, 1196 available
```

The mouse and the audio are still what they were: the games ask for
`lock_pointer` (and LibreQuake additionally for `relative_pointer_v1`) and the
compositor answers both, so mouselook keeps working at the smaller buffer, and
`SDL_AUDIODRIVER=alsa` plus the `pcm.!default` stanza the OS layer added is what
prints `Initing default SDL audio device.` on both. LibreQuake's ALSA side also
logged `snd_pcm_recover underrun occurred` once during a round, without the
sound going quiet - it is the same underrun the seat's own monitor shows at
every underrun, and the audio still played.

The round that first put these games on the seat ran them at the seat's own
1920x1080 and measured the older, slower shape, and those numbers stay here for
the comparison: LibreQuake at `VIRT 620080 KB / RES 55672 KB / SHR 10832 KB /
%CPU 0.0 -> 16.7` with the seat's monitor reading ~240-277 MB used of 1768 and a
128171 B screenshot (`~/.local/share/quake/fte/screenshots/fte-20261111025415-0.png`),
OpenArena at `VIRT 851264 KB / RES 120272 KB / SHR 16616 KB / %CPU 25.0 -> 61.5`
with ~590 MB used and a 1587779 B screenshot
(`~/.local/share/quake3/baseoa/screenshots/fte-20261111041046-0.png`), at
14.0 and 22.1 fps. The busybox `top` truncates the process name to `fteqw-g+`,
so a `ps` filter on the game never matches on this board while `ps -o ... -p
<pid>` off `/proc` does.

## Pitfalls measured in this round

- `wait` delays ONE frame and ignores its argument (`engine/common/cmd.c:206`
  `Cmd_Wait_f` reads the number but waits a single `rframe`), so an argument
  list that chains waits to hold the game open does not hold it: 900-1200
  chained `+wait`s die at the `Out of memory` in
  `engine/qclib/initlib.c:532`, the `PR_Configure` malloc of the client-side
  Quake-C VM that `pr_csqc_memsize` sizes - the arena is re-malloc'd whole
  every time the queued-command list grows, so the fix is `pr_csqc_memsize
  64m` in the launcher rather than a longer queue, and short queues load the
  map 8/8. (The same call exists server-side and is sized by
  `pr_ssqc_memsize`, which is the arena `progs.dat` needs; the two are
  different arenas and the chained-`+wait` OOM is the client-side one.)
- FTE exits when its console queue finishes because it was not started from a
  tty; `ssh -tt` keeps it alive, and typed console commands take NO `+`
  prefix - only command-line queued ones do.
- `-set con_logfile <name>` creates the file and leaves it at 0 bytes: this
  build's console text does not go there, so console answers were read from a
  pty instead. And `screenshot` typed into that pty answers
  `Unknown command "screenshot"` even though `client/cl_screen.c:3671`
  registers it - that command belongs to the GUI console, and the C-side
  stdin console does not carry it. A `+screenshot <name> +quit` on the
  command line does work, but only ever catches the menu: `+wait` chains that
  would advance real frames instead of the queue end in a timeout with no file
  (400 chained `+wait`s answered `rc=124`).
- The weston compositor never grants the screenshooter authority on this
  seat (`weston_compositor_add_screenshot_authority` is never called), so
  `weston-screenshooter` always answers "unauthorized": the picture proofs
  of this whole layer are FTE's own screenshots, and one shot of a running
  game is as far as the board gets.
- A `-basedir /usr/share -game lq1` pair with a `/usr/share/lq1` symlink was
  an earlier round's hack: the launcher now mounts the payload directly
  (`-basedir /usr/share/librequake -game id1`) and no symlink is anywhere.
- FTE refuses gamedir names starting `lib`/`bin`/`doc`/`help`/`downloads`, and
  `-updatesrc ""` had to stay on both games' command lines.
- In the Quake III gamecode a double `+map` (a launcher default map plus the
  user's own) and `-set sv_listen_q3 1` both segfault the client, `rc=139`, so
  the OpenArena launcher carries no default map and the map comes in as
  arguments - the rest of the Q3 gamecode's failure modes are in
  `../openarena/README.md`.
