# meta-embedded-apps

Yocto meta-layer for the Watermelon-Wine STM32MP257F board. It builds three
applications from their upstream git repositories, plus the dependency recipes
missing from the surrounding layer stack. **No patches to upstream sources.**

## Recipes

| Recipe | Upstream | What it builds |
| --- | --- | --- |
| `opennow_git.bb` | [OpenCloudGaming/OpenNOW](https://github.com/OpenCloudGaming/OpenNOW) | Qt 6 Quick UI + native Rust cloud-gaming engine (`opennow-qt`, `opennow-core`, `opennow-streamer`, ...) |
| `nanobrowser_git.bb` | [DanielMartensson/NanoBrowser](https://github.com/DanielMartensson/NanoBrowser) | Minimal QtWebEngine/QML browser (`nanobrowser`) |
| `ytgst_git.bb` | [DanielMartensson/YtGst](https://github.com/DanielMartensson/YtGst) | Qt 6 YouTube client on GStreamer + yt-dlp (`ytgst`) |
| `libsdl3_3.4.16.bb` | [SDL](https://github.com/libsdl-org/SDL) (dependency) | SDL 3.x with `SDL3Config.cmake` (required by OpenNOW) |
| `yt-dlp_2026.08.19.bb` | [yt-dlp](https://github.com/yt-dlp/yt-dlp) (dependency) | Standalone Python zipapp (required by YtGst at runtime) |

## Required companion layers

These are re-added and updated by the Watermelon-Wine BSP manifest / `BBLAYERS`
(the old `meta-opennow`, `meta-imtube`, `meta-wpeqt` layers are NOT used):

- `meta-qt6` – Qt 6.8 packages (`qtbase`, `qtdeclarative`, `qtmultimedia`,
  `qtshadertools`, `qtwebengine`, ...)
- `meta-rust-bin` – Rust >= 1.85 / cargo-native with
  `aarch64-unknown-linux-gnu` standard library (OpenNOW uses Rust edition 2024)
- `meta-clang` – Clang toolchain, required by QtWebEngine on aarch64

Base BSP layers (`openembedded-core`, `meta-oe`, `meta-python`,
`meta-multimedia`, `meta-st-stm32mp`, `meta-st-openstlinux`,
`meta-watermelon-wine`) must of course stay on `BBLAYERS`.

## Build notes

```
bitbake opennow nanobrowser ytgst
```

- **OpenNOW** builds the CMake project in `${S}/opennow-qt` and cross-compiles
  the Rust core. Cargo needs network access to crates.io during `do_compile`.
  Rust target triple is `OPENNOW_RUST_TARGET` (default
  `aarch64-unknown-linux-gnu`). Upstream `install()` rules place everything in
  `${bindir}` (Qt app, Rust core binaries, `opennow-streamer`,
  `libopennow_streamer_ffi.so`).
- **NanoBrowser** has no `install()` rule, so `do_install` copies the binary.
- **YtGst** uses `qml6glsink` / `org.freedesktop.gstreamer.Qt6GLVideoItem`
  (`gstreamer1.0-plugins-good-qt6`, enabled via the layer's bbappend to
  `gstreamer1.0-plugins-good`). Decoder defaults to `v4l2h264dec`
  (STM32MP2 V4L2 stateless codec); override with e.g.
  `YTGST_VIDEO_DECODER = "avdec_h264"` in `local.conf`.

## Runtime notes

- **OpenNOW** needs Wayland (Weston), Vulkan (`vulkan-loader`) and the
  SDL 3 runtime (`libsdl3`) for controller input.
- **NanoBrowser** needs `QTWEBENGINE_DISABLE_SANDBOX=1` (or a setuid sandbox)
  when started as root; e.g.
  `QTWEBENGINE_DISABLE_SANDBOX=1 QT_QUICK_BACKEND=software nanobrowser` is a
  useful safe fallback if GPU issues occur.
- **YtGst** calls `yt-dlp` from `PATH` (installed to `${bindir}`) and requires
  the GStreamer runtime plugin set and audio (`pulseaudio`).

### OpenNOW hardware-accelerated decode (selectable, not STM32-specific)

The STM32MP2 video codec (`/dev/video0`, hantro V4L2 **stateless**) is not
served by OpenNOW's native stateful-H.264 V4L2 path, so decoding falls back to
the bundled FFmpeg software decoder. To use the silicon decoder:

- Add a V4L2-stateless user-space media driver to the image (e.g. a
  `libva-v4l2-request` libVA backend). OpenNOW's VA-API backend
  (`vaapi` PACKAGECONFIG, `libva` runtime dep) then decodes on the hardware
  via `/dev/video0`.
- Launch OpenNOW through the `opennow` launcher helper (installed when the
  `v4l2-request` PACKAGECONFIG is enabled). It exports
  `LIBVA_DRIVER_NAME`/`LIBVA_DRIVERS_PATH` from `OPENNOW_LIBVA_DRIVER` and
  `OPENNOW_LIBVA_DRIVERS_PATH` before exec'ing `opennow-qt`.
- Inside the app select stream -> decoder/backend `VA-API` and codec `H.264`
  so `auto` does not prefer the (software) FFmpeg/Vulkan path.

The same mechanism works on other embedded boards that expose a stateless
decoder through a libVA driver; nothing in the recipe is pinned to STM32.
Override `PACKAGECONFIG`, `OPENNOW_LIBVA_DRIVER`/`OPENNOW_LIBVA_DRIVERS_PATH`
per machine or distribution.

## Updating a pinned revision

The app recipes pin `SRCREV` and `PV = "x+git${SRCPV}"`. Bump `SRCREV` to the
new upstream commit and change `PV` accordingly if the project bumps version.