# meta-embedded-apps

Yocto `scarthgap` layer: the applications that run on the seat. One screen long
on purpose -- everything that was measured, tried or patched lives in the detail
file next to the recipe that owns it. The games and the engines they run on live
in `meta-good-old-games`, and everything that is OS-level lives in
`layers/meta-watermelon-wine`.

## What is in this branch

| Application | Recipe | Upstream pin | Installed on the seat | What it does |
| --- | --- | --- | --- | --- |
| Discordo | `recipes-apps/discordo/discordo_git.bb` | [ayn2op/discordo](https://github.com/ayn2op/discordo) `31905f3e790e63cd0f2366526afe41fb278c226e` | `0.1+git0+31905f3e79-r0.01` | terminal Discord client in the seat's weston-terminal; 21 vendored Go modules, no network at build |
| NanoBrowser | `recipes-apps/nanobrowser/nanobrowser_git.bb` | [DanielMartensson/NanoBrowser](https://github.com/DanielMartensson/NanoBrowser) `b5e01869c10140799085a20d2c18cadb37c3f977` | `0.1+git${SRCPV}-r0.32` | minimal QML browser on QtWebEngine; `--page`, `NANOBROWSER_TIMING`, build default renderer OpenGL (measured 1.3 s to first frame against Vulkan's 3.1 s) |
| YtGst | `recipes-apps/ytgst/ytgst_git.bb` | [DanielMartensson/YtGst](https://github.com/DanielMartensson/YtGst) `9cb2a60806907c6ac91fa3924ea17f910f2172af` | `0.1+git${SRCPV}-r0.48` | YouTube client on GStreamer + yt-dlp with V4L2 hardware decode; picture in a Wayland subsurface of its own window, `YTGST_*` switches |
| VLC | `recipes-multimedia/vlc/vlc_3.0.20.bbappend` | upstream's own [vlc_3.0.20.bb](https://get.videolan.org/vlc/3.0.20/vlc-3.0.20.tar.xz) in `meta-multimedia`, tar `adc7285b4d2721cddf40eb5270cada2aaa10a334cb546fd55a06353447ba29b5` | `3.0.20-r0.0` + `libvlc 3.0.20-r0.0`, built and not yet installed (see the seat note below) | media player whose decode route is GStreamer, whose display half is Wayland + GL ES 2 and whose whole X half is switched off |

### Media payloads

The film that makes the player above a proof rather than an installation: a
1080p H.264 master is what shows the `--enable-gst-decode` route carries a real
picture to this board's panel at the seat's own 1920x1080.

| Payload | Recipe | Upstream pin | Installed on the seat | What it is |
| --- | --- | --- | --- | --- |
| Big Buck Bunny 1080p | `recipes-multimedia/bigbuckbunny/bigbuckbunny.bb` | [download.blender.org/peach/bigbuckbunny_movies/big_buck_bunny_1080p_h264.mov.zip](https://download.blender.org/peach/bigbuckbunny_movies/big_buck_bunny_1080p_h264.mov.zip) `402d6625f614631627d518b931865c9c4aefbbf3b75ed0494672e77ff510666d` | `1.0.0-r0.01`, built and not yet deployed (see the seat note below) | the Blender Foundation's open film, 725,106,140 B, one file, licensed CC BY 3.0 and pinned as a `LicenseRef` in the recipe |

### Bundled dependencies

Additional recipes shipped in this layer because one of the applications above
needs them.

| Recipe | Needed by | Why |
| --- | --- | --- |
| `recipes-multimedia/gstreamer/gstreamer1.0-plugins-good_%.bbappend` | YtGst | adds the Qt6 QML video sink (`qml6glsink`) |
| `recipes-support/yt-dlp/yt-dlp_2026.08.19.bb` | YtGst | runtime downloader |
| `recipes-qt/qt6/qtwebengine_git.bbappend` | NanoBrowser | keeps QtWebEngine buildable on aarch64 |

Nothing else is vendored here. The one Go application in this layer cross-compiles
against oe-core's own `go_1.22.12` and `go-runtime`, and YtGst runs on oe-core's
own GStreamer plus the yt-dlp recipe above. VLC brings no new recipe at all: the
three gstreamer packages its decode route names, and the ffmpeg packages its
`Depends` names, are already on the seat, and the film is one file with no
`Depends`. The only file the layer vendors for the pair is the film's
`files/LICENSE.txt`, the 657-byte CC BY 3.0 note `LIC_FILES_CHKSUM` checksums,
because a movie has no source-tree license file of its own.

### Patches on top of upstream

| Recipe | Patches applied | What they are for |
| --- | --- | --- |
| Discordo | none | pristine pin; its 21 Go dependencies are vendored as one tarball instead |
| NanoBrowser, YtGst | none | pristine upstream; only the two bbappends above are not pristine |
| VLC | none | upstream's recipe and its ten patch files are used as they are; the bbappend restates `REQUIRED_DISTRO_FEATURES`, `PACKAGECONFIG` and `DEPENDS` and adds `--enable-gles2 --disable-xvideo --disable-lua` |
| Big Buck Bunny | one file, not a patch: `files/LICENSE.txt` | the CC BY 3.0 note the recipe's `LicenseRef` checksums against; the film carries no license file of its own |

## Dependencies

### Companion layers

These layers must be present on `BBLAYERS` to build this layer:

| Layer | Provides |
| --- | --- |
| `openembedded-core`, `meta-oe`, `meta-python`, `meta-multimedia` | Base BSP / package recipes; `meta-multimedia` is where upstream's `vlc_3.0.20.bb` lives, so VLC is only possible with it on `BBLAYERS` |
| `meta-qt6` | Qt 6.8 (`qtbase`, `qtwebengine`, `qtshadertools`, …) - NanoBrowser |
| `meta-clang` | Clang toolchain (required by QtWebEngine on aarch64) |

---

## Prerequisites

- A Yocto `scarthgap` environment with the companion layers above activated
  (ST BSP manifest pulls them in automatically).
- **Network access** during the build: GitHub for the sources fetched by
  `SRC_URI`, `get.videolan.org` for VLC's source tarball and
  `download.blender.org` for the film's carrier.

---

## Getting started

Add the layer to `BBLAYERS` and build the applications:

```bash
bitbake discordo nanobrowser ytgst
```

VLC and the film it plays build together, and VLC pulls the GStreamer stack its
decode route names - already on this board, so that build adds no new packages
to the seat:

```bash
bitbake vlc bigbuckbunny
```

To build everything into the reference Weston image:

```bash
bitbake st-image-weston
```

Results are the regular root filesystem archives / images, e.g.
`tmp-glibc/deploy/images/…`.

## Installing on the seat

A package from this layer is built as an `arm64` `.deb` in
`tmp-glibc/deploy/deb/cortexa35/` and installed on the seat with `dpkg -i`: the
seat is the machine the deb was built for, so nothing is rebuilt there and no
reboot is needed. This board's build host cannot ssh to the seat, so the deb is
relayed through the local machine. Seat-side records live in
`layers/meta-watermelon-wine`.

The film is the exception, and the numbers say why: its deb unpacks 725,106,140
bytes, against a rootfs that measured 231,616 kB free, so `dpkg -i` of that
deb is not attempted at all. The member is relayed as the file it already is,
to `/gamedata/media/bigbuckbunny/` on the seat's 8 GB writable mount, and
verified there against the member's sha256. VLC's two debs, at 2,299,708 and
430,968 bytes, do go through `dpkg -i`.

## Where the details live

| File | What is in it |
| --- | --- |
| `recipes-apps/discordo/README.md` | why the pin is not HEAD, the 21 vendored modules and their versions, and what the client is on the seat |
| `recipes-apps/nanobrowser/README.md` | how to drive the browser on the seat, its switches as the installed binary answers them, and the OpenGL-against-Vulkan page timings |
| `recipes-apps/ytgst/README.md` | how to drive the YouTube client, its `YTGST_*` switches, what its window and subsurface are, and the panel timings that made OpenGL the shipped renderer |
| `recipes-multimedia/vlc/README.md` | why the player is a bbappend of upstream's recipe rather than one of this layer, what upstream asks for that this board does not have, the deb's measured size, hash and plugin tree, and how to run `cvlc` on the seat |
| `recipes-multimedia/bigbuckbunny/README.md` | the carrier's and the member's sizes and sha256s, the CC BY 3.0 note and the two build answers the payload needed, and how the film gets to the seat's writable mount without a `dpkg -i` |

This layer keeps no `PROOF.md` files: where the games layer splits its long
record into a `PROOF.md` next to each engine, the measurements of these
applications live in the `README.md` beside the recipe that owns them, and the
games layer's `PROOF.md` files stay in that layer.

The OpenNOW clients, the game engines and the games themselves live in
`meta-good-old-games`, whose `scarthgap` branch carries the long record of
every game round, including the OpenNOW round log and the seat's troubleshooting
table.

---
