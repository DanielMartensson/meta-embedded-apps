# meta-embedded-apps

Yocto meta-layer for `scarthgap`, packaging three Qt 6 applications for
embedded Linux:

- **OpenNOW** — cloud-gaming client (Qt 6 UI + native Rust streamer)
- **NanoBrowser** — minimal web browser on QtWebEngine/QML
- **YtGst** — YouTube client (GStreamer + yt-dlp, V4L2 hardware decode)

The recipes are machine-agnostic. `opennow` builds on any Linux machine:
hardware decode is off by default and is enabled per SoC in a `*.bbappend`
next to your BSP.

## Table of contents

1. [Contents](#contents)
2. [Dependencies](#dependencies)
3. [Prerequisites](#prerequisites)
4. [Getting started](#getting-started)
5. [Configuration](#configuration)
6. [Running on the target](#running-on-the-target)
7. [Troubleshooting](#troubleshooting)

---

## Contents

### Applications

| Application | Recipe | Source |
| --- | --- | --- |
| OpenNOW | `recipes-apps/opennow/opennow_git.bb` | [OpenCloudGaming/OpenNOW](https://github.com/OpenCloudGaming/OpenNOW) |
| NanoBrowser | `recipes-apps/nanobrowser/nanobrowser_git.bb` | [DanielMartensson/NanoBrowser](https://github.com/DanielMartensson/NanoBrowser) |
| YtGst | `recipes-apps/ytgst/ytgst_git.bb` | [DanielMartensson/YtGst](https://github.com/DanielMartensson/YtGst) |

### Bundled dependencies

Additional recipes shipped in this layer.

| Recipe | Needed by | Why |
| --- | --- | --- |
| `recipes-multimedia/libsdl3` | OpenNOW | SDL 3 controller input |
| `recipes-multimedia/gstreamer/…bbappend` | YtGst | Qt6 QML video sink (`qml6glsink`) |
| `recipes-support/yt-dlp` | YtGst | Runtime downloader |

---

## Dependencies

### Companion layers

These layers must be present on `BBLAYERS` to build this layer:

| Layer | Provides |
| --- | --- |
| `openembedded-core`, `meta-oe`, `meta-python`, `meta-multimedia` | Base BSP / package recipes |
| `meta-qt6` | Qt 6.8 (`qtbase`, `qtmultimedia`, `qtwebengine`, `qtshadertools`, …) |
| `meta-rust-bin` | Rust ≥ 1.85 / `cargo-native` with `aarch64-unknown-linux-gnu` std |
| `meta-clang` | Clang toolchain (required by QtWebEngine on aarch64) |

---

## Prerequisites

- A Yocto `scarthgap` environment with the companion layers above activated
  (ST BSP manifest pulls them in automatically).
- **Network access** during the build:
  - `crates.io` for the OpenNOW Rust crates (`do_compile`),
  - GitHub for the sources fetched by `SRC_URI`.

---

## Getting started

Add the layer to `BBLAYERS` and build the applications:

```bash
bitbake opennow nanobrowser ytgst
```

To build everything into the reference Weston image:

```bash
bitbake st-image-weston
```

Results are the regular root filesystem archives / images, e.g.
`tmp-glibc/deploy/images/…`.

---

## Configuration

Defaults are machine-agnostic. Override them per machine or distribution,
usually in `conf/local.conf`, a `*.conf` file, or a `*.bbappend` next to your
BSP. Anything that depends on the video hardware or GPU of a particular SoC
belongs in a `*.bbappend`, not in this layer's recipes.

### OpenNOW

| Variable / flag | Default | Purpose |
| --- | --- | --- |
| `PACKAGECONFIG` | `vulkan` | What to package |
| `OPENNOW_CARGO_FEATURES` | empty | Extra cargo features, see below |
| `OPENNOW_RUST_TARGET` | empty | Rust target triple; empty builds for the host triple |
| `OPENNOW_VK_DRIVER_FILES` | empty | Vulkan ICDs to load (`VK_DRIVER_FILES`) |
| `OPENNOW_VK_ICD_FILENAMES` | empty | Legacy Vulkan ICD list (`VK_ICD_FILENAMES`) |
| `OPENNOW_VK_DEVICE_INDEX` | empty | Physical GPU to use (`VK_DEVICE_INDEX`) |
| `OPENNOW_QT_RHI_BACKEND` | empty | Qt Quick adaptation (`QT_QUICK_BACKEND`), not the RHI backend |
| `OPENNOW_RENDERER` | empty | RHI backend passed to the application: empty is Vulkan, `opengl` picks OpenGL |

#### Choosing a hardware decoder

`OPENNOW_CARGO_FEATURES` takes a comma-separated list. The upstream
`opennow-streamer` crate defines exactly three features, and passing anything
else makes cargo abort:

| Feature | What it does | When to use it |
| --- | --- | --- |
| *(empty)* | No optional decode backend | Default. Builds everywhere. The stateful V4L2 M2M backend in `v4l2.rs` is compiled unconditionally and needs no feature, as is Vulkan. |
| `linux-vaapi` | Adds a VA-API backend that reaches the decoder through DRM PRIME | Only if the machine exposes a **stateless** V4L2 codec node (`V4L2_CAP_VIDEO_CODEC_STATELESS`). Stateful memory-to-memory decoders cannot bind to it, which is the common case. Adds `libva` to `RDEPENDS`. |
| `linux-ffmpeg` | FFmpeg decode modes, for software HEVC/AV1 | If the distribution provides an `ffmpeg`. |
| `linux-ffmpeg-bundled` | Cross-compiles FFmpeg from source | Rejected by the recipe. On aarch64 it pulls a third-party FFmpeg fork rather than upstream FFmpeg, and the build takes hours. |

The right value is a property of the SoC, not of this layer, so set it in a
`*.bbappend` next to your BSP rather than editing the recipe. The client probes
Vulkan, Cuda, VaApi, V4l2 and Ffmpeg and reports what it found; the user picks
one in the UI.

#### Rendering backend

Upstream forces the Vulkan RHI on Linux. That is right for streaming - the
embedded streamer hands its textures to Qt's Vulkan device - but the Ui does not
need it, and a machine whose OpenGL driver is better for Qt Quick can say so:
patch `0005` adds `OPENNOW_RENDERER=opengl`. Empty means Vulkan, so nothing
changes by default.

Measured on the STM32MP257F (Weston 13, 1920x1080 HDMI, the default 1600x900
window, package `r0.12`; start-up from `--measure-startup`, CPU and memory after
the catalog finished loading):

| backend | first frame | idle CPU, GUI + core | RSS, GUI + core | stderr |
| --- | --- | --- | --- | --- |
| Vulkan | 6.9 s | 2.7 % + 0 % of a core | 252 MB + 40 MB | empty |
| OpenGL | 4.6 s | 2.3 % + 0 % of a core | 196 MB + 40 MB | empty |

The QML scene costs about 3.9 s on both backends. On Vulkan the first frame adds
another 2.9 s because the driver compiles the Qt Quick shaders for every
material on the fly; opening a surface that was not drawn before (the
game-details sheet, for example) can stall for about a second the first time.
No pipeline cache is saved: the VeriSilicon driver deadlocks inside
`libvulkan_VSI` when Qt tears the window down, which is also why exit used to
hang. There is no cache file to seed the next run with.

`QWindowPrivate::destroy()` waits for the render thread, while the render thread
waits for a Wayland roundtrip inside `libvulkan_VSI`; the embedded streamer's
scene-graph shutdown can abort on a mutex at the same point. The old result was
the ten-second teardown watchdog, or a core dump on `SIGABRT`. Patch `0007`
stops the core client and leaves the process with `_Exit()` instead of running
Qt's teardown when no restart is pending; the window, scene graph and streamer
are per-process resources, so nothing is lost. Closing the window is immediate
now and writes no core dump. Patch `0006` keeps SDL3 from installing its own
SIGINT/SIGTERM handlers, which used to swallow `kill` and `systemctl stop` and
leave the process running forever.

The recipe tracks upstream's `dev` branch. That branch reports itself as `1.0.4`
(the nightly line), while the newest stable release is `1.0.3` - the version the
app's stable update channel offers. `PV` follows the source tree, so the package
is `1.0.4+git...` even though the last stable release has a lower number.

### YtGst

| Variable | Default | Purpose |
| --- | --- | --- |
| `YTGST_VIDEO_DECODER` | `v4l2h264dec` | GStreamer decoder element |

---

## Running on the target

### OpenNOW

Start via the `opennow` launcher to enable hardware decode:

1. Launch `opennow` (shell script that exports the Vulkan/Qt environment).
2. In the app: pick the backend your machine offers and codec **H.264**.

Which decode path is available depends on `OPENNOW_CARGO_FEATURES` and on the
video hardware:

- default build: **stateful V4L2 memory-to-memory**, as found on most
  hardware video decoders that expose `V4L2_CAP_VIDEO_M2M`
- `linux-vaapi`: **VA-API → DRM PRIME → stateless V4L2 codec node**

Requires Wayland and Vulkan in both cases.

### NanoBrowser

Run `nanobrowser`. As root, disable the Chromium sandbox:

```bash
QTWEBENGINE_DISABLE_SANDBOX=1 nanobrowser
```

### YtGst

Start `ytgst`. Requires GStreamer plugins, the `yt-dlp` tool and PulseAudio
for audio output.

---

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| OpenNOW build fails on Cargo | Ensure crates.io is reachable during `do_compile` |
| OpenNOW configure fails with `unknown feature` | `OPENNOW_CARGO_FEATURES` names a feature the upstream crate does not define. Only the three in the table above exist |
| NanoBrowser crashes when run as root | Set `QTWEBENGINE_DISABLE_SANDBOX=1` |
| No hardware-accelerated video in OpenNOW | OpenNOW reports no usable backend. With the default build it needs a V4L2 memory-to-memory node offering NV12 or I420; with `linux-vaapi` it needs a stateless node. Confirm with `v4l2-ctl -d /dev/video0 --list-formats-ext` |
| QtWebEngine build is slow / OOM on 8 GB host | Lower build parallelism via `PARALLEL_MAKE = "-j<N>"` in `local.conf` |
