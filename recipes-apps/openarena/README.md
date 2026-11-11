# OpenArena - the second game's data, and its record

Detail file for `recipes-apps/openarena/openarena.bb`, kept out of the layer's
own README so that README stays one screen long: what the bundle and its
license actually are, what the seat played with it and what that cost, and the
failure modes of the FTEQW Quake III gamecode that this round measured.

## Free both ways - which is why this is the second game

The rule this layer plays by is that a game has to be free to use AND free to
download before it gets a data recipe, and OpenArena is: the project built its
maps, models, textures, sounds and music from scratch, `readme_088.txt` inside
the release zip carries a LICENSE section that reads "The program and data are
licensed under the GNU GPL v2. See the file 'COPYING' for details.", and `COPYING`
at the top of that same zip is the 17992-byte GNU v2 text with GNU's own md5
`94d55d512a9ba36caa9b7df079bae19f`. So one free download gets the whole game -
no proprietary pak sits anywhere in the payload, which is exactly what the
id Quake data (LibreQuake had to replace) and the Quake II search (stopped:
the yamagi downloads are source archives of the client plus the CTF/Reckoning/Ground
Zero addon source packages, and every one of them still wants id's paks -
there is no fully free Q2 asset set, so no Q2 recipe and no further search)
do not.

## What the recipe installs

