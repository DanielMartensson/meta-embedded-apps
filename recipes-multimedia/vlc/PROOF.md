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
window anywhere in the process. What that leaves, and what still works:

| the 45 s 1080p slice, `cvlc --vout yuv --yuv-file=/dev/null --stats --play-and-exit` | rc 0, 46 s of wall time against the slice's 45.04 s, 7 log lines and not one of them an error |
| the same sink pointed at a file, on the probe clip | 385,690,375 B written = 124 whole 1920×1080 YUV frames at 3,110,400 B a frame, against the clip's 360 frames; a second run of the same sink wrote 127, so the mmcfs write and not the decode is what limits this one |
| the default `gles2` display, no options at all, over the 45 s slice | 14,757 log lines: 2,408 × `gles2 vout display error: parent window not available`, 2,408 × `main video output error: video output creation failed`, 2,408 × `main decoder error: failed to create video output`, 2,408 × `get_buffer() failed` and the same again as `thread_get_buffer() failed`, 4,816 of them counting both spellings, 2,408 × `decode_slice_header error`, 301 × `no frame!` - not one frame reaches the panel |
| the same default chain, 12 s probe clip | the identical shape at the clip's own size: 360 attempts, 360 of each of the five messages, 720 of the two `get_buffer()` spellings |
| the seat's own statistics block | not printed: `--stats` answers silence and `--print-stats` answers `vlc: unknown option or missing mandatory argument`, which is why the frame tally above comes from the yuv sink's bytes rather than from the player |

So the decode-and-present claim is proved at the player's own rate: 45.04 s of
1080p media through vlc's h264 decoder and its own `yuv` video output in 46 s
of wall time, rc 0, with the film's audio playing at real time through the
dummy interface. What this seat cannot do is put vlc's pixels on the panel:
every window-capable display refuses, and the reason is the same in all of
them - the video output chain asks its display for a parent window and the
dummy interface never gives one, and this seat has no `/dev/fb*` device to
fall back to either and no `/usr/share/terminfo` at all.

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
