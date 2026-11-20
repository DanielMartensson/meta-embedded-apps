# VLC - the media player, and how it reaches a seat that has no X

Detail file for `recipes-multimedia/vlc/vlc_3.0.20.bbappend`. The recipe's own
comments keep what is changed and why; this file keeps what a human on the seat
needs to run the player and what the build was measured to do.

## Why a bbappend, and not a recipe of this layer

Upstream's `vlc_3.0.20.bb` already exists in `meta-multimedia`, and that layer
is one of this board's `BASELAYERS` (`LAYERDEPENDS_embedded-apps` names
`multimedia-layer`). Its pin - upstream's own
[vlc-3.0.20.tar.xz](https://get.videolan.org/vlc/3.0.20/vlc-3.0.20.tar.xz), 25,596,392 B,
sha256 `adc7285b4d2721cddf40eb5270cada2aaa10a334cb546fd55a06353447ba29b5`, verified
against the file on the build host - its ten patch files and its configure
skeleton are therefore already in the parse, so the seat's VLC is a delta on
top of them rather than a copy of them.

The direction of that fold is unambiguous: `meta-multimedia` carries
`BBFILE_PRIORITY_multimedia-layer = "5"`, this layer carries
`BBFILE_PRIORITY_embedded-apps = "10"`, so this layer's delta sits above
upstream's recipe rather than beside it. The run log says the same thing
directly - the recipe it executes is
`…/meta-openembedded/meta-multimedia/recipes-multimedia/vlc/vlc_3.0.20.bb`,
with this file folded in.

The one thing upstream gets wrong for this board is the whole reason the
player never lands: upstream asks `REQUIRED_DISTRO_FEATURES = "x11"`, and this
seat has no X server at all - its desktop is Weston - so upstream-shape the
recipe is skipped and there is no player to install.

## What changes, in one table

| Upstream | This seat | Why |
| --- | --- | --- |
| `REQUIRED_DISTRO_FEATURES = "x11"` | `= "wayland"` | the seat's desktop is Weston; there is no X to have |
| `PACKAGECONFIG ?=` upstream's default list | `PACKAGECONFIG = "gstreamer fontconfig freetype png wayland"`, stated strong | a strong set beats upstream's `?=`, so the optional half (Qt UI, live555, dvd/schroedinger, samba/upnp/VNC inputs, x264, gtk+3 notify, taglib, tiff, orc, lua, …) is never pulled at all; `fontconfig`/`freetype` stay because the menu and the subtitles draw text, `png` because the snapshot filter writes one |
| `DEPENDS` carrying that same optional half | restated to the unconditional half only | a `DEPENDS` entry that `PACKAGECONFIG` switched off is still a recipe BitBake pulls, so the two halves move together |
| `--enable-xvideo`, enabled unconditionally | `EXTRA_OECONF:append = " … --disable-xvideo"` | upstream turns the Xvideo output module on whatever the display backend is; with `--without-x` it would go looking for an X that is not here |
| lua on by default | `--disable-lua` and lua out of `DEPENDS` | the same pair, moved together |
| no GL ES 2 | `--enable-gles2` | the context this board's panel really carries: every engine on the seat runs `vid_gl_context_es 1` |
| `--enable-run-as-root` | kept, untouched | this seat runs every Weston app as root with `HOME=/home/weston`, all the engines included |

## Measured on the build side

`bitbake -e vlc` answers for the final configure tail, in the order it is
actually passed:

```
… --enable-gst-decode … --enable-wayland --without-x --disable-xcb
   --disable-x264 --enable-nls --enable-gles2 --disable-xvideo --disable-lua
PACKAGECONFIG="gstreamer fontconfig freetype png wayland"
REQUIRED_DISTRO_FEATURES="wayland"
```

`--enable-xvideo` and `--disable-xvideo` are both in that tail - upstream's
enable comes first, this layer's disable comes last - which is what makes the
bbappend's shape provable rather than assumed: the switch that matters is the
later one.

`bitbake vlc bigbuckbunny` then answered `Attempted 5222 tasks of which 5213
didn't need to be rerun and all succeeded`, and the only WARNING was the
benign `bitbake binary is not found in PATH` of a non-interactive shell. The
player landed as:

| Deb | Bytes | md5 | What is in it |
| --- | --- | --- | --- |
| `vlc_3.0.20-r0.0_arm64.deb` | 2,299,708 | `ad2aad86838db330d6a31e20a850edb2` | 363 entries: `/usr/bin/vlc` (68,000 B ELF) plus the three wrappers and 246 plugin `.so` files under `/usr/lib/vlc/plugins/` in 25 plugin categories |
| `libvlc_3.0.20-r0.0_arm64.deb` | 430,968 | `00771bfb39cca59d850ae06dc7f31577` | `libvlc.so.5.6.1` (200,384 B) and `libvlccore.so.9.0.1` (1,122,816 B) |
| `vlc-dbg_…` | 9,863,800 | | not installed on the seat |
| `vlc-dev_…` / `vlc-doc_…` | 181,556 / 28,508 | | not installed on the seat |

`readelf -d /usr/bin/vlc` says the front-end needs exactly three things:
`libvlc.so.5`, `libc`, and the dynamic loader. The player's body is in `libvlc`,
which is why installing the player means installing both debs: `vlc`'s own
`Depends` names `libvlc (>= 3.0.20)`.

The decode route is visible inside the deb, not only in the configure line:
`/usr/lib/vlc/plugins/codec/libgstdecode_plugin.so`, 68,584 B, is in the deb,
and grepping the deb's 363 entries for the names the pruned features would
have brought in (`lua`, `schroedinger`, `taglib`, `tiff`, `mtp`, `modplug`,
`samplerate`, `live555`, `dvdcss`, `upnp`, `vnc`, `smb`, `x264`, `qt5`, `qt6`)
answers **nothing at all**. The deb's own shape, counted: 363 entries, of
which 292 are `.so` files, and every one of those 292 sits under
`/usr/lib/vlc/plugins/` - 25 plugin categories, from `access` and `codec`
through `video_filter` and `visualization`, so the player brings its whole
plugin tree with it and no shared library anywhere else.

The 38 `Depends` of the main deb are the seat's own stack, which is why
installing the player adds no new recipes: `gstreamer1.0 (>= 1.22.12)` +
`libgstapp-1.0-0` + `libgstvideo-1.0-0`, `libavcodec60`/`libavformat60 (>= 6.1.3)`,
`libavutil58`, `libswscale7` from ffmpeg, `libegl-gcnano`/`libgles2-gcnano (>= 6.4.21…)`,
and `wayland (>= 1.22.0)`.

## How to use it on the seat

The three front-ends are one binary behind three two-line scripts, and what
differs is only the interface:

```sh
exec /usr/bin/vlc -I "dummy" "$@"     # cvlc,  45 B
exec /usr/bin/vlc -I "ncurses" "$@"   # nvlc,  47 B
exec /usr/bin/vlc -I "rc" "$@"        # rvlc,  42 B
```

`cvlc` is the one to run here: it asks for the `dummy` interface, so nothing
tries to open a UI on a seat whose desktop is a bare Weston session. Plain
`vlc` would answer `Running vlc with the default interface. Use 'cvlc' to use
vlc without interface.` (`src/interface/interface.c:214`, when the `intf`
variable is unset).

The run needs the seat's own Weston environment, as every engine on this seat
needs it:

```sh
HOME=/home/weston XDG_RUNTIME_DIR=/home/weston WAYLAND_DISPLAY=wayland-1 \
  cvlc /gamedata/media/bigbuckbunny/big_buck_bunny_1080p_h264.mov
```

A still of what is on the panel is taken the way every other round on this
seat takes one - `XDG_PICTURES_DIR=<dir> weston-screenshooter`, and the
capture's bottom 32 rows are the panel and get cropped.

## What is on the seat

Nothing yet. The seat was off the network across this round (`ping` answers
`Destination Host Unreachable` from the router node, `ssh` gives `No route to
host`), so the debs above are measured on the build host only and the install
has not run. The step that still has to happen, once the seat answers again:

```sh
# relayed build host -> local machine -> seat; the build host cannot ssh to the seat
dpkg -i vlc_3.0.20-r0.0_arm64.deb libvlc_3.0.20-r0.0_arm64.deb
```

One thing that step has to be checked rather than assumed: this seat's
`dpkg` is BusyBox-era and has no `--ignore-dependencies`, so if a `Depends`
entry above is not already on the seat the install needs the
`--force-depends` equivalent verified on the board. The candidate to check is
`wayland (>= 1.22.0)`, the one entry of that list not yet confirmed against a
`dpkg -l` on the seat.
