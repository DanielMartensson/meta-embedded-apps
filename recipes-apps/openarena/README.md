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
readmes and credits) is not installed. The deb is
`openarena_0.8.8-r0.01_arm64.deb`, 403637404 B, 11 payload files
(this round's recomputed manifest `/tmp/opencode/manifests/openarena.txt` on
the build host, md5 `73acc115f837df4b0c8bac46637b5424`, tree 416469563 B).
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
`-updatesrc ""` has to stay on both the Quake and the OpenArena runs.

## How the seat plays it, and what it cost

`/usr/bin/openarena` (695 B, md5 `a13a33d78feb4cf9afdb2a5e49f3a81d`, the launcher
this recipe's `do_install:append` generates and the seat carries) runs
`-basedir /usr/share/openarena -game baseoa -quake3` with `OPENARENA_ARGS`
defaulting to `-set vid_gl_context_es 1 -set vid_fullscreen 2` - the same two
as the Quake launcher needs - and takes the map as arguments. `+wait` before
`+map` is not decoration here: measured on the seat, a bare `+map` queued
before the renderer starts leaves the game silent with a 6121-byte black
screenshot, while `+wait 3 +map oa_dm6` loads every time:

```
/usr/bin/openarena +wait 3 +map oa_dm6
  SpawnServer: oa_dm6   AAS initialized.
  top over the run:  VIRT 851264 KB  RES 120272 KB  SHR 16616 KB  %CPU 25.0 -> 61.5
  free -m while it ran: ~590 MB used of the seat's 1768 MB
  screenshot ~/.local/share/quake3/baseoa/screenshots/fte-20261111041046-0.png 1587779 B
```

The same round ran the map again with the launcher installed from the deb's own
bytes (the seat's tree is a verified `cp`: a `md5sum -c` of the deb's 11-file
manifest run from `/` answers OK for all eleven - the 403 MB deb plus its
extraction does not fit on a filesystem that is 91 % used with the 398 MB tree
already in place, so the deb is verified and relayed rather than installed
whole).

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
