# meta-embedded-apps

Yocto meta-layer for `scarthgap`.

## This branch contains

| Application | Recipe | Source | Builds |
| --- | --- | --- | --- |
| OpenNOW | `recipes-apps/opennow/opennow_git.bb` | [OpenCloudGaming/OpenNOW](https://github.com/OpenCloudGaming/OpenNOW) | Cloud gaming client (Qt 6 UI + native Rust streamer) |
| NanoBrowser | `recipes-apps/nanobrowser/nanobrowser_git.bb` | [DanielMartensson/NanoBrowser](https://github.com/DanielMartensson/NanoBrowser) | Minimal QtWebEngine/QML browser |
| YtGst | `recipes-apps/ytgst/ytgst_git.bb` | [DanielMartensson/YtGst](https://github.com/DanielMartensson/YtGst) | YouTube client (GStreamer + yt-dlp, V4L2 hardware decode) |

| Dependency recipe (in this layer) | Needed by |
| --- | --- |
| `recipes-multimedia/libsdl3` | OpenNOW (SDL 3 controller input) |
| `recipes-multimedia/gstreamer` bbappend | YtGst (Qt6 QML video sink `qml6glsink`) |
| `recipes-support/yt-dlp` | YtGst (runtime downloader) |

## Companion layers (from the Watermelon-Wine manifest / `BBLAYERS`)

| Layer | Provides |
| --- | --- |
| `meta-qt6` | Qt 6.8 (`qtbase`, `qtmultimedia`, `qtwebengine`, `qtshadertools`, ...) |
| `meta-rust-bin` | Rust ≥ 1.85 / `cargo-native` with `aarch64-unknown-linux-gnu` std |
| `meta-clang` | Clang toolchain (required by QtWebEngine on aarch64) |
| `meta-st-stm32mp` / `meta-st-openstlinux` | STM32MP2 BSP (OpenSTLinux) |
| `openembedded-core` / `meta-oe` / `meta-python` / `meta-multimedia` | Base BSP layers |

All layers are pulled in via the BSP manifest; the old `meta-opennow`,
`meta-imtube` and `meta-wpeqt` layers are **not** used.

## Build

```
bitbake opennow nanobrowser ytgst
```

Cargo needs crates.io access during OpenNOW `do_compile`.

## Configuration

| Variable / flag | Default | Purpose |
| --- | --- | --- |
| `PACKAGECONFIG` | `vulkan vaapi v4l2-request` | Selectable runtime backends for OpenNOW |
| `OPENNOW_RUST_TARGET` | `aarch64-unknown-linux-gnu` | OpenNOW Rust target triple |
| `OPENNOW_LIBVA_DRIVER` | `v4l2_request` | libVA driver exported by the `opennow` launcher |
| `OPENNOW_LIBVA_DRIVERS_PATH` | `${libdir}/dri` | libVA driver search path |
| `YTGST_VIDEO_DECODER` | `v4l2h264dec` | YtGst decoder element |

## Runtime notes

| App | Notes |
| --- | --- |
| OpenNOW | Needs Wayland, Vulkan, `libsdl3`. Use the `opennow` launcher to enable hardware decode: VA-API backend → libva `v4l2-request` driver → `/dev/video0` (STM32MP2 stateless decoder); in-app select backend `VA-API` + codec `H.264` |
| NanoBrowser | As root run `QTWEBENGINE_DISABLE_SANDBOX=1` |
| YtGst | Needs GStreamer plugins, `yt-dlp` and `pulseaudio` |
