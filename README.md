# meta-embedded-apps

Yocto `scarthgap` layer: the applications that run on the seat. One screen long
on purpose -- everything that was measured, tried or patched lives in the detail
file next to the recipe that owns it.

## What is in this branch

| Application | Recipe | Upstream pin | Installed on the seat | What it does |
| --- | --- | --- | --- | --- |
| OpenNOW | `recipes-apps/opennow/opennow_git.bb` | [OpenCloudGaming/OpenNOW](https://github.com/OpenCloudGaming/OpenNOW) `v1.0.4` = `bec4a0b11fc77fb5bfe9584f012296333718e84b`, `branch = "main"` | `1.0.4+release+git0+bec4a0b11f-r0.76` | signs the seat into the CloudMatch account and keeps the credential the other clients use; on this board it does not stream |
| OpenNOWLite | `recipes-apps/opennowlite/opennowlite_git.bb` | same pin as OpenNOW | `1.0.4+release+git0+bec4a0b11f-r2.12` | the client that plays: its own Wayland window, KMS scan-out, Vulkan/VAAPI decode, NVST input and Opus audio decoded in process into the seat's ALSA sink |
| NanoBrowser | `recipes-apps/nanobrowser/nanobrowser_git.bb` | [DanielMartensson/NanoBrowser](https://github.com/DanielMartensson/NanoBrowser) `b5e01869c10140799085a20d2c18cadb37c3f977` | `0.1+git${SRCPV}-r0.32` | minimal QML browser on QtWebEngine |
| YtGst | `recipes-apps/ytgst/ytgst_git.bb` | [DanielMartensson/YtGst](https://github.com/DanielMartensson/YtGst) `9cb2a60806907c6ac91fa3924ea17f910f2172af` | `0.1+git${SRCPV}-r0.48` | YouTube client on GStreamer + yt-dlp with V4L2 hardware decode |

The two CloudMatch clients pin the same upstream commit on purpose: the seat's
account, the catalog and the CloudMatch consent are one thing both of them read,
and a pin that lets the two disagree is the one mistake that produced two
clients arguing over the same allocation.

### Bundled dependencies

Additional recipes shipped in this layer because one of the applications above
needs them.

| Recipe | Needed by | Why |
| --- | --- | --- |
| `recipes-multimedia/libsdl3/libsdl3_3.4.16.bb` | OpenNOW | SDL 3 controller input |
| `recipes-multimedia/gstreamer/gstreamer1.0-plugins-good_%.bbappend` | YtGst | adds the Qt6 QML video sink (`qml6glsink`) |
| `recipes-support/yt-dlp/yt-dlp_2026.08.19.bb` | YtGst | runtime downloader |
| `recipes-devtools/rust/rust-1.85.0-host.bb`, `rust-1.89.0-host.bb` | OpenNOW, OpenNOWLite | the host Rust/Cargo their `do_compile` runs, pinned past what the core's own crate set needs |
| `recipes-qt/qt6/qtwebengine_git.bbappend` | NanoBrowser | keeps QtWebEngine buildable on aarch64 |

Nothing else is vendored here. A Go application in this layer cross-compiles
against oe-core's own `go_1.22.12` and `go-runtime`, and a Quake-style game
against oe-core's own SDL 2.

### Patches on top of upstream

| Recipe | Patches applied | What they are for |
| --- | --- | --- |
| OpenNOW | 1: `0001-keep-the-sign-in-card-out-of-the-layer-path.patch` | the sign-in card and its primary button moved out of the QML layer path, which is where pristine `v1.0.4` segfaults under the seat's Vulkan driver |
| OpenNOWLite | 1: `0001-hand-encoded-media-to-the-host.patch`, plus the vendored `opennow-lite.c`, the patched `opennow_streamer_ffi.h` and the `native/` reference tree | the NVST/RTP ACK fix, the cursor path and hand-encoded media straight to the host |
| NanoBrowser, YtGst | none | pristine upstream |

The 85 OpenNOW patch files of the older pins (`files/`,
`files-unused-playset/` and `files-legacy-987ab3da/`, ~968 K) are not in this
branch: none of them was in `SRC_URI` any more, so they were removed from the
tree rather than kept as an audit trail. Their md5 manifest is on the build host
and they are recoverable from layer commit `61673a6`. What each of them did is
still in the running log in `recipes-apps/opennow/README.md`.

## Dependencies

### Companion layers

These layers must be present on `BBLAYERS` to build this layer:

| Layer | Provides |
| --- | --- |
| `openembedded-core`, `meta-oe`, `meta-python`, `meta-multimedia` | Base BSP / package recipes |
| `meta-qt6` | Qt 6.8 (`qtbase`, `qtmultimedia`, `qtwebengine`, `qtshadertools`, …) |
| `meta-rust-bin` | Rust ≥ 1.89 / `cargo-native`; OpenNOW needs edition 2024 |
| `meta-clang` | Clang toolchain (required by QtWebEngine on aarch64) |

---

## Prerequisites

- A Yocto `scarthgap` environment with the companion layers above activated
  (ST BSP manifest pulls them in automatically).
- **Network access** during the build:
  - `crates.io` for the OpenNOW Rust crates (`do_compile`),
  - GitHub for the sources fetched by `SRC_URI`,
  - ffmpeg.org for the bundled FFmpeg 9.0 tarball (its checksum is pinned in
    the recipe so the build script does not clone GitHub during configure).

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
| `recipes-apps/opennow/README.md` | the layer's long record: OpenNOW's configuration defaults and how the clients run on the seat, the troubleshooting table that spans the whole layer, and the running log of every round (items 1..65) |
| `recipes-apps/opennowlite/README.md` | the OpenNOWLite round records: what the client carried, the fps probes, and what a round costs the seat |
| the standalone OpenNOWLite repository's own `README.md` | how to use the playing client: its switches, its credentials, its troubleshooting |

---
