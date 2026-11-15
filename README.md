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
own GStreamer plus the yt-dlp recipe above.

### Patches on top of upstream

| Recipe | Patches applied | What they are for |
| --- | --- | --- |
| Discordo | none | pristine pin; its 21 Go dependencies are vendored as one tarball instead |
| NanoBrowser, YtGst | none | pristine upstream; only the two bbappends above are not pristine |

## Dependencies

### Companion layers

These layers must be present on `BBLAYERS` to build this layer:

| Layer | Provides |
| --- | --- |
| `openembedded-core`, `meta-oe`, `meta-python`, `meta-multimedia` | Base BSP / package recipes |
| `meta-qt6` | Qt 6.8 (`qtbase`, `qtwebengine`, `qtshadertools`, …) - NanoBrowser |
| `meta-clang` | Clang toolchain (required by QtWebEngine on aarch64) |

---

## Prerequisites

- A Yocto `scarthgap` environment with the companion layers above activated
  (ST BSP manifest pulls them in automatically).
- **Network access** during the build: GitHub for the sources fetched by
  `SRC_URI`.

---

## Getting started

Add the layer to `BBLAYERS` and build the applications:

```bash
bitbake discordo nanobrowser ytgst
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

## Where the details live

| File | What is in it |
| --- | --- |
| `recipes-apps/discordo/README.md` | why the pin is not HEAD, the 21 vendored modules and their versions, and what the client is on the seat |
| `recipes-apps/nanobrowser/README.md` | how to drive the browser on the seat, its switches as the installed binary answers them, and the OpenGL-against-Vulkan page timings |
| `recipes-apps/ytgst/README.md` | how to drive the YouTube client, its `YTGST_*` switches, what its window and subsurface are, and the panel timings that made OpenGL the shipped renderer |

The OpenNOW clients, the game engines and the games themselves live in
`meta-good-old-games`, whose `scarthgap` branch carries the long record of
every game round, including the OpenNOW round log and the seat's troubleshooting
table.

---