The pin is the project's own whole release as one zip,
`http://download.tuxfamily.org/openarena/rel/088/openarena-0.8.8.zip`: 425189255
bytes, the site's file page publishes 395.51 MB with md5
`9f353d96d7889c377349d692c3905e5b` and this round re-fetched it and agrees
with both, so the recipe pins the sha256 `5a8faf7f5b51f351b0a1618c06b6b98a5f1a6758f1d39818de2c87df2a0bac4a`
of those exact bytes (openarena.ws itself hands the release through a download
form whose link now 302-redirects through the Internet Archive, so the form is
not a stable fetch address). The zip unpacks to `openarena-0.8.8/` and the
recipe installs only the payload: `baseoa/` with its nine `pak0..pak6-patch088`
pk3s, 410218649 B of maps, players, weapons, shaders and sounds, and
`missionpack/` with its one `mp-pak0.pk3`, 6250219 B, kept as a sibling
directory rather than merged, so a map inside it stays addressable by its own
name. The rest of the zip (macOS `.app` bundles, the Windows `openarena.exe`,
readmes and credits) is not installed. This round's deb is
`openarena_0.8.8-r0.01_arm64.deb`, 403636880 B, md5
`788b1b02a473ec1dac2e2574b5463f1d`, 11 payload files: the ten pk3s and the
launcher (the data manifest of the previous round, `/tmp/opencode/manifests/openarena.txt`
on the build host with md5 `73acc115f837df4b0c8bac46637b5424` for the whole tree
and 416469563 B, is what the seat's own tree was verified against, and this
round's ten pk3s are the same bytes because nothing about the payload moved).
The dirs install at 0755 root:root with the pk3s at 0644: `unzip` leaves the
tree at 700 dirs owned by whoever runs bitbake, and a 700 dir is what the seat
answers "Failed to open dir /usr/share/openarena/baseoa/" with, and the uid is
what `do_package_qa` calls host contamination.

## The engine side is FTEQW's, not this recipe's

FTEQW carries the Quake III engine code compiled in, not loaded: `config_fteqw.h`
has `-DLINK_QUAKE3`, `engine/Makefile:142` resolves `LINK_QUAKE3=1` and links
`../plugins/quake3` plus botlib straight into the binary, and `engine/common/plugin.c:66`
registers `{"quake3", Plug_Q3_Init}`. Running the client prints `------- Quake III Arena Initialized -------`.
FTEQW's own table of named games has no OpenArena entry, so the launcher states
`-quake3` by hand and `-game baseoa` only names the tree - which protocol and
which data are therefore both explicit on the seat, nothing is guessed.
`-updatesrc ""` has to stay on both the Quake and the OpenArena runs. The one
engine edit this game needs is not in this recipe either: OpenArena's `qagame.qvm`
calls the `G_REAL_TIME` game trap with a null `qtime`, which FTE's pointer
validator rejected before the trap body ran, and `0002-quake3-real-time-accept-null.patch`
in the fteqw recipe's `files/` is what let the game past
`Call to game trap 41 passes invalid pointer` - see `../fteqw/README.md`.

## How the seat plays it, and what it cost

`/usr/bin/openarena` (1157 B, md5 `37c3c028af16f2d8824972bff23c7300`, the
launcher this recipe's `do_install:append` generates; this round the seat got
the launcher as one extracted file from the deb's own `data.tar.xz` and a
`md5sum` on the seat agrees with that md5) runs
`-basedir /usr/share/openarena -game baseoa -quake3` with `OPENARENA_ARGS`
defaulting to `-set vid_gl_context_es 1 -set vid_fullscreen 1 -set vid_width
1024 -set vid_height 576` - the ES context the Vivante refuses when left
alone, and the render buffer that decides how smooth the seat plays, in the
form the seat accepts without padding. It takes the map as arguments, and
`+wait` before `+map` is not decoration: measured on the seat, a bare `+map`
queued before the renderer starts leaves the game silent with a 6121-byte black
screenshot, while `+wait 3 +map oa_dm6` loads every time:

```
/usr/bin/openarena +wait 3 +map oa_dm6
  console:  SpawnServer: oa_dm6   AAS initialized.   "Player entered the game"
  log proof (WAYLAND_DEBUG=1):
            Setting mode 1024*576 32bpp OpenGLES2+
            wl_surface@19.damage(0, 0, 1024, 768)
            xdg_toplevel@26.set_fullscreen(wl_output@13)
            lock_pointer x1, relative_pointer_v1 relative_motion x2
            Initing default SDL audio device.
  fps: 53.5 steady over the measured 20 s
  cost while it ran (from /proc, 6 s apart): 77 % of one core, rss 161688 kB
  ps beside it: VSZ 894736 KB  RSS 161836 KB  %CPU 95.0
  free -m while it ran: 1768 total, 571 used, 1196 available
```

The fps is counted the same way for both games - the `wl_surface@19.commit()`
lines of the game's own surface in the `WAYLAND_DEBUG=1` log, over the steady
20 s that starts 12 s in - and on OpenArena it swings far more than on LibreQuake,
which never moves by more than 0.1 fps between runs. The whole ladder on the
seat, steady 20 s over `oa_dm6`, with the surface the seat actually allocated:

| Ask | Surface | OpenArena `oa_dm6` steady fps |
| --- | --- | --- |
| `vid_fullscreen 2` (1920x1080) | 1920x1080 | 22.1 |
| 1280x720 | 1280x720 | 40.5, 42.7, 43.4, 46.5, 48.1, 48.4, 52.1, 56.2 (median 47) |
| 1024x576 (shipped) | 1024x768 | 49.5, 50.2, 51.9, 53.5, 53.6, 64.7 (median 52) |
| 768x432 | 800x600 | 53.8, 54.0, 54.2 |
| 640x360 | 640x480 | 52.6, 79.2 |

So the same curve as LibreQuake's: the seat is fill-rate bound and the buffer
area is the lever, and OpenArena's real gain runs from 22 fps at the seat's own
desktop size to about 52 at the shipped 1024x576 - below that the game stops
paying (768x432 measures about the same as 1024x576), while LibreQuake still
gains there. OpenArena's spread is bot-side work, not measurement noise: the
same setting measured 40.5 and 56.2 in two runs.

The same round ran the map again with the launcher installed from the deb's own
bytes: the seat's tree is a verified `cp` rather than a `dpkg -i`, because the
403 MB deb plus its extraction does not fit on a filesystem that is 91 % used
(4271 MB total, 403 MB free) with the 398 MB tree already in place. The
verification is a `md5sum -c` of the deb's own payload manifest run from `/`,
which answers OK for all the payload files - so the bytes the recipe installs
and the bytes the seat plays are one and the same.

## The Q3 gamecode's failure modes, measured here

- a double `+map` (a launcher with a default map plus a user's own) and
  `-set sv_listen_q3 1` both segfault the client, `rc=139`: the launcher has
  no default map, and the map is passed in as `+wait 3 +map <name>`.
- `+connect local` answers `Unable to resolve server address "local"` - this
  build's q3 code has no built-in local-server alias.
- `+status` in the Quake III gamecode prints `0 teams with 0 entities` for a
  single-player map: the Q3 status is a team-matches status, so the Q3 proof
  is the `SpawnServer:`/`AAS initialized.` lines plus the screenshot, where
  the Q1 proof can use the full `+status` block.
- OpenArena's `qagame.qvm` calls `G_REAL_TIME` with a null `qtime`, which is
  legal and which FTE's `VALIDATEPOINTER` used to reject: that is the engine's
  `0002` patch, not something this data recipe can answer.
- `+map` before the renderer is up leaves a black frame; `+wait 3 +map` loads
  every time, and one `+wait` delays one frame (see the fteqw recipe's note:
  `engine/common/cmd.c:206` `Cmd_Wait_f` waits a single `rframe` and ignores
  its argument).
