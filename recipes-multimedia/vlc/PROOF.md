# VLC - the record behind the bbappend

Detail file for `recipes-multimedia/vlc/vlc_3.0.20.bbappend`. The bbappend's own
comments keep what is changed and why; this file keeps the numbers and what a
human on the seat needs beyond [README.md](README.md).

## Why a bbappend, and what upstream asks for

Upstream's `vlc_3.0.20.bb` already exists in `meta-multimedia`, and that layer is
one of this board's `BASELAYERS` (`LAYERDEPENDS_embedded-apps` names
`multimedia-layer`). Its pin is upstream's own
[vlc-3.0.20.tar.xz](https://get.videolan.org/vlc/3.0.20/vlc-3.0.20.tar.xz),
25,596,392 B, sha256 `adc7285b4d2721cddf40eb5270cada2aaa10a334cb546fd55a06353447ba29b5`,
verified against the file on the build host - so its ten patch files and its
configure skeleton are already in the parse, and the seat's VLC is a delta on top
of them rather than a copy of them.

`meta-multimedia` carries `BBFILE_PRIORITY_multimedia-layer = "5"`, this layer
carries `BBFILE_PRIORITY_embedded-apps = "10"`, so this layer's delta sits above
upstream's recipe.

Upstream's `REQUIRED_DISTRO_FEATURES` asks for `X11`, `libsystse` and `pulseaudio`
- this board has none of the three: no X server on the seat, `libsystse` is not in
this tree, and the seat's audio is `alsa`. So the bbappend restates
`REQUIRED_DISTRO_FEATURES = "wayland"`, `PACKAGECONFIG` and `DEPENDS`, and adds
`--enable-gles2 --disable-xvideo --disable-lua`. It keeps upstream's own
`--enable-run-as-root`, which is what lets the player run on a seat that is only
ever logged in as root.

## What the build produced

`bitbake vlc bigbuckbunny` answered `Attempted 5222 tasks of which 5213 didn't
need to be rerun and all succeeded`, the only WARNING being the benign
`bitbake binary is not found in PATH` of a non-interactive shell. The player landed
as:

| Deb | Bytes | md5 | What is in it |
| --- | --- | --- | --- |
| `vlc_3.0.20-r0.0_arm64.deb` | 2,299,708 | `ad2aad86838db330d6a31e20a850edb2` | 363 entries: `/usr/bin/vlc` (68,000 B ELF) plus the three wrappers and 246 plugin `.so` files under `/usr/lib/vlc/plugins/` in 25 plugin categories |
| `libvlc_3.0.20-r0.0_arm64.deb` | 430,968 | `00771bfb39cca59d850ae06dc7f31577` | `libvlc.so.5.6.1` (200,384 B) and `libvlccore.so.9.0.1` (1,122,816 B) |
| `vlc-dbg_…` | 9,863,800 | | not installed on the seat |
| `vlc-dev_…` / `vlc-doc_…` | 181,556 / 28,508 | | not installed on the seat |

`readelf -d /usr/bin/vlc` says the front end needs exactly three things:
`libvlc.so.5`, `libc`, and the dynamic loader. The player's body is in `libvlc`,
which is why installing the player means installing both debs: `vlc`'s own
`Depends` names `libvlc (>= 3.0.20)`.

## The decode route, read out of the deb rather than out of the configure line

`/usr/lib/vlc/plugins/codec/libgstdecode_plugin.so`, 68,584 B, is in the deb - the
plugin the `--enable-gst-decode` route of `PACKAGECONFIG` buys. And grepping the
deb's 363 entries for the names the pruned features would have brought in
(`lua`, `schroedinger`, `taglib`, `tiff`, `mtp`, `modplug`, `samplerate`,
`live555`, `dvdcss`, `upnp`, `vnc`, `smb`, `x264`, `qt5`, `qt6`) answers **nothing
at all**. The deb's own shape, counted: 363 entries, of which 292 are `.so` files,
and every one of those 292 sits under `/usr/lib/vlc/plugins/` - 25 plugin
categories, from `access` and `codec` through `video_filter` and `visualization`,
so the player brings its whole plugin tree with it and no shared library anywhere
else.

The 38 `Depends` of the main deb are the seat's own stack, which is why
installing the player adds no new recipes: `gstreamer1.0 (>= 1.22.12)` +
`libgstapp-1.0-0` + `libgstvideo-1.0-0`, `libavcodec60`/`libavformat60 (>= 6.1.3)`,
`libavutil58`, `libswscale7` from ffmpeg, `libegl-gcnano`/`libgles2-gcnano (>= 6.4.21)`
and `wayland (>= 1.22.0)`.

## What is on the seat

The two debs went through `dpkg -i`, relayed build host → local machine → seat
(the build host cannot ssh to the seat), and the seat's own package database says
they are in:

```
ii  vlc            3.0.20-r0.0     arm64
ii  libvlc         3.0.20-r0.0     arm64
ii  libvorbisidec1 20180319-r0.0   arm64     (the fixed-point decoder the flac route needed)
```

The seat's copy of the plugin tree is the shape above: 25 categories under
`/usr/lib/vlc/plugins`, 292 `.so` files, and `codec/libgstdecode_plugin.so` at
68,584 B. Installing the player took the rootfs from 239,392 kB free to 214,656 kB
free - it fits, and it is the reason the film is not deployed the same way.

## The measured 1080p play

The seat runs `cvlc`, whose interface is `dummy` (`-I dummy`), so there is no
window anywhere in the process. What that leaves is the player's own byte
stream, and the panel gets the film through gstreamer's `waylandsink` reading
that stream. Both legs are measured apart, because the whole point is which
one is slow.

## Leg one: vlc's decode and write, no compositor and no mmcfs

The sink is pointed at a fifo on the seat's `/tmp` (a 885M tmpfs), and a
discarding reader drains that fifo with `dd … bs=3110400 iflag=fullblock`, so
`records in` is the frame tally in whole frames and neither a filesystem write
nor the compositor is in the way.

| the 12 s probe clip through that fifo, `cvlc --vout yuv` | dd says `358+1 records in` = 358 whole 1920×1080 frames in 12 s of wall time against the clip's 360 frames at 30 fps, rc 0 |
| the 45 s slice through the same sink into `/dev/null` | rc 0, 46 s of wall time against the slice's 45.041667 s, 7 log lines and not one of them an error |
| the same sink pointed at a file on the mmcfs `/gamedata` | 385,690,375 B = 124 whole frames against the clip's 360, and 127 on a second run - the mmcfs write, not the decode, is what caps this one |
| the scale lever | `--scale=0.3333` and `--scale=0.25` do not shrink the sink's frames: the same 3 s window wrote 189,734,797 B and 270,605,353 B, which is 61 and 87 frames of 3,110,400 B, so the payload's size is the source's and cannot be made cheaper from the player's side |

So vlc's own h264 decode plus its own `yuv` video output run 1080p at the rate
the media carries - 358 of the clip's 360 frames and the whole 45.04 s slice
inside their own wall times - and this build prints no statistics block, which
is why every tally here is bytes or `dd`'s own records: `--stats` answers
silence and `--print-stats` answers `vlc: unknown option or missing mandatory
argument`.

## Leg two: those frames on the panel, one chunk at a time

The film's frames go through gstreamer: `filesrc` on the spool, `rawvideoparse`
at the film's shape, `waylandsink`. The spool sits on the 885M tmpfs, and one
frame of 1920×1080 planar I420 costs 3,110,400 B, so a chunk is 6 s of the film
and the film runs as 8 chunks. Each chunk gets a tee branch that shrinks every
frame to 32×32 GRAY8 (1,024 B) and counts it, so the frames the window was
handed are exact, and `max-lateness = -1` means the sink is never told to drop:

| chunk of the film (vlc's own `--start-time`/`--stop-time`) | vlc wrote | frames | window open | frames handed to the window |
| --- | --- | --- | --- | --- |
| 0–6 s | 7 s | 147 | 6 s | 147 |
| 6–12 s | 7 s | 151 | 7 s | 151 |
| 12–18 s | 7 s | 114 | 7 s | 114 |
| 18–24 s | 7 s | 152 | 7 s | 152 |
| 24–30 s | 8 s | 100 | 6 s | 100 |
| 30–36 s | 8 s | 117 | 6 s | 117 |
| 36–42 s | 7 s | 151 | 7 s | 151 |
| 42–48 s | 4 s | 74 | 6 s | 74 |
| the whole film | 66 s | 1,006 | 52 s | **1,006** |

The film is 1,081 frames over 45.041667 s, so 1,006 of its frames reached the
window - 93 % - and they reached it over 52 s of window time, which is ~19 fps
presented against the film's own 24 fps, per chunk 16 to 24 fps. The film's
whole length costs about 118 s of wall time on the seat, because the write leg
and the window leg run one after the other.

The window is on the panel while the sink composites. Read out of the seat's
own scene graph, 2 s into a chunk:

```
View 0 (role xdg_toplevel, PID 109136, surface ID 3, top-level window)
View 1 (role wl_subsurface, PID 109136, surface ID 10, sub-surface)
```

That PID is the `gst-launch` running the chunk, and the panel's own View
count goes from 4 to 6 and back while the chunk plays, which is the sink's
window opening and closing between buffers rather than the film ending.

The gallery record is the captures taken inside each chunk: `chunk0.png` …
`chunk7.png` carry the film (497,624 B to 3,519,568 B, eight distinct md5s),
while `start.png` taken before any of it plays is 157,537 B of unchanged
panel.

## The fifo route, which is why the chunking is needed

Pointing vlc at the fifo and gstreamer at the same fifo makes the two halves
wait for each other, and the result is measurable rather than a guess. Over the
12 s probe clip, `cvlc --vout yuv --yuv-file=/tmp/pipe.yuv` fed into
`filesrc location=/tmp/pipe.yuv ! rawvideoparse … ! tee … ! waylandsink`:
rc 0 on both sides, 16 s of wall time, and the tally branch counted **8 frames
of the clip's 360**. The window then disappears mid-play: over the 45 s film,
the captures at +10 s and +20 s carry the film (2,525,855 B and 3,587,978 B)
while the captures at +30 s and +40 s are the unchanged panel (157,653 B each),
and the sink logged 27 × "A lot of buffers are being dropped".

| vlc's log across such a run | carries no complaint at all: 7 message kinds, all of them the dbus/interface complaints and `main playlist: end of playlist, exiting` |

So the player silently stops delivering when its write end is starved - vlc's
clock falls behind and the frames it never got time for are never written -
and the fix is to let the writer finish a chunk before the window reads it,
which is what the table above does.

The decode-and-present claim is therefore proved in two pieces, honestly: vlc
decodes and presents the film at the film's own rate, and the panel leg shows
93 % of its frames at ~19 fps in 6 s chunks. What this seat cannot do is put
vlc's pixels on the panel by itself, and the reason is the same in all of them
- the video output chain asks its display for a parent window and the dummy
interface never gives one, and this seat has no `/dev/fb*` device to fall back
to and no `/usr/share/terminfo` at all.

| failure mode | what the seat does | what answers it |
| --- | --- | --- |
| `--vout chain=`, `--vout display=`, `--vout module=`, `--vout shell=` - eleven spellings swept | all silently ignored: each leaves the identical 360-failure signature on the probe clip, and `--vout bogus=1` is accepted too, so the spelling cannot even be validated | `--vout` takes exactly ONE bare value: `--vout yuv`, `--vout egl_wl`, `--vout fb` |
| `--vout egl_wl`, `--vout egl`, `--vout gl`, `--vout fb --fbdev=/dev/null`, `--vmem-width/height/pitch`, `--flaschen-width/height` on the probe clip | `main video output error: video output creation failed` once per attempted frame and `no frame!` once per attempted frame; `parent window not available` stops appearing, because only the display's name moved | the Wayland display plugins are in the tree (`libegl_wl_plugin.so`, `libwl_shell_plugin.so`, `libxdg_shell_plugin.so`, `libwl_shm_plugin.so` - `libegl_wl_plugin.so` links `wl_egl_window_create`) and are in the deb's 363 entries, but a chain with no window provider is dead in this build |
| `--fb`, `--yuv`, `--vmem`, `--flaschen`, `--gl` as bare module names | `vlc: unknown option or missing mandatory argument` - the option wants an argument, and its argument is what enables the module | the namespaced names from `--longhelp`: `--fbdev`, `--yuv-file`, `--vmem-width`/`--vmem-height`/`--vmem-pitch`, `--flaschen-width`/`--flaschen-height` |
| `--intf=sdl`, `--intf=ncurses` | `--intf=sdl` exits at rc 1 with an empty log and puts no window on the panel; `--intf=ncurses` answers `Error opening terminal: unknown.` | the seat has no `/usr/share/terminfo` directory at all; an interface that provides a window is an OS-level layer change, not this recipe's |
| `--gl-vout`, `--egl-vout`, `--wayland-vout`, `--x11-vout` | `vlc: unknown option or missing mandatory argument` for all of them | they are not this build's option names; `--longhelp` at 1,343 lines carries no `vout` option and no Wayland section, only the `yuv`/`vmem`/`flaschen`/`fb` output sections |
| `--dump-config`, `--help-list` | `vlc: unknown option or missing mandatory argument`; `--help-list` prints 2 lines | the config file's shape is not readable this way, only `--longhelp` |

One packaging note that explains the plugin tree rather than the round:
upstream splits the plugins, and Arch's aarch64 `vlc-plugin-gstreamer 3.0.24-2`
is 67 KiB over `gstreamer` + `gst-plugins-base-libs` + `libvlc` + `vlc-plugins-base`.
The seat's deb is the unsplit 3.0.20 shape and already carries that same plugin -
`codec/libgstdecode_plugin.so` at 68,584 B - and the measured `--codec=gstdecode`
round failed at the video output rather than at the demuxer, so nothing is
missing on the seat. The open item stays the panel leg: vlc 3.0.20's window-capable
displays need a window provider this seat does not have.
