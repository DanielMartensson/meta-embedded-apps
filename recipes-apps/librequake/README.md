# LibreQuake - the Quake data the FTEQW launcher mounts, and its record

Detail file for `recipes-apps/librequake/librequake.bb`, kept out of the
layer's own README so that README stays one screen long: what the payload
actually carries, what the seat played with it and what that cost.

## What the payload actually is

The whole tree arrives as the release's own `full.zip` (pinned at the tag
`v0.09-beta`, sha256 `623e463b35811216244f9ba15e0c45abc1765288650b5e3d36a899b35e4bcf3d`, 115678161 B) and unpacks under
`${WORKDIR}/full`; the recipe installs `full/id1` as `/usr/share/librequake/id1`
unchanged - `id1` is the Quake protocol's own name for the game directory, so
the launcher pairs `-basedir /usr/share/librequake -game id1` and no symlink
is involved anywhere. Unpacked that is 252557855 B, 63 files
(the deb payload manifest md5 `f32912dd562e45f075119cffc777f9d3` on the build
host, 242 MB of it on the seat), and inside `id1` it is:

- `pak0.pak` 94730968 B and `pak1.pak` 100635264 B - the rebuilt assets;
- `maps/lqdm1..lqdm13` as loose `.bsp`/`.lit`: thirteen deathmatch maps, and
  that is the whole map list - `+listmaps` on the seat prints `lqdm1..lqdm13`
  only. There is no campaign and no demos in this payload: the single-player
  side is absent, so the honest description of this data is "deathmatch only".
- `music/track02..11.ogg`, `progs.dat` (the multiplayer code the QCC progs run
  through - this is why the launcher carries `pr_ssqc_memsize`), `docs/`
  (the COPYING pair, `README-IMPORTANT-LICENCE-INFO`, the `style_*.cfg` set)
  and `dev.cfg`, `zoom90.cfg`, `zoom120.cfg` as loose configs.

## The licensing, as the repo states it

There is no one-license file at the repository root. Inside the zip, `id1/docs/COPYING`
is the BSD-3-Clause text the project puts on its models/textures/sounds (md5
`da773883bf8745d2637b3a191dd24fa5`), and `id1/docs/misc-docs/COPYING` is the GPL v2
that covers the quakec progs (`progs.dat`, `pop.lmp`) - its md5 `94d55d512a9ba36caa9b7df079bae19f`
is GNU's own v2 text - and `docs/README-IMPORTANT-LICENCE-INFO` spells that split
out. The recipe states `BSD-3-Clause & GPL-2.0-only` because that is what the
payload is, and `check-license` resolves both `file://` names against `S`.

## How the seat plays it, and what it cost

The launcher that mounts this tree is the fteqw recipe's `/usr/bin/fteqw` -
it is the default pair there (`-basedir /usr/share/librequake -game id1 -quake`),
so on the seat `/usr/bin/fteqw +wait 3 +map lqdm3` is a complete game command,
and how the launcher and its `FTEQW_ARGS` set work is in `../fteqw/README.md`.
The round measured on Nov 11 2026:

```
/usr/bin/fteqw +wait 3 +map lqdm3
  +status over the running game:
    current map : lqdm3 (Hyperborea)
    entities : 60/60/131072 (mem: 3.2%)
    models 50/16384   sounds 79/4096   gamedir : qw
  top over the run:  VIRT 620080 KB  RES  55672 KB  SHR 10832 KB  %CPU 0.0 -> 16.7
  free -m while it ran: ~240-277 MB used of the seat's 1768 MB
  screenshot of a rendered world: ~/.local/share/quake/fte/screenshots/
    fte-20261111025415-0.png, 128171 B
```

The tree the seat carries today is a `cp` of the same payload rather than a
`dpkg -i` of its deb: with the 242 MB already in place the seat's own
filesystem answers 91 % used, ~404 MB free, and a 252 MB deb plus its
extraction does not fit beside the tree it replaces. The copy was verified
against the deb's own payload instead: all 63 files answer `OK` to a `md5sum
-c` of the deb's manifest run from `/`, so the bytes the recipe installs and
the bytes the seat plays are one and the same.
