# meta-embedded-apps

Yocto `scarthgap` layer: the applications that run on the seat. One screen long
on purpose -- everything that was measured, tried or patched lives in the detail
file next to the recipe that owns it. The games and the engines they run on live
in `meta-good-old-games`, and everything that is OS-level lives in
`layers/meta-watermelon-wine`.

Every upstream source below is pinned to a commit or a tarball, and the pin's exact
hash lives in the recipe's `SRC_URI` - and in the recipe's `PROOF.md` where that file
exists - rather than in this table: hashes do not belong on the screen this file is
meant to be read from.

## What is in this branch

| Application | Recipe | Upstream pin | Installed on the seat | What it does |
| --- | --- | --- | --- | --- |
| Discordo | `recipes-apps/discordo/discordo_git.bb` | [ayn2op/discordo](https://github.com/ayn2op/discordo), pinned to a commit | installed | terminal Discord client in the seat's weston-terminal; 21 vendored Go modules, no network at build |
| NanoBrowser | `recipes-apps/nanobrowser/nanobrowser_git.bb` | [DanielMartensson/NanoBrowser](https://github.com/DanielMartensson/NanoBrowser), pinned to a commit | installed | minimal QML browser on QtWebEngine; `--page`, `NANOBROWSER_TIMING`, build default renderer OpenGL (measured 1.3 s to first frame against Vulkan's 3.1 s) |
| YtGst | `recipes-apps/ytgst/ytgst_git.bb` | [DanielMartensson/YtGst](https://github.com/DanielMartensson/YtGst), pinned to a commit | installed | YouTube client on GStreamer + yt-dlp with V4L2 hardware decode; picture in a Wayland subsurface of its own window, `YTGST_*` switches |
| VLC | `recipes-multimedia/vlc/vlc_3.0.20.bbappend` | upstream's own [vlc_3.0.20.bb](https://get.videolan.org/vlc/3.0.20/vlc-3.0.20.tar.xz) in `meta-multimedia`, which this layer only appends | installed: `vlc` and `libvlc`, both at `3.0.20-r0.0` | media player whose decode route is GStreamer, whose display half is Wayland + GL ES 2 and whose whole X half is switched off |
| vlc-play | `recipes-multimedia/vlc-play/vlc-play_1.0.0.bb` | none: this layer's own launcher script, vendored as `files/vlc-play` | installed: `vlc-play` | plays a film on the seat's panel at the film's own rate: gstreamer's `waylandsink` does the presenting and the choice of decoder is read off the file with `ffprobe`, so an h264 film goes to this board's own H.264 block and everything else to gstreamer's software chain, with `--sw` and `--vlc` forcing those two |

### Media payloads

The film that makes the player above a proof rather than an installation: a
1080p H.264 master is what shows the decode route carries a real picture to
this board's panel at the seat's own 1920x1080. **The film is that example and
nothing more** - it is not part of the seat's shelf, and what the seat-side
proof needs of it is a slice, relayed and then deleted.

| Payload | Recipe | Upstream pin | How it gets to the seat | What it is |
| --- | --- | --- | --- | --- |
| Big Buck Bunny 1080p | `recipes-multimedia/bigbuckbunny/bigbuckbunny.bb` | [download.blender.org/peach/bigbuckbunny_movies/big_buck_bunny_1080p_h264.mov.zip](https://download.blender.org/peach/bigbuckbunny_movies/big_buck_bunny_1080p_h264.mov.zip), pinned to its sha256 | the member is relayed to `/gamedata/media`, never `dpkg -i` | the Blender Foundation's open film, one file, licensed CC BY 3.0 and pinned as a `LicenseRef` in the recipe |

### Bundled dependencies

Additional recipes shipped in this layer because one of the applications above
needs them.

| Recipe | Needed by | Why |
| --- | --- | --- |
| `recipes-multimedia/gstreamer/gstreamer1.0-plugins-good_%.bbappend` | YtGst | adds the Qt6 QML video sink (`qml6glsink`) |
| `recipes-support/yt-dlp/yt-dlp_2026.08.19.bb` | YtGst | runtime downloader |
| `recipes-qt/qt6/qtwebengine_git.bbappend` | NanoBrowser | keeps QtWebEngine buildable on aarch64 |

Nothing else is vendored here, and the launcher is the one exception to "upstream
somewhere else": `vlc-play` installs a script this layer wrote, so its recipe
fetches nothing. The other file the layer vendors is the film's
`files/LICENSE.txt`, the CC BY 3.0 note `LIC_FILES_CHKSUM` checksums, because a
movie has no source-tree license file of its own. VLC brings no new recipe at all:
the three gstreamer packages its decode route names, and the ffmpeg packages its
`Depends` names, are already on the seat, and the film is one file with no
`Depends`. The launcher's `RDEPENDS` name that same seat stack rather than
asking for new packages - the two split packages of `gstreamer1.0-plugins-bad`
that carry the panel sink and the board's H.264 decoder, plus `vlc`, `ffmpeg`
and `coreutils` for the tools its routes call.

### Patches on top of upstream

| Recipe | Patches applied | What they are for |
| --- | --- | --- |
| Discordo | none | pristine pin; its 21 Go dependencies are vendored as one tarball instead |
| NanoBrowser, YtGst | none | pristine upstream; only the two bbappends above are not pristine |
| VLC | none | upstream's recipe and its ten patch files are used as they are; the bbappend restates `REQUIRED_DISTRO_FEATURES`, `PACKAGECONFIG` and `DEPENDS` and adds `--enable-gles2 --disable-xvideo --disable-lua` |
| vlc-play | none - there is nothing upstream to patch | the recipe installs `files/vlc-play`, the layer's own script, into `${bindir}` |
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
  `download.blender.org` for the film's carrier. The launcher fetches nothing,
  so a build of it alone needs no network at all.

---

## Getting started

Add the layer to `BBLAYERS` and build the applications:

```bash
bitbake discordo nanobrowser ytgst
```

VLC, the film it plays and the launcher that puts a film on the panel build
together. The launcher adds one file to the seat and asks for no new packages
underneath, since the gstreamer stack and the ffmpeg tools it calls are already
on this board:

```bash
bitbake vlc bigbuckbunny vlc-play
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

The film is the exception, and the reason is space rather than licence: its deb
unpacks the whole 1080p master into `/usr/share`, against a rootfs that has
kilobytes of room left rather than megabytes, so `dpkg -i` of that deb is not
attempted at all. The member is relayed as the file it already is, to the seat's
own writable `/gamedata` mount, and verified there against the member's sha256 -
the numbers of both ends are in
[`recipes-multimedia/bigbuckbunny/PROOF.md`](recipes-multimedia/bigbuckbunny/PROOF.md).
VLC's two debs and the launcher's one do go through `dpkg -i`, and the seat's
package database says so once they are in.

## Where the details live

| File | What is in it |
| --- | --- |
| `recipes-apps/discordo/README.md` + `PROOF.md` | README: how to build and run the client on the seat. PROOF: why the pin is not HEAD, the 21 vendored modules and their versions, and what the seat has |
| `recipes-apps/nanobrowser/README.md` | how to drive the browser on the seat, its switches as the installed binary answers them, and the OpenGL-against-Vulkan page timings |
| `recipes-apps/ytgst/README.md` | how to drive the YouTube client, its `YTGST_*` switches, what its window and subsurface are, and the panel timings that made OpenGL the shipped renderer |
| `recipes-multimedia/vlc/README.md` + `PROOF.md` | README: how to build and run the player on the seat. PROOF: why it is a bbappend of upstream's recipe, what upstream asks for that this board does not have, the debs, the plugin tree's counts, the interface sweep against the seat's own terminfo, the measured 1080p play and the failure modes of both routes |
| `recipes-multimedia/vlc-play/README.md` + `PROOF.md` | README: how to build the launcher and what its switches do on the seat. PROOF: why the launcher is this layer's own package rather than an append of the player's, what every route measured, the captures the panel round carries, and the gstreamer spells that do not work |
| `recipes-multimedia/bigbuckbunny/README.md` + `PROOF.md` | README: what the film is for and how the decode route is proved on the seat. PROOF: the carrier's and the member's sizes and sha256s, the CC BY 3.0 note, and the two build answers the payload needed |

Where an application of this layer was measured, its numbers now live in a `PROOF.md`
next to its recipe rather than in the `README.md`, so the README stays the one screen
a human reads before building or running something. The games layer keeps the same
shape, and its `PROOF.md` files stay in that layer.

The OpenNOW clients, the game engines and the games themselves live in
`meta-good-old-games`, whose `scarthgap` branch carries the long record of
every game round, including the OpenNOW round log and the seat's troubleshooting
table.

---
