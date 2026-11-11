# meta-embedded-apps

Yocto `scarthgap` layer: the applications that run on the seat. One screen long
on purpose -- everything that was measured, tried or patched lives in the detail
file next to the recipe that owns it.

## What is in this branch

| Application | Recipe | Upstream pin | Installed on the seat | What it does |
| --- | --- | --- | --- | --- |
| OpenNOW | `recipes-apps/opennow/opennow_git.bb` | [OpenCloudGaming/OpenNOW](https://github.com/OpenCloudGaming/OpenNOW) `v1.0.4` = `bec4a0b11fc77fb5bfe9584f012296333718e84b`, `branch = "main"` | `1.0.4+release+git0+bec4a0b11f-r0.76` | signs the seat into the CloudMatch account and keeps the credential the other clients use; on this board it does not stream |
| OpenNOWLite | `recipes-apps/opennowlite/opennowlite_git.bb` | same pin as OpenNOW, vendored at `files/native` rather than fetched | `1.0.4+release+git0+bec4a0b11f-r2.12` | the client that plays: its own Wayland window, KMS scan-out, Vulkan/VAAPI decode, NVST input and Opus audio decoded in process into the seat's ALSA sink |
| Discordo | `recipes-apps/discordo/discordo_git.bb` | [ayn2op/discordo](https://github.com/ayn2op/discordo) `31905f3e790e63cd0f2366526afe41fb278c226e` | `0.1+git0+31905f3e79-r0.01` | terminal Discord client in the seat's weston-terminal; 21 vendored Go modules, no network at build |
| FTEQW | `recipes-apps/fteqw/fteqw_git.bb` | [fte-team/fteqw](https://github.com/fte-team/fteqw) `f09ac7cccd2d713ddf881d3f1324703082c2cfe4`, `branch = "master"` | `0.0+git0+f09ac7cccd-r0.01` | the gles-only client both games run on: one patch moves the tree to `GLES2/gl*.h` includes, CFLAGS `-DGLESONLY -DGLSLONLY -DFTE_GLES`, no libGL in `NEEDED` |
| LibreQuake | `recipes-apps/librequake/librequake.bb` | [lavenderdotpet/LibreQuake](https://github.com/lavenderdotpet/LibreQuake) release tag `v0.09-beta`, `full.zip` pinned by sha256 | `0.09-beta-r0.01` | the free Quake data: `/usr/share/librequake/id1`, thirteen deathmatch maps, no campaign; the fteqw launcher's default game |
| OpenArena | `recipes-apps/openarena/openarena.bb` | [OpenArena](https://openarena.ws) release `0.8.8`, one zip pinned by sha256 | `0.8.8-r0.01` | the second game's data: `baseoa` nine pk3s + `missionpack`, GPL-2 both for program and data; played by the FTEQW q3 gamecode through `/usr/bin/openarena` |
| NanoBrowser | `recipes-apps/nanobrowser/nanobrowser_git.bb` | [DanielMartensson/NanoBrowser](https://github.com/DanielMartensson/NanoBrowser) `b5e01869c10140799085a20d2c18cadb37c3f977` | `0.1+git${SRCPV}-r0.32` | minimal QML browser on QtWebEngine |
| YtGst | `recipes-apps/ytgst/ytgst_git.bb` | [DanielMartensson/YtGst](https://github.com/DanielMartensson/YtGst) `9cb2a60806907c6ac91fa3924ea17f910f2172af` | `0.1+git${SRCPV}-r0.48` | YouTube client on GStreamer + yt-dlp with V4L2 hardware decode |

The two CloudMatch clients pin the same upstream commit on purpose: the seat's
account, the catalog and the CloudMatch consent are one thing both of them read,
and a pin that lets the two disagree is the one mistake that produced two
clients arguing over the same allocation. OpenNOWLite's tree is vendored inside
its recipe (`files/native`) rather than fetched: the standalone repository that
used to carry it was deleted, and the recipe builds from the vendored copy with
`file://` entries only, driving the same cargo workspace from
`${S}/native/opennow-streamer` that the opennow recipe drives inside its own
upstream checkout.

### The two games the seat plays natively

Both are played through FTEQW's gles client, both were run on the seat on
Nov 11 2026 while their launchers, options and payloads were set up, and both
printed their console proof, a screenshot of a rendered world, and their cost
while running - the seat ran LibreQuake's `lqdm3` at `RES 55672 KB / %CPU ->
16.7 / ~240-277 MB used of 1768 MB` with a 128171 B screenshot, and OpenArena's
`oa_dm6` at `RES 120272 KB / %CPU -> 61.5 / ~590 MB used` with a 1587779 B one.
The pair follows the layer's rule that a game must be free to use AND free to
download: LibreQuake replaces the proprietary Quake paks with free ones
(thirteen deathmatch maps, and that is all it ships), OpenArena is free both
ways under GPL-2, and Quake II was searched, found to have no fully free
asset set, and skipped without a recipe. Everything measured lives in the
games' own detail files.

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
| OpenNOWLite | 1: `0001-hand-encoded-media-to-the-host.patch` kept as documentation (its hunks are already folded into `files/native`), plus the vendored `native/` tree, `opennow-lite.c` and `opennowlite-launch.sh` | the NVST/RTP ACK fix, the cursor path and hand-encoded media straight to the host |
| FTEQW | 1: `0001-use-the-gles2-headers-instead-of-the-desktop-gl-ones.patch` | the tree's one Android branch reused for this board: desktop `GL/gl*.h` includes replaced by `GLES2/gl*.h` ones under `-DFTE_GLES` |
| LibreQuake, OpenArena | none | data-only recipes, the release archives installed as they are |
| Discordo | none | pristine pin; its 21 Go dependencies are vendored as one tarball instead |
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
bitbake opennow opennowlite discordo fteqw librequake openarena nanobrowser ytgst
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
| `recipes-apps/opennow/README.md` | the layer's long record: OpenNOW's configuration defaults and how the clients run on the seat, the troubleshooting table that spans the whole layer, and the running log of every round (items 1..67) |
| `recipes-apps/opennowlite/README.md` | the OpenNOWLite round records: what the client carried, the fps probes, what a round costs the seat, and what the vendored tree changed |
| `recipes-apps/opennowlite/files/README.md` | how to use the playing client: its switches, its credentials, its troubleshooting - the OpenNOWLite project's own README, vendored into the recipe |
| `recipes-apps/fteqw/README.md` | the engine: how it was chosen over what has no GLES, what its makefile flags answer, the launchers' option sets and the failure modes of the console queue measured on this board |
| `recipes-apps/librequake/README.md` | what the Quake payload carries and its two-part license, plus the lqdm3 round's proof numbers |
| `recipes-apps/openarena/README.md` | the release's own license claim, the pin, the oa_dm6 round's proof numbers and the Q3 gamecode's failure modes |
| `recipes-apps/discordo/README.md` | why the pin is not HEAD, the 21 vendored modules and their versions, and what the client is on the seat |

---
