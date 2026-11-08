# meta-embedded-apps

Yocto meta-layer for `scarthgap`, packaging three Qt 6 applications for
embedded Linux:

- **OpenNOW** — cloud-gaming client (Qt 6 UI + native Rust streamer)
- **NanoBrowser** — minimal web browser on QtWebEngine/QML
- **YtGst** — YouTube client (GStreamer + yt-dlp, V4L2 hardware decode)

The recipes are machine-agnostic. `opennow` builds on any Linux machine:
hardware decode is selected at runtime by the client, and what a given SoC
offers belongs in a `*.bbappend` next to your BSP.

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

### OpenNOW series state

| Item | Value |
| --- | --- |
| Upstream source | `dev` branch, `SRCREV = "b3fe71026d8261d386c6f16fcdeb1596ebb85942"` |
| Patches applied | 35 (the play set), `recipes-apps/opennow/files/0001`…`0044` with gaps at the retired numbers; 11 retired aside in `recipes-apps/opennow/files-unused-playset/` |
| Package version | `1.0.4+git1+b3fe71026d-r0.74` |
| Reference series kept aside | 39 patch files of the older `987ab3da` SRCREV, in `files-legacy-987ab3da/` (not in `SRC_URI`, not applied) |

The two series solve the same problem from different directions, so only one
is in the build. The legacy tree is kept as documentation of what was tried,
including the attempts that were measured to be wrong.

---

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
| `OPENNOW_CARGO_FEATURES` | `linux-ffmpeg-bundled` | Extra cargo features, see below |
| `OPENNOW_RUST_TARGET` | empty | Rust target triple; empty builds for the host triple |
| `OPENNOW_VK_DRIVER_FILES` | empty | Vulkan ICDs to load (`VK_DRIVER_FILES`) |
| `OPENNOW_VK_ICD_FILENAMES` | empty | Legacy Vulkan ICD list (`VK_ICD_FILENAMES`) |
| `OPENNOW_VK_DEVICE_INDEX` | empty | Physical GPU to use (`VK_DEVICE_INDEX`) |
| `OPENNOW_QT_RHI_BACKEND` | empty | Qt Quick adaptation (`QT_QUICK_BACKEND`) |

Runtime knobs the application itself reads (set them in the shell, not in the
recipe):

| Knob | Effect |
| --- | --- |
| `OPENNOW_RENDERER=opengl` | The interface draws with OpenGL ES instead of Vulkan. **Lowercase matters** — patch `0001` tests `qgetenv("OPENNOW_RENDERER").toLower() == "opengl"`, so `OPENGL` and anything else take the Vulkan branch. It is a switch, not a toggle: `vulkan` and unset are the same path. |
| `OPENNOW_SWAP_INTERVAL` | The interface window's present throttle (patch `0013`/`0014`): how many vsyncs must pass before the next presentation. Unset or `<= 0` becomes **2**; `>= 1` is applied as given, so `1` is upstream's every-vsync behaviour, which is the setting that reproduces the vendor assert. |
| `OPENNOW_LOG_PRESENT=1` | Makes `wayland-video fps=…` and `opennow-present fps=… render=… swapped=…` go to the terminal. Capture them with a tee (`opennow 2>&1 \| tee -a …`) — the diagnostics files do not carry the present counter. |
| `OPENNOW_LOG_FRAME_RATES=1` | Frame-rate counters. |
| `OPENNOW_LOG_INPUT=1` | What the stream window forwards: pointer positions at most twice a second, keys and buttons one line each. |
| `OPENNOW_WAYLAND_VIDEO=0` | Opt back out of the DMA-BUF stream window and put the video back through the Qt scene graph, for the A/B runs. |
| `OPENNOW_HANDOFF_RETAINED` | The pinned handoff frame (patch `0012`). See the measured note below before raising it. |

#### Choosing a hardware decoder

`OPENNOW_CARGO_FEATURES` takes a comma-separated list. The upstream
`opennow-streamer` crate defines exactly three features, and passing anything
else makes cargo abort with "none of the selected packages contains these
features":

| Feature | What it does | When to use it |
| --- | --- | --- |
| *(empty)* | No optional decode backend | Builds everywhere. The stateful V4L2 memory-to-memory backend in `v4l2.rs` is compiled unconditionally and needs no feature, as does Vulkan. |
| `linux-vaapi` | Adds a VA-API backend that reaches the decoder through DRM PRIME | Only if the machine exposes a **stateless** V4L2 codec node (`V4L2_CAP_VIDEO_CODEC_STATELESS`). Stateful memory-to-memory decoders cannot bind to it, which is the common case. Adds `libva` to `RDEPENDS`. |
| `linux-ffmpeg` | FFmpeg decode modes, for software HEVC/AV1 | If the distribution provides an `ffmpeg`. |
| `linux-ffmpeg-bundled` | Cross-compiles FFmpeg 9.0 from source, and applies the v4l2-request patches to it | The value this layer uses, and the only one that makes the STM32MP25 VPU usable: on aarch64 it is what carries the V4L2 **Request** hwaccel, the same hardware path the GStreamer `v4l2slh264dec` element uses. A distribution FFmpeg (6.1 in this image) has no such hwaccel, so `linux-ffmpeg` cannot replace it. |

The right value is a property of the SoC, not of this layer, so set it in a
`*.bbappend` next to your BSP rather than editing the recipe. The client probes
Vulkan, Cuda, VaApi, V4l2 and Ffmpeg and reports what it found; the user picks
one in the UI.

What the application reported for this board's decode capability, verbatim from
its own diagnostics dump:

```
"supportsVideoDecode": true, "supportsVideoPresent": true,
"availableVideoBackends": [],
"reason": "the attached Vulkan device does not support this decode profile"
"reason": "no compatible shared Vulkan decode device is attached"
"reason": "no accessible streaming h265 stateless (S265) decoder under /dev/video*"
"reason": "no accessible streaming av1 stateless (AV1F) decoder under /dev/video*"
```

So decode runs through the FFmpeg/V4L2 (Hantro VPU) path, **not** through
Vulkan Video: the STM32MP25's VeriSilicon device has neither `VK_KHR_video_queue`
nor a decode extension. The Vulkan device OpenNOW creates is the buffer-export
device for the decoder's DMA-BUFs (`embedded Vulkan buffer-export device (no
video decode): queue=0:0`).

#### Rendering backend

Upstream forces the Vulkan RHI on Linux. That is right for streaming — the
embedded streamer hands its textures to Qt's Vulkan device — but the Ui does
not need it, and a machine whose OpenGL driver is better for Qt Quick can say
so: patch `0001` adds `OPENNOW_RENDERER=opengl`. Empty means Vulkan, so nothing
changes by default.

The Qt Vulkan examples that import an external image do not move that answer, and
the reason is in what each side creates. Qt 6.11's `quick/scenegraph/vulkantextureimport`
example has to have its `VkImageCreateInfo` moved from `VK_IMAGE_TILING_OPTIMAL`
plus `VK_IMAGE_LAYOUT_PREINITIALIZED` to `VK_IMAGE_TILING_LINEAR` plus
`VK_IMAGE_LAYOUT_UNDEFINED` on this machine, and that fixes the *example's* image:
the example maps host memory and promises a layout it wrote before first use, and a
tiled GPU's driver refuses both. OpenNOW's imported video image is not that shape.
`frame_producer.rs`'s `import_nv12_dmabuf` already creates it with
`.tiling(vk::ImageTiling::DRM_FORMAT_MODIFIER_EXT)` and
`.initial_layout(vk::ImageLayout::UNDEFINED)` beside
`VkImageDrmFormatModifierExplicitCreateInfoEXT` and explicit plane layouts - the only
tiling the DMA-BUF contract allows, so `VK_IMAGE_TILING_LINEAR` there is a validation
error, and OpenNOW already carries the second half of the change everywhere: every
layout it needs is reached through an explicit `pipeline_barrier` transition
(`UNDEFINED` to `TRANSFER_DST_OPTIMAL` to `SHADER_READ_ONLY_OPTIMAL`). The two places
`vk::ImageTiling::OPTIMAL` does appear in OpenNOW are a format-support query for its
own GPU-only output images and the VK_VIDEO decode admission, which refuses a driver
that reports anything but `image_tiling == OPTIMAL` for the decode output format - a
check this board never reaches, since the VeriSilicon device has no `VK_KHR_video_queue`
at all and decodes through the Hantro V4L2 path. So the Vulkan failure this board has
is not a tiling or layout question: it is the measured `buffer_fenced_release` at an
EGL swapchain recycle, the +172 MB of RSS over OpenGL at streaming, and the 31.4 ms of
composition per frame at 1280x720. OpenGL stays the renderer of record.

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

At the current package (`r0.45`, 14-patch series, Dark and Darker at 1280x720
streamed onto the 1920x1080 output) the same build was measured end to end on
both renderers; see [Measured stream throughput](#measured-stream-throughput).

`QWindowPrivate::destroy()` waits for the render thread, while the render thread
waits for a Wayland roundtrip inside `libvulkan_VSI`; the embedded streamer's
scene-graph shutdown can abort on a mutex at the same point. The old result was
the ten-second teardown watchdog, or a core dump on `SIGABRT`. Patch `0001`
(which carries the bring-up set of the old `0006`/`0007`) stops the core client
and leaves the process with `_Exit()` instead of running Qt's teardown when no
restart is pending; the window, scene graph and streamer are per-process
resources, so nothing is lost. Closing the window is immediate now and writes no
core dump.

The recipe tracks upstream's `dev` branch. That branch reports itself as `1.0.4`
(the nightly line), while the newest stable release is `1.0.3` — the version the
app's stable update channel offers. `PV` follows the source tree, so the package
is `1.0.4+git…` even though the last stable release has a lower number.

#### Patches on top of upstream

`0001`-`0022` in `recipes-apps/opennow/files/` are the only changes to the
upstream tree at `b3fe7102`. They are one curated series: each step is the
smallest change that made a measured symptom go away, and the later steps
stand on the earlier ones.

| Patch | What it does |
| --- | --- |
| `0001` | **Settle the interface on this board.** The bring-up set the video work stands on: ask FFmpeg only for the decoders and surface extensions this machine actually has (a Wayland-only board has no X11 surface, and FFmpeg reported every extension it had been asked for); drop the VeriSilicon driver's "queue destroyed while proxies still attached" report, which it prints on every start and exit of a connection it opened itself, while leaving every other `libwayland` diagnostic alone; select the renderer at run time so the interface can draw with OpenGL while the video keeps its own Vulkan device; let `SIGTERM` stop the application and leave the process with `_Exit()` when its event loop ends, so `systemctl stop` does not have to kill it; keep the sign-in card and its primary button out of the QML layer path, because the driver segfaults in `QSGRhiLayer::grab()` when the card re-renders while the QR panel appears; do not let one slow region block a launch; and print the native error message instead of a generic one. |
| `0002` | **Give the streamer a frame the compositor can import.** The decoder hands out DMA-BUF backed NV12 frames, and the interface can only sample a Vulkan image it owns. Adds the three bridges — a CPU copy of the two NV12 planes for a device that cannot import the decoder's buffer at all, a linear NV12 `VkBuffer` import so a device with the external-buffer bit copies on the GPU instead, and a cache keyed by the DMA-BUF object so the same buffer is imported once and reused frame after frame — plus the frame counter and first-frame PPM dump every later measurement in this layer quotes. |
| `0003` | **Keep the video pass running under the interface.** Qt stops rendering a scene that does not change, and the video used to advance only inside the interface's render pass. Keeps the pass alive while a stream is up (a repaint timer, an opaque window sampled 1:1 on the bandwidth-bound part), counts the frame notifications so a stuck pass is visible in the log without a debugger, and lets the video pass be switched while a stream is running, which is what makes the A/B attributions possible. |
| `0004` | **Present decoded frames through a Wayland surface.** The frame producer exports the decoder's DMA-BUF (plane offsets, strides, the duplicated handle) through the FFI, and the interface owns a Wayland surface of its own that turns each export into a `wl_buffer` with `zwp_linux_dmabuf_v1` and commits it. Nothing is converted on the GPU: the compositor samples the decoder's NV12 planes directly. The surface is created on the GUI thread with the connection quiet and posts frames from the render thread, and the buffer is described at its visible height even when the decoder pads the picture. |
| `0005` | **Make the stream window a window the user can drive.** The stream surface becomes a real top-level: `F11` toggles it between the windowed frame and the whole output, closing it stops the stream instead of leaving a black rectangle, and the keyboard keeps working while the video is on top. The interface underneath stops repainting what the window covers, and a timer pulls frames from the interface thread so the video rate no longer follows the interface's repaint rate. |
| `0006` | **Make the stream window the presentation path.** The DMA-BUF window becomes the default route (the Qt swapchain stays available through `OPENNOW_WAYLAND_VIDEO=0` for the A/B runs). The window takes the pointer and the keyboard, the frame descriptor stays open until Wayland has flushed it, and the compositor keeps one buffer per decoder buffer instead of one per frame, so the decoder's small pool is reused instead of re-imported. |
| `0007` | **Hand input to the game and own the connection.** A cloud game needs relative motion, not a pointer that stops at the screen edge: the stream window turns the mouse into the relative events the host asks for, switches the window once per `F11` press (the release used to repeat the switch, so one tap went fullscreen, windowed and fullscreen again), and treats a frame the compositor cannot take as a dropped frame rather than a failed session. The stream window also gets a Wayland connection of its own, so the render thread and the interface never dispatch each other's events — the shared connection made the driver see a second release fence for a buffer it was still using. |
| `0008` | **Draw with OpenGL and keep the decoder's buffers alive.** The interface draws with OpenGL, which leaves the video without a Qt Vulkan device to borrow, so the streamer builds its own buffer-export device (first graphics queue family, no decode profiles) and Qt never adopts it. The frame producer keeps the decoder frame whose descriptor went out to the compositor alive over a one-entry ring: the export already owns its own duplicate of the handle, and one entry is enough to hold the surface until the queued import and the CPU copy have read it, while leaving the decoder's capture pool free to recycle. |
| `0009` | **Take the seat's pointer only when the seat says it has one.** Weston creates a seat's pointer on the first pointer event, not when the seat appears, and `wl_seat.get_pointer` before that hands out an inert object whose server-side user data is NULL. This build asked for a relative pointer on that object unconditionally, and Weston 13.0.1 dereferenced the NULL `weston_pointer` in `relative_pointer_manager_get_relative_pointer`, so the compositor died with `SIGSEGV` the moment the stream window opened, before the first frame could be presented. The fix listens for `wl_seat.capabilities` and creates the keyboard, the pointer, the relative pointer and the pointer lock only when the matching capability is advertised; a withdrawn capability tears the same objects down in reverse order. |
| `0010` | **Hand the streamer its own device whatever Qt draws with.** The stream window presents the decoder's DMA-BUF itself, so the video needs no Qt graphics device at all — only the Vulkan device the streamer creates for the buffer export. The handover sat inside the QRhi Vulkan case, so an interface drawn with OpenGL fell into the default branch and reported "This graphics backend cannot present native video", which is exactly the backend this board has to use, because the VeriSilicon Vulkan WSI asserts in `buffer_fenced_release` and takes the process with it. The handover moves in front of the switch over `rhi->backend()`. |
| `0011` | **Let libwayland own the descriptors the stream window sends.** The stream window kept every frame descriptor in a list and closed the list after a flush reported success, because libwayland only copies a descriptor into the socket when the request carrying it is written. But libwayland closes each descriptor itself as soon as the request goes out, so that close was a second close of a number that had been free for a moment already. The decoder opens a fresh dma-buf descriptor for every frame it exports and this board's capture pool churns through them at frame rate, so it regularly took the number back between the two closes: the export then failed with `EBADF`, and a number the decoder had reused for something else was handed to the compositor, which refused the import ("Failed to import plane 0 as R8") and closed the stream window with a protocol error. |
| `0012` | **Pin one handoff frame — the window the 55 fps series measured.** The compositor imports the planes only when the queued `add`/`create_immed` pair reaches it, and by then the decoder has retired the frame and handed its capture buffer to the next decode, which makes the import fail with "Failed to import plane 0 as R8". The export's own duplicate keeps the dma-buf *object* alive but not the decoder's *surface*, so the handoff needs one pinned frame across the message. A ring of zero was the theory that the duplicate is enough; it is not. A ring of 24 was measured to break the stream: it pins the whole capture pool, so the decoder names a fresh DMA-BUF every frame, the presenter's cache misses every frame, the compositor EGL-imports a new image every frame and the driver eventually refuses one of them. **One** is what the r0.44 series ran at 55 fps, and that is the value this series keeps. |
| `0013` | **Throttle the interface window's presents so a released image cannot be reused too soon.** The crash that ends the game the moment the stream starts is not in the presenter: it is an assertion inside the VeriSilicon EGL driver, the only file on the board that holds `api/gc_egl_wayland.c`:<br>`opennow-qt: api/gc_egl_wayland.c:2699: buffer_fenced_release:`<br>`Assertion 'buffer->release_fence_fd == -1' failed.`<br>gdb says where: `QRhi::endFrame` on the `QSGRenderThread`, through `QWaylandGLContext::swapBuffers`, into vendor libEGL, inside `wl_display_dispatch_queue_pending` when weston's release callback arrives. The driver objects to a fenced release of a buffer whose previous release fence has not been answered yet — an image presented again too soon after it was released. weston reports the lag itself: `event processing lagging behind by 45ms, your system is too slow`. So the reuse distance of a swapchain image has to exceed that lag, and the window's swap interval is the app-side knob for it. |
| `0014` | **Default the interface window's present throttle on.** The throttle in `0013` was opt-in, and the run that never asked for one died the moment the stream window started presenting — the assert above, with weston 45 ms away from answering a buffer release while three images at 60 Hz return an image after about 50 ms. Making **2** the default costs nothing on the picture: the stream window draws through its own DMA-BUF surface and keeps its own 60 Hz present rate (measured `wayland-video fps=58.6..60`), and the interface window paints at 30 Hz, which is the seat UI, not the game. `OPENNOW_SWAP_INTERVAL=1` still selects upstream's every-vsync behaviour for the A/B run, and that is the setting that reproduces the assert. The companion memory change lives on the OS side in `meta-watermelon-wine` (`transparent_hugepage=madvise` in the machine conf). |
| `0015` | **Press the Start button on a clock of its own.** A session can only be started by tapping the Start button, and the pointer that carries that tap is the thing the stream window fights over, so an armed run that is left alone starts nothing and nothing about the audio or the present rate can be measured hands-free. The clock waits `--auto-launch-ms=<ms>` of home screen and then calls root.startHero(), the very function the button's TapHandler calls, so the launch path, its guards and their order are upstream's unchanged. `0` is the default and is upstream's behaviour; the 60 s that was measured here as the shortest settle for a catalog-loaded, signed-in seat is what the clock was set to in the run that worked. Measured: 60 `opennow-autolaunch` lines ending in `opennow-autolaunch: "pressing Start on Dark and Darker"`, then a fresh session in `native-streamer.log`. |
| `0016` | **Open the audio sink on a device and a format the card has.** The measured r0.46 session threw 190 of the 197 arriving audio packets out of the low-latency play queue every second, which says the sink behind that queue took almost none of them; probing `libasound.so.2` on the board said why: `snd_pcm_open("default")` answers `-112` ("Host is down") for the NONBLOCK mode the sink asked for, `hw:0,0` refuses `FLOAT_LE` with `-22`, and a NONBLOCK stream that was never started answers `-EIO` after 0.1 s of audio. So the sink now tries the configured name, then `sysdefault`, `default` and the raw card, in **BLOCK** mode, and asks each one for the first sample format it accepts (`FLOAT_LE`, `S32_LE`, `S16_LE`), converting the f32 stream into whatever the card answers (float to integer casts saturate, which is what a stream louder than 1.0 needs rather than a wrapped sample). Measured in r0.47: the session prints `opennow-alsa-sink: opened default as FLOAT_LE` and the same session logs **no** `queue-dropped` event at all, against ~190/s before. |
| `0017` | **Answer the Escape key in the stream window.** `WaylandVideoSurface::virtualKeyFor` translates the key codes the compositor reports into the virtual keys the host speaks, and it had no entry for evdev code 1, so Escape translated to 0 and `submitKey` returns at once when the virtual key is 0 - the press never went out, which is the open item the game's menus show. Escape is `VK_ESCAPE = 0x1b`, one line in the table the rest of the physical keys are already in. Not yet verified on a screen: the run that would show it needs a fresh boot. |
| `0018` | **Stop concealing the hole a queue drop opens.** The play queue holds 12 packets of 5 ms and `push_latest` evicts the oldest when a stall fills it - the packet the worker is about to play. Every pop after the queue fills therefore leaves an RTP hole, `conceal_before` filled it with up to 100 ms of packet loss concealment, and writing concealment takes exactly as long as the audio it stands in for. The r0.47 session is that fixed point: 186 of the 200 packets a second were dropped and the worker consumed 14, and 14 x 71 ms of concealment is one second of audio a second, so the sink never idles, the queue never drains, and ~95 per cent of what the board played was concealment noise. A full queue now drops the arriving packet (`PushOutcome::DroppedNewest`), so the packets the worker pops stay contiguous and the queue drains; the worker marks the decoder when the queue reports a drop and the next hole is played through instead of concealed, and any hole longer than four packets is played through as well. Holes of up to four packets are still concealed, which is what network loss needs. Built as r0.50 and heard correct: the stream held 193.5 audio packets a second against ~2.5 dropped, the jitter buffer stopped growing, and 60 holes totalling 2.94 s were played through. Marker: `opennow-audio-gap-skip`. |
| `0019` | **Hide the interface window under the stream window.** The stream window is a separate xdg_toplevel that covers the interface completely, yet a covered `QQuickWindow` keeps its render thread: the r0.47 session logs `opennow-present render=22.5-26.6 fps=0.0 swapped=0` for the whole run, i.e. a scene graph and a 25 Hz present timer drawing a buffer the compositor never takes, on the same two cores as the decoder and the audio sink. The interface is hidden when `WaylandVideoSurface` commits its first frame and the visibility it had is put back when the surface goes away; the stream window is not its child, so it is untouched. Measured on r0.50: `opennow-interface-hidden` fires with the first presented frame and the interface's render thread then consumes 0 CPU ticks over a 122 s window against 22.5-26.6 frames a second before, while the stream window keeps presenting at 54 fps. The show-back half finally appeared on the idle r0.53 session of Oct 31 (`ab71-2-2.log`): its stream window died on the vendor `buffer_fenced_release` assert and the log then carries `opennow-interface-shown: the stream window is gone` at 05:35:13.Markers: `opennow-interface-hidden`, `opennow-interface-shown`. |
| `0020` | **Write the diagnostic export when the interface exits.** The redacted `.txt` that carries the Qt log, the RPC log and the embedded streamer's native log could only be produced by pressing Export while the application was still up, and the run worth attaching to a report is the one that just ended. The core now writes that same export when its stdin closes (the interface exiting), prints `opennow-diagnostics-export: <path>` on stderr, and keeps the five newest files so the diagnostics directory does not grow without bound. Measured on r0.50: closing the session wrote a 245 KiB export 2 s after the interface closed. |
| `0021` | **Measure the two halves of the frame handoff.** Nothing in the series says where the frames a run loses between the streamer's notification and the surface's delivery go, so `presentWaylandFrame` and `pullWaylandFrame` count both halves per 5 s and print one line: `pulls= presented= stale= noframe= handoff-us= max-handoff-us= commit-us= max-commit-us= max-gap-ms=`. `handoff-us` is the Rust call that hands a DMA-BUF frame over, `commit-us` is attach/damage/commit plus the flush the compositor answers. The counters sit next to `0012`'s pinned handoff frame and print only under `OPENNOW_LOG_PRESENT`, so the patch adds counters and no work on a path that is not already logging. Marker: `opennow-present cost`. Measured on the r0.51 session that held ten 20 s windows (`c53.log`): `pulls=566-589` a second against `presented=273-287` and `noframe=290-304` with `stale=0`, `handoff-us=16.8-21.4k us` summed over the pulls of a 5 s window (29-38 us per pull, max 0.18-3.8 ms on one) and `commit-us=55k-82k us` summed over its ~289 commits (190-284 us per commit, max 0.5-6.2 ms). The two halves together are ~0.22-0.32 ms of the 16.7 ms a 60 Hz frame has, so the present path is not where the dip is. What the run lost is 3.7 frames a second between the notification (`native avg 59.92`) and the drain (`delivered avg 56.21`), against 11 564 commits at ~57.8/s and ingress of 1197-1201 video packets per 20 s (~60/s), and the longest gap between pulls was 77-130 ms. |
| `0022` | **Name the pull clock's starvation in the pull that just made it.** r0.51's counters put the pull clock at 113-118 pulls a second against the 125/s its own 8 ms timer asks for, and every 5 s window holds one gap of 77-130 ms, so the clock loses about a tenth of a second once a window - while no single handoff took more than 3.8 ms and no single commit more than 6.2 ms, that loss is not inside either half of the handoff. `reportHandoffCost` now attributes every gap over 16 ms to the outcome of the pull that preceded it (`gaps16+=`, `after-presented=`, `after-stale=`, `after-noframe=`, `after-other=`) and prints `opennow-pull-gap: gap-ms=... previous=...` for every gap over 64 ms, so the instant sits in the log beside the present and input markers. Starvation after a **present** means the commit's flush waits on the compositor's swap reply, the same reply that answers `flushFailed ~= committed` and the `buffer_fenced_release` assert; starvation after a pull that found **no frame** means this thread is owned by something else and the pull clock is only its scheduler. Counters only, gated on `OPENNOW_LOG_PRESENT`: five statics, a switch on the previous outcome and one line per rare gap. Markers: `opennow-present cost` (the extended line), `opennow-pull-gap`. Measured on r0.52 in the session `ab68-2.log` (107 s of play, `media-ingress elapsed_ms=107180 video=5959 audio=19950`, p50 52.5 fps): `pulls=533-570` and `presented=260-267` with `noframe=273-285` and `stale=0` over a 5 s window, so the clock ran at 107-114 pulls a second; `handoff-us=17.5-23.9 ms` summed over those pulls (32-44 us each) and `commit-us=99.6-117 ms` summed over the ~262 commits of the same window (380-448 us each, 3.7-6.6 ms on the worst one), while `max-gap-ms=141-175` and `gaps16+=5-10`. The attribution answers the question the patch asks: `after-presented=2-8` of those 5-10 gaps is the dominant kind, `after-noframe=1-3` and `after-stale=0`. The pull clock is starved **after a presentation**, not inside the handoff and not inside the commit, and 0021's two halves together cost 0.4-0.5 ms of the 16.7 ms a 60 Hz frame has. What the starvation waits on is the compositor's reply to the commit, the same reply that answers `flushFailed ~= committed` and the `buffer_fenced_release` assert. |
| `0023` | **Stop closing the descriptor of a frame the surface already shows.** `present()` closed the DMA-BUF descriptor whenever `attachFrame` returned false, and `attachFrame` returns false a second time for a picture whose DMA-BUF is already in the surface's `wl_buffer` cache: that frame names the screen again without sending a request, so libwayland never takes ownership of its descriptor and the surface's cache is what keeps the object alive. The Oct-31 logs say what that costs. The streamer reports `opennow-diag: dma-buf export handles kept alive at handoff: retained=1`, then `opennow-diag: linear DMA-BUF buffer import failed: Vulkan operation failed: fstat on DMA-BUF failed: Bad file descriptor (os error 9); falling back to the CPU copy`, then `opennow-diag: cpu-copy frame 1920x1080 range=Limited matrix=Bt709 chroma=Center` for the rest of the session, because `frame_producer.rs` keeps the probe's answer once it fails (`buffer_import_probe = Some(false)`): one dead descriptor costs 3 MiB of NV12 memcpy per present for the rest of the run, and no import ever again. The descriptor that was closed is the one the *decoder* still holds for that same DMA-BUF in the same process, and the EBADF is the next pull of that picture asking for it. The close now belongs to the frames that never reached the surface at all (`surfaceTookFrame` decides it in `present()`), and the close of a frame the surface accepted but no request carried sits in `attachFrame`'s `!buffer` branch, which is the only place that knows the difference. Measured on the r0.52 logs that carry the failure verbatim, and measured on r0.53 at 1280x720 the next morning: the driven session took the import route (`opennow-diag: linear NV12 DMA-BUF buffer import route chosen by the device, not the interface report`, no EBADF anywhere) and presented 17 s at p50 52.2 before the descriptor limit of open item 9 ended it, while the idle session still logged `fstat on DMA-BUF failed: Bad file descriptor (os error 9)` and the `opennow-diag: cpu-copy frame 1280x720 ...` line for the run. **Superseded by `0026`.** With `0025` making a dead descriptor cost one frame instead of the whole run, the close belongs in `present()` again: leaving it out is what grew `/proc/PID/fd` from 39 to 1024 in 901 presented frames and kept ~900 capture objects per crashed run in the V4L2 pool (open item 8).Markers: `wayland-video fps`, `opennow-diag`. |
| `0024` | **Raise the process's file-descriptor limit before the stream starts.** The Oct-31 driven session on r0.53 at 1280x720 ended at `wayland-video fps=54.5 committed=901 dropped=0` with `[AVHWFramesContext] Failed to export memory plane 0 (16): Too many open files (24)` and the FFmpeg hint `Consider setting extra_hw_frames to a larger value (currently set to 6, giving a pool size of 10)`, while a sampler of `ls /proc/PID/fd | wc -l` every 5 s showed the open-descriptor count climb from 39 to 1024 = `sh -c "ulimit -n"` with the frames: about 1.13 descriptors per presented frame, so the whole 60-second limit bought 17 s of picture. That is 0023 working as intended - a present the surface already has a cached `wl_buffer` for sends no request and closes nothing, and the streamer keeps the decoder's own handles alive - so each such present leaves one descriptor open that nothing else will close. What leaks is descriptor numbers, not capture memory: the dma-bufs behind them are the ten-odd capture surfaces the V4L2 pool reuses, and VmRSS stayed at 188-238 MB across the run. The board's soft limit is 1024 and its hard limit 524288, so raising the soft one at startup is legal: `main()` raises it to 65536 (about 20 minutes of play at 52 fps) and logs `opennow-fd-limit: the file descriptor limit was raised from %llu to %llu` once. The fix the FFmpeg hint suggests - a larger `extra_hw_frames` pool - is not taken: a 30-entry pool wants 30 capture surfaces at once and this boot's `free_cma:21784kB` cannot give them even at 720p. Deployed on Oct 31 as r0.54: both launch attempts of that evening log `opennow-fd-limit: the file descriptor limit was raised from 1024 to 65536` within the first seconds of the run, so the raise itself is on the board and verified; the long held stream and the ESC-against-a-live-window check still have to wait for a boot whose capture pool answers the allocation at all (open item 8). Markers: `opennow-fd-limit`, `wayland-video fps`. |
| `0025` | **Ask the descriptor before trusting the import probe.** The r0.53 logs show what one dead descriptor cost: `opennow-diag: linear DMA-BUF buffer import failed: Vulkan operation failed: fstat on DMA-BUF failed: Bad file descriptor (os error 9); falling back to the CPU copy` sets `buffer_import_probe = Some(false)` and keeps that answer for the rest of the run, so every later present copies 3 MiB of NV12 on the CPU and no import is tried again; the same `Some(false)` also closes the `dmabuf_buffer_import_enabled` gate. A descriptor is not the device's opinion: it is a per-frame export the stream window may already have closed. The streamer now asks the descriptor first (`fstat` on the first object's fd); a dead one takes the CPU copy for that frame and leaves the probe untouched, and a descriptor that dies between that check and the import is treated the same way (`Bad file descriptor` in the error does not set the probe). What the probe records is now only a real verdict about the graphics device. Markers: `opennow-diag`, `wayland-video fps`. |
| `0026` | **Close the descriptor of every frame no request carried.** This is 0023's close placement reversed, and 0025 is what makes it safe: `present()` closes every frame that did not go into a request, the frames that reuse a cached `wl_buffer` included, so the dma-bufs are released as the decoder drops them instead of accumulating one descriptor and one capture object per present. The measured cost of not doing that: the driven r0.53 session at 1280x720 grew `/proc/PID/fd` from 39 to 1024 in 901 presented frames (~1.13 per present) and each crashed run left ~900 capture objects (about 1.2 GB at 720p) in the V4L2 pool; the r0.51/r0.52 sessions that closed these descriptors ran 577 s with zero capture failures. A descriptor the window closes now costs one CPU-copied frame at worst, because `0025` no longer lets that answer poison the run. Markers: `wayland-video fps`, `opennow-diag`. |
| `0027` | **Read `wl_display_flush` as the byte count it is.** `flushDisplay()` tested `wl_display_flush(m_display) == 0` for success, but libwayland returns the number of bytes it sent: zero only when nothing was queued, a positive count when the commit went out, and -1 with errno on failure. Every present commits and flushes bytes, so the success arm was never taken: `m_flushFailures` climbed at the present rate (`flushFailed=2014` at `committed=2013` in the r0.55 1080p session), `forgetDeferredFds()` was never reached on a real flush, and the first warning reported whatever errno an unrelated call had left behind (`wayland-video: flush failed: File exists (display error 0)`). Testing the sign instead makes a successful flush forget the descriptors libwayland already closed, so the 240-entry list measures a compositor that really stopped reading instead of counting presents. Markers: `wayland-video fps`, `flush failed`. |

#### The stream window

A running stream has two surfaces on the board's Weston: the application's own
window, which draws the interface, and a toplevel of OpenNOW's own that carries
the decoder's frames. The second is created with `xdg-shell` and
`linux-dmabuf`, and OpenNOW owns its lifetime. Since patch `0007` the surface
has a **connection of its own** rather than Qt's: Qt 6 dispatches its display on
an event thread of its own, and libwayland allows one dispatcher per queue, so
sharing one connection made the video path race Qt on every event and the
VeriSilicon WSI saw a second release fence for a buffer it was still using.

| Mode | How it behaves |
| --- | --- |
| Fullscreen, the default while a stream runs | The whole screen, and the window owns the pointer, the wheel and the keyboard. `F11` leaves this mode. |
| Windowed | A decorated 1280x720 toplevel, scaled with `wp_viewporter`. It can be moved and minimised, and it keeps forwarding input, scaled to its size. `F11` returns to fullscreen. |
| Closed | The compositor's close button, or the trigger below, ends the session exactly like the application's own stop. |

The path is on by default. `OPENNOW_WAYLAND_VIDEO=0` restores upstream's
presentation inside the Qt scene graph, which is what an older package does and
is worth having when comparing.

#### The compositor's descriptor budget

Weston 13 does not close the dma-buf descriptors of a buffer it has imported.
That is invisible to a client that presents a handful of buffers and fatal to one
that presents fifty a second: the compositor holds one descriptor per buffer
until it reaches its limit of 1024, and from then on it cannot take another
descriptor from any client. On this board the limit arrived after about twenty
seconds of video. The first symptom was not the picture: the compositor could no
longer send the keymap of the next stream window and dropped the client, and the
frame already in flight arrived without its descriptor and came back as
`invalid arguments for zwp_linux_buffer_params_v1.add`, which Qt reports as a
fatal Wayland error and turns into an abort. Restarting Weston clears the count;
the numbers here come from a freshly started compositor.

The series keeps the compositor out of that corner by giving it fewer objects
to hold. The hardware decoder cycles through a small pool of capture buffers, so
the same dma-buf returns frame after frame: the surface recognises it by the
device and inode of the descriptor together with the layout it was imported with,
and re-attaches the `wl_buffer` it already built instead of creating a new one. A
session now creates as many buffers as the decoder's pool holds, and the
compositor's descriptor count stays flat instead of climbing once per frame.
Buffers are destroyed only after the compositor has released them.

To watch it on a running session:

```
for f in /proc/$(pgrep -x weston)/fd/*; do readlink $f; done | grep -c dmabuf
```

#### Input

The stream window stands in front of the interface, and a compositor gives the
keyboard to the front window. Weston 13 does not implement `xdg-activation`, so
an application cannot ask for the focus back: while a stream ran, the interface
window was inactive, and OpenNOW's own input path — which only forwards events
while its window is active — dropped every pointer and key event. Only `F11`
arrived, because the stream window handled that key itself.

Patch `0007` (the old `0027`/`0034`) reads input where the compositor sends it.
The stream window takes the whole surface as its input region, keeps a `wl_pointer`
next to the `wl_keyboard` it already had, and forwards positions, buttons, the
wheel and keys to the host through the streamer's input API. Positions are
expressed in the frame's own pixels — the surface carries the whole frame, so
fullscreen is 1:1 and the windowed form is scaled — keys are translated from
Linux key codes to the Windows virtual keys the host speaks, and the local
cursor is hidden because the game draws its own. Keys still held when the window
goes away are released first, so the game is not left walking. Since patch `0009`
the listener set is driven by `wl_seat.capabilities` rather than by assumption.

The video target links Qt Gui only, so it does not call the streamer itself: it
takes a small table of input hooks that the render callback fills in with the
streamer's input calls. That keeps the Rust FFI and Qt Quick out of a target that
has no business including them.

Three development aids exist because Weston 13 refuses the screen capture
protocol and the stream window is not part of the interface window:

| Aid | Effect |
| --- | --- |
| `OPENNOW_FRAME_DUMP=<dir>` | Write the newest decoded frame to `<dir>/frame.ppm`, at most once a second, converted on the CPU. |
| `touch /tmp/opennow-grab` | Write the interface window to `/tmp/opennow-grab.png`. |
| `touch /tmp/opennow-close` | Do what the compositor's close button does to the stream window. |

#### Why the Ui looks right on the STM32MP257F

The board did not only render slowly, it rendered wrong. The sign-in card and its
primary button were QML layers (`layer.enabled: true`) with a `MultiEffect`
drop shadow, and the VeriSilicon Vulkan driver segfaults in `QSGRhiLayer::grab()`
when the card re-renders while the QR panel appears. They are drawn directly now,
so the card no longer renders itself into an offscreen buffer on every repaint.
The driver's own `libwayland` error is taken off the console, where it read like
the application failing.

The renderer knob matters for how the Ui *feels*: on Vulkan the driver compiles
the Qt Quick shader for every material on the fly, so any surface that has not
been drawn before stalls for about a second the first time it appears. OpenGL
avoids that. Since patch `0010` the streaming path no longer depends on Qt's
device at all, so `OPENNOW_RENDERER` moves the Ui and the video keeps its own
buffer-export Vulkan device.

#### Measured stream throughput

The stream no longer travels through the Qt scene graph. The decoder's DMA-BUF
is handed to a Wayland surface of its own, which the compositor composites — and
scans out when the format allows — without the GPU converting a pixel. It is the
same path YtGst reaches through `waylandsink`.

With `OPENNOW_LOG_PRESENT=1` both halves log themselves, about once a second:

```
wayland-video fps=60.0 committed=22868 dropped=0
opennow-present fps=49.6 render=30.0 swapped=0
```

`wayland-video fps=` is how often a decoded frame reached the compositor,
`dropped=` counts frames that were superseded before they could be shown, and
`committed=` is the running total. `opennow-present render=` is how often the
interface repainted; the video no longer depends on it.

FPS by pipeline stage for this build (`r0.45`, 14-patch series), each number
measured from its own counter rather than inferred:

| Stage | Rate | Source | Notes |
| --- | --- | --- | --- |
| wire (packets in) | **60.07 fps** | `nvst-video elapsed=… frames=21107 → 22909` over 30.0 s | full 60 delivered |
| ingress (accepted) | **59.85 fps** | `media-ingress video=22269 → 22868` over 10.0 s, `dropped=0 paused=0` | nothing rejected |
| decode | **59.5/s** | `decodeTimings.submissionsTotal 7122→7721→8316`, `unmatchedOutputs=0`, `inFlight=0..1` | the decoder keeps up |
| queued handoff | equal to decode | `frameStageTimings.assembled==admitted==queuedAck`, `undeliveredFramesTotal=0` | no backlog |
| present to screen | **49.6 presents/s** | `wayland-video fps=… committed=…` from a tee'd run | the only gap |
| audio in | **200.1 packets/s** | `media-ingress audio=74739 → 76740` over 10.0 s | steady |

The 10 fps gap is the presenter: patch `0012` pins **one** handoff frame, so the
next decoded frame overwrites the pending one. That overwrite is not counted as
`dropped`, which is why every "dropped" counter reads 0 while the screen still
shows about 49 fps. `OPENNOW_HANDOFF_RETAINED` is the untested lever for it —
and the measured note above says why a wide ring breaks the stream instead of
closing the gap.

Renderer and memory at the same phase, same build, same game:

| Renderer | Phase | `used` | `available` | `opennow-qt` RSS | Wire FPS | Comment |
| --- | --- | --- | --- | --- | --- | --- |
| — (cold boot, no app) | boot | 237 MiB | 1.5 GiB | – | – | `MemFree` 1438 MiB, `Cached` 147 MiB |
| OpenGL | loading | 483 MiB | 1.3 GiB | 253 MB | – | cheapest state seen |
| OpenGL | streaming | 806 MiB | 961 MiB | 265 MB | 59.9 | `Pss` 213 MB, `Pss_Dirty` 144 MB |
| Vulkan | loading | 596 MiB | 1.1 GiB | – | – | +113 MiB over OpenGL at the same phase |
| Vulkan | streaming | 891 MiB | 876 MiB | 437 MB | 60.1 | +172 MB RSS over OpenGL; ~120 MiB above the cliff where the DMA pool failed before |
| — | after quit | 281 MiB | 1.5 GiB | – | – | only ~44 MiB residue vs cold boot; the app's own ~300 MiB returned |

`weston` stays at 37.6–39.4 MB in every case, so none of this is the compositor.
`drop_caches` (root) returned 146 MiB of `MemFree` but only 29 MiB of
`MemAvailable` — the rest are library pages still mapped by running processes
and cannot be freed.

**OpenGL is the thing to use on this board**: it survives `F11` better and costs
~172 MB less app RSS at the same phase.

The CPU budget at 100 s of play on the 2 cores (load average 2.36–3.47), by
thread, which is where the budget actually goes:

| Thread | CPU |
| --- | --- |
| `opennow-qt` (main) | 188 s |
| `QSGRenderThread` | 100 s |
| `opennow-nvst-ev` / `-vi` / `-mj` | 84 / 63 / 50 s |
| `opennow-linux-a` (audio) | 37 s |
| `opennow-linux-v` (video) | 32 s |
| total, 31 threads | 632 s |

`QSGRenderThread` at 100 s is scene-graph work in the interface window. That
thread is the largest single borrower from the audio playout, and patch `0013`/
`0014` is what puts its clock under our control.

Audio telemetry, identical in both renderers:

| Metric | Value | Meaning |
| --- | --- | --- |
| `audioPacketDropCount` / `audioDiscardedMs` | 0 / 0 | the wire is clean |
| ingress audio | 200.1 packets/s | arriving on time |
| `queue-dropped` events | ~2/s (2616 → 2858 in ~2 min) | OpenNOW's low-latency play queue throws stale packets |
| `AudioJitterStats.underflows` | 3–5 | playout falls behind |
| `AudioJitterStats.reordered/stale/overflow/invalid` | 0 | no reordering, no corruption |
| `audio-jitter target_ms` | 45 (`queued=14`) | queue depth in this session; the earlier one ran at 145 |
| audio thread name | `alsa-pipewire` | ALSA + PipeWire shim; `aplay` at the console uses the plain kernel card |
| `pipewire` / `pipewire-pulse` CPU | 1.2 % / 0.0 %, no `underrun` in its log | the sink is not starved |

So the fault is OpenNOW's play queue running behind, not the sink, not the
renderer and not a background daemon: stopping `netdata.service` (4 processes,
~50 MB) changed nothing, `queue-dropped` still advanced 80 events in 40 s.

The queue running behind is what patch `0016` removes: the sink behind the queue was
opened on a name the card does not answer, in a mode that was never started, for a
format the card refuses. r0.47's session prints the sink's own line and logs no
`queue-dropped` event, so the arriving audio goes out as it arrives rather than being
thrown away; the two lines below are the r0.45/r0.46 state, kept for the comparison.

Errors and warnings seen in a session, verbatim, with what is known about each:

| Line | Count | Assessment |
| --- | --- | --- |
| `opennow-qt: api/gc_egl_wayland.c:2699: buffer_fenced_release: Assertion 'buffer->release_fence_fd == -1' failed.` | once per `F11` press during the stream | vendor VeriSilicon WSI assert; ends the session (also seen at `src/wsi/gc_wsi_surface_wayland.c:611`, same assertion) |
| `wayland-video: flush failed: File exists (display error 0)` | ratio `flushFailed/committed = 0.985` | our flush is the second flush of the same connection (`display error 0` = connection healthy); picture stays smooth and `dropped=0`, so this is a double-flush artifact, not lost frames and the counter's own cause: `flushDisplay()` tests `wl_display_flush(...) == 0` for success while libwayland's flush answers 1 on success, so every healthy present takes the failure branch, `errno` is whatever an earlier call left (`File exists`), `wl_display_get_error` answers 0 and `flushFailed ~= committed`. The descriptors stay correct because the branch it takes is the one that keeps them open, and the list is bounded by its own 240-entry guard; the count is the artifact, not the frames. |
| `QObject::killTimer: Timers cannot be stopped from another thread` | 1 per session | cross-thread timer stop in the interface path |
| `NVST dropping oversized UDP packet: STUN parse error: STUN packet missing priority` | 3 per session | an STUN packet the parser rejects, not video data |
| `unknownCommands=1 lastUnknownCommand=Some(526)` | 1 | one NVST opcode OpenNOW does not implement |
| `rfiSent=7 rfiDropped=47`, `fecRecovered=3`, `gaps=1`, `replay=14`, `nackRecovered=12` | per session | intra-refresh requests refused; routine FEC recovery |
| `failed to get EGL extensions` (`weston.log`) | 1 | benign weston probe |

Every one of these is greppable with `grep -ac` in the app's own logs:
`buffer_fenced_release`, `flush failed`, `wayland-video fps=`,
`opennow-present fps=`, `opennow-present: interface window swap interval %d
requested`, `nvst-video elapsed=… frames=`, `media-ingress`, `audio-jitter`,
`queue-dropped`, `embedded Vulkan buffer-export device (no video decode)`.

Earlier eras, kept because they explain the shape of the current series:

- The same session presented 6-8 frames a second while the video went through
  the Qt scene graph, and about 28 once the decoder's DMA-BUF was imported into
  that scene graph as a Vulkan buffer. Two GPU passes — NV12 to RGBA, then a
  sampled texel copy — cost more than everything else in the frame together.
- At ~756 MiB available the decoder failed `hantro-vpu … dma alloc of size
  3657728 failed` together with `page allocation failure: order:10, mode:
  0xcc1(GFP_KERNEL|GFP_DMA)`. That is the cliff the memory table above is
  measured against, and it is why the kernel must reserve CMA.
- `OPENNOW_FRAME_DUMP` is not free. Converting one 1080p frame from NV12 to RGB
  on the two A35 cores costs about a second, so a session with the dump enabled
  presents about one frame a second. It is a diagnostic, not a setting.

For comparison, the shell's own profiler (`--performance-report`) on an idle
home screen has a cost that is linear in the *window area*, about 25 ms fixed
plus 25 ms per megapixel: p50 74.7 ms at 1920x1080, 62.6 ms at 1600x900,
31.4 ms at 1280x720 and 26.5 ms at 960x540 on Vulkan (71.7 ms and 31.1 ms at
the two ends on OpenGL). `weston.ini`'s stock `repaint-window=100` is
responsible for the 1080p tail: with `repaint-window=1` the 1920x1080 p95 falls
from 1233 ms to 117 ms, while the 720p and 540p medians do not move. That
belongs to the image's Weston configuration, not to this layer, and is not
shipped as a workaround.

Throughput per application, same board and session conditions:

| Application | Video path | Stream | Presented fps | Measured with |
| --- | --- | --- | --- | --- |
| OpenNOW | Own Wayland toplevel, DMA-BUF to the compositor | 1280x720, fullscreen | 54-55 (`r0.33`), 49.6 presents/s (`r0.45`) | `wayland-video fps=` |
| YtGst | GStreamer `waylandsink`, own subsurface | 1920x1080 | not measured yet | GStreamer `fpsdisplaysink` |
| NanoBrowser | QtWebEngine | - | not measured yet | compositor frame count |

OS-side levers, for completeness — none of them live in this layer, they live in
`meta-watermelon-wine`:

| Lever | State |
| --- | --- |
| `cma=512M` | live in `/proc/cmdline`. The layer says 256M; the board was hand-raised, because the decoder's 16 MB contiguous request fails at 256M: `cma: cma_alloc: linux,cma: alloc failed, req-size: 394 pages, ret: -12` |
| `transparent_hugepage=madvise` | live in `/proc/cmdline` and reported `[/madvise]`; hand-edited into `/boot/extlinux/extlinux.conf` (backup `extlinux.conf.bak-pre-thp`) plus `UBOOT_EXTLINUX_KERNEL_ARGS:append` in the machine conf for the next flash. While streaming: `HugePages_Total: 0`, `Mlocked: 0` |
| `netdata.service` | stopped at runtime; `PACKAGE_EXCLUDE:append = " netdata modem-manager-modemsinifo "` staged in `st-image-weston.bbappend` for the next flash |

### YtGst

| Variable | Default | Purpose |
| --- | --- | --- |
| `YTGST_VIDEO_DECODER` | `v4l2h264dec` | GStreamer decoder element |

---

## Running on the target

### OpenNOW

Start via the `opennow` launcher, which normalises the runtime environment: this
image's weston runs with `XDG_RUNTIME_DIR=/home/weston`, which is where its Wayland
socket lives, so that value has to be preserved rather than replaced.

```sh
XDG_RUNTIME_DIR=/home/weston WAYLAND_DISPLAY=wayland-1 opennow
```

To keep every counter greppable in one session, which is what every number
above was measured from:

```sh
export OPENNOW_RENDERER=opengl
export OPENNOW_LOG_PRESENT=1 OPENNOW_LOG_FRAME_RATES=1
opennow 2>&1 | tee -a /home/weston/.runs/g1.log
```

In the app: pick the backend your machine offers and codec **H.264**.

Which decode path is available depends on `OPENNOW_CARGO_FEATURES` and on the
video hardware:

- `linux-ffmpeg-bundled` (this layer's default): **FFmpeg + V4L2 Request hwaccel**,
  the stateless path the STM32MP25 VPU offers
- default build: **stateful V4L2 memory-to-memory**, as found on most
  hardware video decoders that expose `V4L2_CAP_VIDEO_M2M`
- `linux-vaapi`: **VA-API → DRM PRIME → stateless V4L2 codec node**

Requires Wayland and Vulkan in every case.

### NanoBrowser

Run `nanobrowser`. As root, disable the Chromium sandbox:

```bash
QTWEBENGINE_DISABLE_SANDBOX=1 nanobrowser
```

#### The `r0.74` slim play set: the clocks and the tallies out, and the window opens on the first try (Nov 6, board `weston@192.168.1.33`)

`r0.74` keeps the 35 patches a human player needs and retires the 11 that were
only ever a witness or a clock for a seat nobody was driving; the retired files
sit beside the series in `recipes-apps/opennow/files-unused-playset/` so the
record keeps them while `SRC_URI` does not. Dropped clocks, five: `0015` pressed
the Start button on a clock of its own, `0035` took a picture of the stream when
asked for one, `0041`/`0042` answered the PLAY notice and the signup notice on
clocks of their own, `0045` kept an unattended seat inside the game with an
Enter every minute - all of them exist to keep a session alive while nobody was
at the seat, and a player who is watching the window clicks those notices
instead. Dropped tallies, six: `0020` wrote the diagnostic export when the
interface exits, `0021`/`0022` measured the two halves of the frame handoff on
the pull clock and named the starvation it saw, `0038`/`0039` counted what the
drain side did with the window's input and named the queue the embedder feeds,
`0046` named the colour description the stream carries - all print-only, their
numbers fed the write-ups above and never changed what the seat did. Two groups
are kept whole because dropping one half breaks the other half's context: `0019`
and `0029`, the second reversing the first, and `0032`/`0033`, the pop-up's Enter
clock and the hold that makes the host read it, which stay because `0040`'s
capture-owner hunks edit that same clock code, and because the pop-up the game
opens behind the window is a notice a human can miss while the host still needs
it answered. `0040` was regenerated against this series: the full chain took the
`AtomicUsize` import from the dropped `0039`, so `0040`'s own call counter had no
name for its type and `do_compile` stopped at `cannot find type AtomicUsize` in
`embedded_input.rs:27`; the section was re-diffed from the real pre state and now
carries `use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};` itself,
which is why `do_patch` applies all 35 patches with no fuzz and no offset.
Measured on the board: the deb's md5 matched host to board
(`d419baae6e48390f35b9ecd28ea19d45`), `dpkg -i` took `r0.74` over `r0.73`, and
the bare launch with no clock env at all reached the window on the FIRST attempt
of the retry loop (`p74-75.log`, app pid 253634, `wayland-video: stream window
created` 10:05:03 after 148 s), ran the stream at 55.5 and 50.5 fps with
`committed=4189 dropped=0 planes=0 dead=0 flushFailed=0`, printed `0040`'s
naming 5 times, printed no clock press at all, and took none of the death
family - no `invalid fence fd`, no `Bad file descriptor`, no vendor assert, no
`Protocol error`. `opennow-server-end-closes-window: the server ended the
session; unmapping the stream window` came 86 s later at 10:06:29 (this boot's
cratering, not the app's), and both `opennow-qt` and `opennow-core` were still
alive at 10:19 after the window's close, so the slim set starts, streams, and
survives the close on its own.

#### The `r0.73` colour naming reads out on a live session, and says the seat's own numbers were already right (Nov 5, board `weston@192.168.1.33`, this boot)

`0046` of `r0.73` is print-only: once a session, at the DMA-BUF export and again beside the CPU-copy diagnostic, the streamer prints `opennow-diag: stream colour: pixels=... range=... matrix=... primaries=... transfer=... chroma=...`, the colour description the stream carries; no colour is converted. The session that ran 40 minutes on this boot (`probe-56.log`, degraded at 0.5-0.6/s against this boot's four `GPU[0] core0 hang`) prints the line once and it reads `pixels=Nv12 range=Limited matrix=Bt709 primaries=Bt709 transfer=Sdr chroma=Center`: the stream is limited-range SDR on BT.709 primaries end to end, and the seat's `nv12_to_rgb` uses BT.709 coefficients, so the conversion already matches what the stream says it carries and nothing seat-side is owed. The frame the seat handed over measures the same from the other side: the session's `frame.ppm` is 1920x1080 with mean luminance 45.3/255, `minL=0 maxL=255`, 0.62% of pixels at or below 3 and 8.26% at or below 16, and channel means R59.4 G37.4 B82.8 - the blacks reach true zero at the seat, so the seat's limited-range-to-full-range move happened before presentation and the darkness the TV reads is the content's own, not the handoff's. A wrong-looking picture is then the set's own guess: weston answers the panel (EDID `TCL SMART TV`), attempts `EOTF mode: SDR` and picks `stock sRGB color profile` - whose primaries are BT.709, the same as the stream declares - on a surface `XRGB8888 (0x34325258)`, while this build has no colour interface at all (`dm_transfer`, `DM-Transfer` and `colourspace` grep 0 in `/usr/bin/weston` and in `libweston-13.so.0.0.1`), and the handoff stays the legacy `zwp_linux_buffer_params_v1_add`, which has no room to name primaries or transfer. A seat-side conversion would be `meta-watermelon-wine` territory; the remedy for a wrong-looking picture is the set's picture settings. The NV12 plane dump the earlier colour measurements read (`OPENNOW_DUMP_NV12`) lands no files on r0.73 any more - `probe-56` carries the PPM dump, and no `.probe` directory holds `nv12.y` or `nv12.uv` - so the plane-level black point is owed a fresh witness.

#### `r0.72`'s unattended session survives the free tier's whole hour with nothing dying (Nov 5, board `weston@192.168.1.33`, fresh boot)

The board was rebooted (by the seat's owner, at their request: open item 8's cratered half clears only that way), and on the fresh boot the retry loop reached the stream window on its third attempt (`hour` 42 and 43 stopped at 480 and 479 bytes, `hour` 44 streamed), `dmesg` carrying two `GPU[0] core0 hang, automatic recovery.` for the whole run against the 31 the cratered boot had collected. Nobody sat at the keyboard: the two notice presses and the hour's keep presses are all the seat's own clocks.

The window opened at 10:56:54 and the surface's own line reads, one second later, `wayland-video fps=56.1 committed=58 dropped=2 planes=2 dead=0 flushFailed=0`; an hour later, at 11:56:54, the same line reads

```
2026-11-05T11:56:54.076 info default: wayland-video fps=49.4 committed=178233 dropped=3246 planes=3246 dead=0 flushFailed=0
```

so the hour is 3,599 s of the game at 42-56/s and 178,233 presents, and the whole log carries zero of `invalid fence`, `Bad file descriptor`, `buffer_fenced_release`, `safe_close` and `The Wayland connection experienced`, with every one of the 3246 refusals answering as `planes` and none as `dead`. What closed the session was the free tier itself, one second after the hour:

```
2026-11-05T11:56:55.252 info default: opennow-server-end-closes-window: the server ended the session; unmapping the stream window
```

which is `0031`'s own path, and the app stayed alive through it (the next stills read the queue page rather than a desktop) while the keep clock kept its last presses going to 12:00:32, its 60th and last answer.

Both halves of `0044` and `0045` that open item 19 said were owed are measured here. The app's own descriptor table stayed flat across the hour - `live=98 highest=4111` 4.5 minutes in, `live=101 highest=4112` at 16, 32 and 53 minutes, and `live=75` after the window was unmapped - against the r0.70 session's `live=1303 highest=5312` at 17 minutes, which is the close doing its job: refusals cost one present and no longer cost one descriptor. The compositor's side stayed flat too, `pid=1277 fds=96 dmabuf=56` at 4.5 minutes, `fds=99 dmabuf=59` at 32 and `fds=97 dmabuf=56` at the hour, against the cratered boot's `fds=252 dmabuf=217` - the surface's `wl_buffer` cache and the close together keep the capture objects to a handful a session instead of 24. And the keep clock's presses crossed the wire all the way: `keep-alive enter clock armed: 270000 ms then 60 presses at 60000 ms`, 60 answers printing one per minute from 11:01:24 to 12:00:32 with `keep-alive held for 120 ms and released` after each, the drain reading `keys=6 absolutes=2` at the first of them and the embedded tally `keys=127 moves=120 absolutes=774` at the 58th - and it was that traffic, not the app, that kept the unattended seat inside the game, which the same item measured ending 14 min in through `0031`'s close when the seat carried only the two notice presses.

#### The refusal's close, the unattended hour's clock, and where the starved pool's leak actually lives (Nov 4-5, board `weston@192.168.1.33`)

`0044` of `r0.71` closes what `0043`'s band made legal - the duplicate of a frame the surface refused - and splits the refusal counter. The r0.70 session had refused 2989 of 150,129 presents and walked its descriptor table with them (`live=1303 highest=5312` at 17 minutes against `live=103 highest=4113` at 8), and each of those duplicates pinned one capture buffer of the 3657728-byte pool, which is how that session ended 52 minutes in right after the surface's own refusal line. The r0.71 session measures the counter split working: it presented at 39-51/s for 4.5 minutes and its line reads

```
2026-11-04T20:45:08.056 info default: wayland-video fps=40.8 committed=6189 dropped=92 planes=92 dead=0 flushFailed=0
```

every refusal answering as `planes` (the pool retired the picture across `0011`'s one-frame handoff ring, the same starvation as open item 8) and none as `dead`, with 324 of `could not be exported as a DMA-BUF` in the same log; it then ended at 20:47:36 with the vendor's `api/gc_egl_wayland.c:2699: buffer_fenced_release: Assertion 'buffer->release_fence_fd == -1' failed`, the third reading of the family, while `opennow-present fps=0.0 render=0.0 swapped=0` says the interface window itself presented nothing.

What the leak is leaking turned out not to be our side at all. A census of the seat's own processes found the compositor holding the capture objects: `pid=2698 comm=weston fds=252 dmabuf=217`, against 11 fds and 3 `/dmabuf:` for each seat helper (`weston-keyboard`, `weston-desktop-*`) - about 24 per session of our surface's own `wl_buffer` cache, each pinning 3657728 bytes, and after about nine sessions this boot was down to `free_cma:10206` against a request for one contiguous 3657728-byte block. Restarting the seat's own session (with consent, `systemctl restart weston-graphical-session.service`) closes them with the compositor and the state comes back: the new compositor reads `pid=350600 comm=weston fds=38 dmabuf=3`, and `free -m` went from 560 MB free to 1337 MB with `used` down from 1055 MB to 253 MB. That is open item 8's runtime cure for the accumulated half, and it is the reason the launches after the restart answer `Failed to create buffer of type 9` no more - the decoder initialises.

What the restart did not cure is the boot's other half: after it, eleven launches in a row (`hour` attempts 27-38, `manual` 40) stop at the same two points, 480 bytes with the app alive or 74 bytes, `opennow-qt` in `wchan=do_sys_poll`, and `dmesg` collects one `[galcore]: GPU[0] core0 hang, automatic recovery.` per launch, 32 of them by the last attempt. Those are the vendor's own core-hang launches the series keeps meeting, and the same item says only a reboot clears the cratered half.

`0045` of `r0.72` arms the fourth clock the unattended hour needs: `OPENNOW_KEEP_ENTER_MS` arms the first press, `OPENNOW_KEEP_ENTER_EVERY_MS` is the spacing (60 s when unset) and `OPENNOW_KEEP_ENTER_TIMES` counts the presses (60 when unset, the free tier's hour), each held for `OPENNOW_KEEP_ENTER_HOLD_MS` (120 ms when unset), pressing the character screen's own Enter and never the corner's Esc, since an Esc there opens the `Exit to Desktop` notice and its next still reads the quit confirmation (open item 16's measured chain). The need is measured, not guessed: the two sessions that carried a player's keys stayed inside the game 35 and 52 minutes, while the unattended session that carried only the two notice presses was ended by the host's own idle timeout about 14 min in through `0031`'s close (open item 16), and a minute is wide enough that this never becomes the 5 s follow-up burst the opening clock measured as wandering into whatever window the host has open. `r0.72` is installed and its markers print (`wayland-video input: keep-alive enter clock armed`, `keep-alive answered (%d left)`, `keep-alive held for %d ms and released`), but no session reached the stream window on this cratered boot, so its presses have not yet been watched onto the wire.

#### The r0.70 descriptor band, and the seat's own play measuring the death family's third reading (Nov 4, board `weston@192.168.1.33`)

The seat is playable, and playing is what the series has never been able to do long enough to see the app's own death come. The session that ran the game for its player (attempt 22, still `r0.69`) answered the consent and PLAY notices with physical keys, streamed at 47-55/s the whole time and reached **103,880 presents**:

```
2026-11-04T18:02:28.305 info default: wayland-video fps=52.3 committed=103880 dropped=0 flushFailed=0
2026-11-04T18:02:28.932 warning default: The Wayland connection experienced a fatal error: Bad file descriptor
```

no assertion and no `invalid fence` line there, one fatal error of a third shape (that session's log was reclaimed for board disk before this reading; the lines above are the ones recorded while it was still on the board). The 3-minute death of attempt 21 in the same release read the family's other two shapes side by side:

```
zwp_linux_surface_synchronization_v1@30: error 0: invalid fence fd
2026-11-04T16:53:20.435 warning default: The Wayland connection experienced a fatal error: Protocol error
```

at `committed=10216 dropped=0 flushFailed=0`, after three clean minutes at 49-55/s.

The line with no timestamp of its own names the interface's own connection, not the stream window's: the string it prints lives in the board's `/usr/lib/libEGL.so` (the vendor's `libEGL.so.1.5.0` holds weston's egl-wayland client `api/gc_egl_wayland.c`), which hands the compositor a **release fence for every swapchain image it presents**, with that fence carried as a file descriptor. The descriptors are the collision, and the app's own table says so: watched for 8 minutes at 47/s during a session that was still alive, it holds **102-104 live descriptors, highest number 104**, and inside that narrow band sit `0` = `anon_inode:sync_file`, `4` = `/dev/galcore`, the driver's 21 `anon_inode:[eventfd]` entries in the low teens, 12 sockets and 37 `/dmabuf:` objects. Every one of the streamer's four `F_DUPFD_CLOEXEC` duplicates asks for the **lowest free number** - `, 3` at the compositor handoff, `, 0` at the Vulkan, SAND and presenter imports - so 55 times a second the exports and the driver's fences occupy the same numbers, and a fence or an import arrives naming one that was just taken back. The three readings are one family: the driver's fence gets a dma-buf's number (`invalid fence fd`), the compositor's read gets a closed number (`Bad file descriptor`), the driver's own release gets the number the streamer stole (`buffer_fenced_release`'s `release_fence_fd == -1`).

`0043` separates them instead of catching them: the four duplications ask for their number at a band of their own, `EXPORT_FD_BAND` = 4096 in the crate root, and the driver and libEGL allocate nothing that high. The r0.70 session measures the separation in the process's own table - the band line prints once, and the table now splits:

```
opennow-diag: dma-buf export fd band: duplicates start at 4096
live=103  highest=4113
```

the driver keeps its 0-104 band and never sees a streamer number, the duplicates reuse a handful of numbers above 4096 rather than walking the table, and 8 minutes into that session at 42-55/s the log carries zero of `invalid fence`, `Bad file descriptor`, `buffer_fenced_release` and `safe_close`. Nothing is buffered and no rate is added: the export still owns its duplicate and the presenter still keeps it until the flush that carries it succeeds, so `0011`'s handoff ring, `0025`'s ask-the-descriptor probe and `0026`'s release ownership are unchanged, and the surface's own refusal of a descriptor whose planes do not fit still answers.

This closes the crash family the recipe was blocked by. The deaths that stay are the ones with no collision in them - `0014`'s swap interval keeps a release from arriving after its image is presented again, and the `galcore` `GPU[0] core0 hang` launches that die before the window are the driver's own recovery, not a shared number.

#### The r0.69 signup clock reaches the character screen, and the third press is what broke in (Nov 4, board `weston@192.168.1.33`)

`0042` (built as `r0.69`, deb md5 `961fdcb99c0fc7d3c06add97166e6a16`) armed the
third notice of the chain with a key of its own, and the attempt that streamed
measured both halves of it - the Main Menu the series has been asking for
renders, and the extra press that was armed for the signup notice is what
broke in:

- the clocks armed together at window creation (`stream window created`, 11:10:00):
  `opening enter clock armed: 60000 ms`, `opening play clock armed: 210000 ms`,
  `signup clock armed: 330000 ms then 0 more at 5s, key 1b`
- the presses went out at +63 s (11:11:03, the consent window's "ok"), +212 s
  (11:13:32, the PLAY splash, held 150 ms and released) and +332 s (11:15:32,
  the signup clock's Esc, held 150 ms and released), and all three crossed the
  gate and rode the wire: `NVST native input drain: … keys=6 absolutes=1`
  for the three presses' two transitions each, 3 `events=[key-down(3)]` and 3
  `events=[key-up(4)]` inside 7 `route=ControlReliable` packets, with
  `buttons=0 moves=0` so nothing else reached the host that session
- the witness stills read the chain: the loading screen 13 s after the PLAY
  press, then the **character screen** 25 s after that - the guardian standing
  with a class card beside it, and the corner's only key hint,
  `Esc Exit to Desktop`. That is the Main Menu the series asked for,
  40 s after the PLAY press
- the third press is what did not belong there: the Esc hit that corner hint,
  and the next still reads `ARE YOU SURE? … quit your current session and exit to
  desktop`, whose options are `Enter Confirm` and `Esc Cancel`, waiting there
  for the rest of the session

So the measured press budget of the cloud boot is TWO Enters - consent and
PLAY - and the Main Menu renders without a third press; the signup notice the
`r0.68` stills named is not what the PLAY press opens next on this path. The
quit notice has an answer if a player ever does press the corner's Esc - a
second Esc, its "Cancel" - but arming only the consent and PLAY clocks never
reaches it, and that is the clean chain the next attempts run.

One rate has to be read in this release: the presented picture advanced at
`wayland-video fps=0.6` (a histogram of 0.4 to 0.7/s across the session) while
the stream delivered `opennow-frame-rate native=59.6/s delivered=0.7/s`, and
the surface's own counters now read `committed=412 dropped=2 flushFailed=116`
- the `r0.67` attempt had read `dropped=0 flushFailed=0`. A failed compositor
flush is the first named reason the window's pull clock is kept off, and it is
open item 17 below, because the seat cannot play at one commit every 1-2 s.

The same release then measured the clean chain to the end of a session: the
retry loop died twice on the known `buffer_fenced_release: Assertion
buffer->release_fence_fd == -1 failed` and the third attempt streamed
(`play-d2d-19.log`, window live from 11:37:31). Its clocks armed the consent
and PLAY presses only - `signup clock armed` never appears in the log because
`OPENNOW_SIGNUP_MS` is unset - and the two presses at +62 s and +212 s
crossed the gate (`NVST native input drain: available=true … keys=4
absolutes=1`) and rode the wire (2 `events=[key-down(3)]` and 2
`events=[key-up(4)]` inside 5 `route=ControlReliable` packets). The witness
stills hold the character screen at +400 s and at +800 s with no quit notice
in sight, and the session ended only when the host ended it
(`server ended the session` 1, `invalid fence` and `buffer_fenced_release` 0
in that session). Two presses are the whole budget the Main Menu needs, and
once it renders the seat's own keyboard drives the game.

That run splits the rate reading, which one session alone could not: the clean
session presents at `wayland-video fps=0.5` to `fps=0.7` while its own
counters read `committed=448 dropped=0 flushFailed=0`, so the slow pull of the
Main Menu refuses and fails nothing at all, while the 116 failed flushes above
belong to the session where a player's own mouse was moving through the queue
(the tally read `moves=539 absolutes=730 buttons=18` there against this
session's `keys=4 absolutes=1`). Two separate things are owed now, not one.


#### The r0.68 play clock answers the PLAY notice, and the chain's third notice becomes visible (Nov 4, board `weston@192.168.1.33`)

The r0.67 stills closed open item 16's question and named the press budget: ONE
Enter per notice, the notices about 135 s apart, and any extra press of the
opening clock wanders into whatever window the host has open (measured twice:
the 24-press and the 12-press attempts both closed the host's own windows and
left a bare desktop). The follow-up answers of the `0032`/`0033` clock are 5 s
apart, so no setting of that clock can hold that shape. `0041` (built as
`r0.68`, deb md5 `46058ccf2936c505f94b3f57ad18a037`) gives the second notice a
clock of its own, beside the opening one and with the same key machinery:

- `OPENNOW_PLAY_ENTER_MS` says how far after the stream window became live the
  PLAY press goes out - unset arms nothing, so an attempt can still answer only
  the first notice
- `OPENNOW_PLAY_ENTER_TIMES` (1 when unset) repeats it at the same 5 s follow-up
  spacing, `OPENNOW_PLAY_ENTER_HOLD_MS` (150 when unset) holds each press down
- the press and its release re-assert the window's capture state exactly as
  `0040`'s enter presses do, so a press cannot be lost in the silent switch the
  r0.67 reading named
- the lines are `wayland-video input: opening play clock armed: … ms then … more at 5s`,
  `opening play answered (… left)` and `opening play held for … ms and released`,
  beside the existing `opening enter` lines

The install reads `/usr/bin/opennow-qt` `opening play clock armed`=1,
`opening play answered`=1 and still `capture re-armed`=1 from `r0.67`, and the
ffi keeps `embedded capture gate`=1, `embedded input tally`=1 and
`keeps the capture request armed`=1.

The first `r0.68` attempt that streamed (`play-d2d-14.log`, window live from
~09:51:27) is the measured answer, and it walks one notice further down the
chain:

- `wayland-video input: opening play clock armed: 210000 ms then 0 more at 5s`
  at window creation, then `opening enter answered (0 left)` at 09:52:31 (+64 s,
  the consent window's "ok") and `opening play answered (0 left)` at 09:55:00
  (+213 s) followed by `opening play held for 150 ms and released`
- the presses crossed the gate and reached the host: `NVST native input drain:
  … keys=4 absolutes=1` for the two presses' two transitions each, and the HID
  wire carried 2 `events=[key-down(3)]` and 2 `events=[key-up(4)]` inside 5
  `route=ControlReliable` packets
- the witness stills read the chain: 25 s after the PLAY press the splash that
  waited "PRESS ⏎ TO PLAY" is gone and the host shows a new notice -
  **SIGN UP / JOIN THE FIGHT // EARN EXOTIC GEAR**, whose two options are bound to
  keys: `⏎ Sign Me Up` and `Esc Not Now`. That notice then sits unpressed from there
  to the end of the session, which the host ended again at ~14 min after window
  creation through `0031`'s close

So the cloud chain is three notices deep, not two: consent `⏎`, PLAY `⏎`, and a
signup notice that answers `⏎` to sign up or `Esc` to skip. A third clock of the
same shape, armed farther still and answering with a key the notice can name, is
owed by the next patch; the Main Menu itself has not been witnessed yet.

#### The r0.67 capture gate names who flips it, and the window keeps it armed (Nov 4, board `weston@192.168.1.33`)

The r0.66 reading closed the two-queue theory and left one unexplained silence:
the window armed capture once and the tally then said `active=false dropped=48`,
with no capture call anywhere in the app's log. Reading the code names the
caller exactly, and `0040` (built as `r0.67`, deb md5 `d858ac008683fe9afc0ee67722159c09`)
answers it from both sides. The cause: the capture owner is **shared**. The
application's own Qt video item switches it off on its own clock and never says
so - `StreamVideoItem::syncCaptureState` (`src/streaming/StreamVideoItemInput.cpp:956`)
calls `NativeStreamRuntime::setCaptureActive(desired, …)` directly, `desired`
false for an item with no active Qt window focus, which is every run on this
board because the stream window is weston's window, not a `QQuickWindow`'s. The
native window's own `WaylandVideoSurface::setCaptureActive` then refuses to
re-arm because its `m_captureActive` still says active - and `submit` answers
`OPENNOW_STREAMER_OK` either way, so the presses die silently.

- the streamer side logs the gate's own state changes, so the next reader does
  not have to read code to find the caller:
  `NVST embedded capture gate: call={} active={} previous={} relative={} window_handle={} raw={} queue={:p}`
  on the gate's first call and on every call that changes the state it leaves
  behind, beside the existing `NVST embedded input tally:` and the drain's line,
  all three naming the same queue by address
- the app side re-asserts what it believes it asked for:
  `WaylandVideoSurface::reassertCaptureOwner(reason)` calls the existing
  `refreshCaptureState` only while the window's own `m_captureActive` says
  active - a keyboard that left the window or a teardown still owns that
  decision and this cannot undo it - before every key transition the window
  submits (`submitKey`, and the `0032`/`0033` clock's press and release) and
  once a second on a `QTimer` while the window is live, which keeps a player's
  pointer and button alive across the silent switch too. The line is
  `wayland-video input: capture re-armed: %s`

The install reads `/usr/bin/opennow-qt` `capture re-armed`=1 and
`/usr/lib/libopennow_streamer_ffi.so` `embedded capture gate`=1,
`embedded input tally`=1, `keeps the capture request armed`=1.

The first `r0.67` attempt (`play-d2c-3.log`, app pid 164313) is evidence about
the launch, not the re-arm: it never reached a stream window. The gate's first
call is the app's own silent one reaching the owner before any window exists -

  NVST embedded capture gate: call=1 active=false previous=false relative=false window_handle=0 raw=false queue=0x14e0c850

then the interface came up at 03:26:51 and the log froze at 03:29:41 (13 KB,
last lines all `opennow-present fps=0.0 render=0.0 swapped=0`). The app stayed
alive: `opennow-qt` wedged in `wchan=do_sys_poll`, waiting on a compositor
reply that never came, `opennow-core` on `pipe_read`, and `dmesg` carries
three `[galcore]: GPU[0] core0 hang, automatic recovery.` for the boot, two
of them within minutes of this launch. That is the Vivante/driver death
family the starved capture pool sits in (open item 8), not a `0040`
regression: with the compositor reply lost, the interface never painted and
the launch never asked for a window. The attempt driver now treats a log that
stops growing as a dead launch instead of waiting out its budget, and the
attempt was relaunched.

The second `r0.67` attempt (`play-d2c-4.log`, window live 04:08:29 to 04:23:08,
14.7 min - the first session in this series to outlive the release-fence death
window, `invalid fence` 0 times) is the measured answer, and it closes the input
half:

- the gate transitions are now visible in one line each: `call=1 active=false`
  (the app's Qt item reaches the owner before any window exists), `call=25
  active=true` (the window arms it at its creation), and the flip-flop cycle
  `26 false / 27 true / 28 false / 30 true` - the silent Qt-item flip and the
  window's re-arm, both named
- `NVST embedded input tally: active=true pushed=48 dropped=0 keys=47` beside
  `NVST native input drain: available=true … keys=48`: all 24 Enter presses
  of the opening clock crossed the gate and were forwarded
- the HID wire carried them: 24 `events=[key-down(3)]` and 24 `key-up(4)`
  packets with 49 `route=ControlReliable`
- 249 `capture re-armed: clock` lines while the window was live, plus the
  `key`/`enter press`/`enter release` re-arms at the presses

The picture half is read off the witness PPMs (the streamer's own decoded
frame): in the frames after the Enter burst the opening pop-up is gone - the
presses reached the host and answered it - but the game did not launch: Steam
sat on the store's **Featured & Recommended carousel, slide 6 of 10**
(Torchlight Infinite featured), cursor still at its rest position, from ~6 min
after the window to its end; at 04:23:08.864
`opennow-server-end-closes-window: the server ended the session; unmapping the
stream window` - the host's ~14 min idle timeout closing through `0031`'s
path, and the app alive after it. What the presses did not do is navigate,
which is open item 16.

One rate has to be called out for the next reader: the window presented at
`wayland-video fps=0.6 committed=475 dropped=0 flushFailed=0` across this
session while the decoder produced `native=59.5/s` - the stills differ so the
picture advanced, but one commit per 1.7 s reached the compositor. With
`dropped=0` nothing was refused: something kept the window's own pull clock
off. The re-arm costs one call to the streamer per second and the stills cost
nothing on the board, so this reads as owed to whatever starves the pull (the
same suspect as the starved capture pool), not as a `0040` rate addition.

The same release was then measured again after a session-service restart -
which on this image kexecs a fresh kernel (`dmesg` starts over), the standing
remedy for the degraded GPU session - where the first launches died in the
same `galcore` core-hang family as attempt 3 (three frozen launches with
`opennow-present fps=0.0` and a log frozen while the app waits in
`do_sys_poll`/`futex_wait_queue`, one silent exit; the attempt driver now
calls a log that stops growing a dead launch instead of waiting its budget
out), while later attempts launched cleanly and `0040` measured identically
again: `pushed=48 dropped=0`, `keys=48` on the drain, 24 Enters on the wire.

The time-lapse witness this series added (the `d2d` driver keeps a time-stamped
stills every 25 s through the stream) then reads the chain itself, which no
counter had ever been able to say:

- the Steam client is open from the first still, together with an unread
  friend-chat pop-out, and the blocking notice is a consent window (a game
  launcher's privacy consent, whose highlighted button at the bottom reads
  "ok") that is up from the first still of the session
- with NO presses at all (a no-clock observation attempt), the Steam client
  sits with that consent window open for the whole 11 min: the notice, not
  the host, is what waits
- ONE Enter, armed 60 s after the window became live, answers it and the
  game launches: the still 95 s later shows the Destiny 2 splash waiting for
  "PRESS ENTER TO PLAY", and the splash is still unpressed at the 14th
  minute - the presses never came again because the clock was armed once
- the same clock with 12 presses 13 s apart (first at +90 s) closes the
  host's own windows instead: the desktop is bare 95 s into the burst,
  before the splash exists, and the extra presses wander into whatever
  window the host has open - this is also why the 24-press attempts left
  a bare desktop

So the press budget of the cloud chain is ONE Enter per notice, with the
notices about 135 s apart, and the opening clock's 5 s follow-ups cannot
hold that shape; the answer is owed to the next patch. The chat pop-out and
the account names in the host's own window belong to the player and stay
out of this layer's documentation.

How a later attempt's verdict is read - and why the picture is the witness:
the delivered rate cannot tell Steam's painted window from Destiny's Main
Menu, both run at 57-60/s, and `weston-screenshooter` answers
`unauthorized` here. The witness is the streamer's own dev aid:
`OPENNOW_FRAME_DUMP=<dir>` writes the newest decoded frame as a PPM at most
once a second (`frame_producer.rs:550 dump_decoded_frame`) and says once
why one could not be written (`opennow-dump: <reason>`). The `0035`
screenshot clock saved 0 files on this board because the native Wayland
path never runs the Qt render callback that completes a screenshot, so it
is replaced, not fixed.

#### The r0.66 counters name the queue and both sides of its gate (Nov 4, board `weston@192.168.1.33`)

`play-d2-1.log` (the r0.65 attempt that launched Destiny 2 by itself through
`--launch-app-id=100362311`) said the window pressed and the drain forwarded
nothing, and both are true; `0038` of `r0.65` moved the counters to the drain
side and `OPENNOW_NVST_TRACE=1` finally put the HID wire in the log, so the gap
between the two could be read instead of guessed. `0039` (built as `r0.66`, deb
md5 `c87f9c3af4cc5139d596de8d20818c48`) closes the half that was still dark:
`EmbeddedInputCapture::submit` counts what the embedder handed it and whether the
queue's owner took it, and both it and the drain print the queue's address, so a
session can say in one line which queue the press entered and whether the gate
refused it:

```
NVST embedded input tally: active={} pushed={} dropped={} keys={} moves={} absolutes={} buttons={} other={} queue={:p}
```

The `r0.66` attempt (`play-d2b-2.log`, window created 01:16:07.273, the Enter
clock armed 20 s later) answers the question the counters were for:

- both sides name the **same** queue, `queue=0x295638d0` in the tally and in the
  drain, so there is no second queue nobody reads - the two-queue theory the
  earlier sessions left open is dead
- `tally: active=true pushed=1 dropped=0 keys=0 moves=0 absolutes=1` at the
  window's own creation: the seat's one pointer absolute went in while the gate
  was open, and the drain forwarded it (`absolutes=1`, `NVST encoded input tx`)
- `tally: active=false pushed=1 dropped=48 keys=48 absolutes=1` for the rest of
  the session: all 24 Enter presses of the clock, 48 key transitions, were
  refused inside `submit` because the queue's owner was inactive
- the app's side logged exactly twice, `wayland-video input: streamer capture
  requested=0 inputAllowed=1` at 01:16:07.211 and `requested=1` at .355 beside
  `capture active` at .356, and never again, so nothing on the app's side says
  it turned the gate off

The code answers why. `EmbeddedInputCapture::set_active` has one production
entry point, the FFI's `opennow_streamer_set_capture_active`
(`crates/opennow-streamer-ffi/src/lib.rs:1643`), and the native window reaches it
through `setVideoInputCapture`, which logs. The app's own Qt video item does not:
`StreamVideoItem::syncCaptureState` (`src/streaming/StreamVideoItemInput.cpp:956`)
calls `s_nativeRuntime->setCaptureActive(desired, …)` straight, with `desired`
false for an item that has no active Qt window focus, and the call makes no log
line. The Qt item switches the same capture owner off moments after the native
stream window turned it on, and nothing switches it back because
`WaylandVideoSurface::setCaptureActive` keeps its own `m_captureActive` and
returns early when that already says active. This is why the window's presses
travelled to `submit` and no further.

Still owed, and what the next patch does: the gate's own transition log on the
streamer's side, and the app-side answer that keeps the owner armed while the
stream window is the front window - the window has to re-assert capture
(`refreshCaptureState`) before it submits, because the press's own submit status
is `OPENNOW_STREAMER_OK` either way and the refusal is silent.

#### The r0.65 drain counters and the HID wire made visible (Nov 3, board `weston@192.168.1.33`)

`0038` (built as `r0.65`) counts what the drain side did with the window's input
- `NVST native input drain: available={} clearedPolls={} keys={} absolutes={} moves={} buttons={}`
once per drain pass - because the app had already logged the presses and 0 refusals,
so the loss had to be at or after the queue. The same session is the one that
proved the earlier "0 HID markers" readings were worthless: `nvst.rs:198
verbose_diagnostics_enabled()` is armed by `OPENNOW_NVST_TRACE=1`, and without
that variable the streamer's HID wire lines (`NVST native input rx: events=[...]`,
`NVST encoded input tx: route=...`) are never printed, so counting them said
nothing about whether a press reached the wire. Armed, the cleanest run this
series has produced so far reads:

```
23:08:01.146 wayland-video: stream window created            (--launch-app-id=100362311, 132 s in)
23:08:01.171 wayland-video input: capture active             (never inactive again)
23:09:01.146 wayland-video input: opening enter answered (14 left)
23:09:01.298 wayland-video input: opening enter held for 150 ms and released
   ... 4 presses, 5 s apart, 0 refused
NVST native input drain: available=true clearedPolls=2906 keys=0 absolutes=1 moves=0 buttons=0
NVST native input rx: events=[mouse-settings(25)]   NVST encoded input tx: route=ControlReliable
NVST native input rx: events=[mouse-absolute(5)]    NVST encoded input tx: route=ControlPartial
```

So the drain's gate is open and its queue is live - the one absolute the seat
made at the window's creation came through and was encoded onto the wire - while
the four Enters that were meant to answer Steam's pop-up never reached
`queue_input` at all. `keys=0` beside 4 presses logged by the window is the
half `0039` then names: the presses never entered the queue, so the drain's
counters were never the reason. The same session is also the one that died with
the release-fence death this series keeps meeting: 23:09:18.913 is its last
`opennow-pull-gap` line, and the run ends in
`zwp_linux_surface_synchronization_v1@30: error 0: invalid fence fd` followed by
`The Wayland connection experienced a fatal error: Protocol error`, 78 s of
picture after the window opened.

#### The r0.64 cursor report that keeps the capture request armed (Nov 3, board `weston@192.168.1.33`)

The host answers every control message with its own cursor state -
`NVST cursor wire rx: … command=0x010f cursorId=Some(1) position=Some((39785, 23201)) visible=Some(false)`
- and `nvst_cursor.rs` used to treat that report as a verdict on the cursor
capture it had been asked for, replying `command=0x0308 enabled=false
reason=cursor-notification`. That answer is a request to the host to stop
painting its cursor, and it took the capture request down with it: the host
stopped answering, the deadline lapsed, and the surface's own cursor request
died ~90 s into sessions that were otherwise streaming at 55-60 fps. `0037`
(built as `r0.64`) lets a report renew the deadline instead of disarming it, and
says so: `NVST cursor report keeps the capture request armed: reason=cursor-report
deadlineRenewed=true`. The `r0.65` run above carries that line once and keeps
293 cursor reports for the rest of the session instead of one and a death, so
the picture keeps coming and the host keeps answering the cursor state the seat
needs. What the run does not show is the cursor following the mouse: the
position stays at `(39785, 23201)` in 171 of the reports and `visible=false`,
which is still open.

#### The r0.63 screenshot clock and the compositor's own descriptor (Nov 3, board `weston@192.168.1.33`)

Two things the player reported - "I can see the pointer in Steam but I cannot
move it", and a click that does nothing - are both about what the screen shows,
and this series has no way to look at the screen: the counters say what the app
sent, the TV says what the host did. The app's own screenshot shortcut cannot be
used while a stream is up, because the stream window takes the seat's keyboard
and forwards every key to the host - it goes into the game. So `0035` arms the
same path from the environment: `OPENNOW_STREAM_SCREENSHOT_MS` is the interval
and `OPENNOW_STREAM_SCREENSHOT_TIMES` how many pictures to keep (4 when unset),
and it goes through `AppController::captureStreamScreenshot` and
`NativeStreamRuntime::requestScreenshot`, i.e. the streamer's own decoded frame
written under `Pictures/OpenNOW/Screenshots`. A tick while the stream is not
presenting is refused and costs nothing, so the first picture lands once the
host has painted. The two ends are `opennow-stream-screenshot: armed every %d
ms, %d times` and `opennow-stream-screenshot: saved %s`.

`0036` closes the class that ended two of the sessions that were being measured.
Run 10 (`play-dstny10.log`, PID 48992) created its window at 17:01:16.011, made
four `wl_buffer`s over the next 94 ms, and at .121 lost the connection:

  wayland-video: the compositor connection failed: Socket operation on non-socket
  wayland-video: flush failed: Socket operation on non-socket (display error 88)
  wayland-video: stream window closed

ENOTSOCK is what sendmsg answers when the descriptor is not a socket, so by then
this surface's own connection number named something else. The decoder exports a
fresh dma-buf descriptor per frame out of a pool this board churns through, and
a number the streamer has already closed is handed to the next `open()` in the
process, which can be `wl_display_connect`'s socket; `present()` closes every
frame descriptor no request carried, and that close is correct for every number
except this one. It now refuses that number and says so once,
`opennow-diag: frame fd %d is the compositor connection; not closing it`: the
frame is lost either way, the window is not.

| Counter | Value |
| --- | --- |
| the class it answers (`play-dstny10.log`) | window created 17:01:16.011, `dmabuf buffer 1…4 created` by .105, `the compositor connection failed: Socket operation on non-socket` at .121, `flush failed: Socket operation on non-socket (display error 88)` at .165, `stream window closed` at .165: the window died 154 ms after it appeared and the run logged **0** `wayland-video fps=` samples |
| the neighbouring fd churn | Run 11 (`play-dstny11.log`, PID 53158) is the other session of this family, measured one build earlier: its window lived 24.9 s, `opennow-server-end-closes-window` and `wayland-video: stream window closed` both at 17:07:08.324, and the pull clock then logged `[V4L2RequestContext] Failed to reinit request object 0: Bad file descriptor (9)` and `hardware accelerator failed to decode picture` before the log's last line was the `buffer_fenced_release` assertion. `Socket operation on non-socket` does not appear in that log, so 0036's refusal is not what its fd churn is made of |
| what this release has measured so far | `play-dstny13.log` (the r0.63 launch at 17:33) never reached its stream window before the board stopped answering, so every counter of this release is owed to the session after the restart: the screenshot clock's `armed`/`saved` lines, and 0036's `frame fd %d is the compositor connection` refusal |
| the input half, already measured on the build before it | `play-btn1.log` logged `the seat now has a pointer` 4 ms **before** the window, 10 button transitions and **0** `submit refused`; see the `r0.62` section above |

The run that measures the picture is the one that has to follow a reboot: the
screenshot clock writes into `Pictures/OpenNOW/Screenshots`, so the picture the
player described - the host's cursor visible in the video and not moving while
the mouse does - becomes a file that can be looked at next to the log.

#### The r0.62 seat pointer at bind and the click that carries its position (Nov 3, board `weston@192.168.1.33`)

The window answered Steam's notice with held Enters and the player still could
not click anything in the stream. Two measured holes sit on the click's own path.

The first is the pointer object. The window asked the seat for a `wl_pointer`
only when the seat advertised the pointer capability, and on this board the
capability arrives with the first pointer event, not with the seat: `play-dstny9.log`
created the window at 16:43:56.312 and logged `wayland-video input: the seat now
has a pointer` at 16:44:03.843, so for 7.5 s the window had no pointer at all and
a click in that interval went nowhere. Weston's `seat_get_pointer` answers from
`seat->pointer_state` directly - the comment in its own source says it hands out
a pointer "if the seat has ever had one - even though the spec explicitly states
that this request only takes effect if the seat has the pointer capability" - so
the window now asks at bind. `0034` keeps the capability event for the parts that
must not exist on an inert pointer (the relative pointer and the lock, which is
what took the session down when it was created on a NULL `weston_pointer`), and
the capability event replaces the inert object with a real one when the seat
finally gets a pointer - `unbind_pointer_client_resource` in weston 13 nulls the
user data before it touches it, so releasing that object is safe.

The second hole is the click itself: a click is a position and a transition, and
only the transition was sent. The streamer clears its captured input on every
poll until the host's channel reports ready, so a motion dropped before that
point leaves the host aiming somewhere else, and the button lands there.
`handlePointerButton` now sends the last position the compositor reported
immediately before the transition. Both ends of the gate became greppable:
`wayland-video input: streamer capture requested=%d inputAllowed=%d` and
`wayland-video input: button %u submit refused status=%d`.

| Counter | Value |
| --- | --- |
| the pointer at bind (`play-btn1.log`, PID 74254) | `wayland-video input: the seat now has a pointer` at 17:41:48.787, **4 ms before** `wayland-video: stream window created` at 17:41:48.791, against r0.61's run 9 where the same line sat 7.5 s **after** the window |
| the click's own path | `streamer capture requested=0 inputAllowed=1` at 17:41:48.506 (the hooks are installed before the window exists), `streamer capture requested=1 inputAllowed=1` at .906, `wayland-video input: capture active` at .907 and the first `wayland-video input: pointer 364x567 of 1920x1080` in the same millisecond |
| buttons the window forwarded | **10** transitions in 1 s: `button 1 pressed=1` 17:41:54.665, `button 1 pressed=0` .761, `button 3 pressed=1` .849, `button 3 pressed=0` .999, `button 1 pressed=1` 17:41:55.000, `button 3 pressed=1` .092, `button 1 pressed=0` .146, `button 1 pressed=1` .242, `button 3 pressed=0` .440, `button 1 pressed=0` .440, and **0** `submit refused`, so none of them was dropped by the streamer. r0.61's four runs (`play-dstny8`…`play-dstny11`) logged **0** button lines |
| keys | 2: `wayland-video input: key 28 vk=0xd mods=0x0 pressed=1` at 17:41:50.996 and `pressed=0` at 17:41:51.100 - a physical Enter held 104 ms, which the host reads |
| the F11 window | `wayland-video: stream window windowed` at 17:42:21.691 and the stream kept its rate through the toggle: `fps=48.1 committed=1589 dropped=0 flushFailed=0` at 17:42:21.691's next sample and `fps=52.4 committed=1692 dropped=0 flushFailed=0` three seconds later |
| the picture it paid | 35 s of stream, first sample `wayland-video fps=42.5 committed=44 dropped=0 flushFailed=0`, last `fps=52.4 committed=1692`, 0 dropped of 1692, 0 `dropping a frame (the frame could not be exported as a DMA-BUF)` |
| what survived | 0 `connection failed`, 0 `Socket operation on non-socket`, 0 `invalid fence`, 0 `dropping a frame`; the run's one crash is the `buffer_fenced_release` assertion 3 s after the windowed toggle, which is the vendor EGL client's release-fence family and is item 14's, not this patch's |

The window is therefore not a picture only: it forwards the pointer's positions,
its buttons and the keyboard, and this release does it from the millisecond the
window exists. What the run still cannot show is where the host's cursor was when
the player clicked, which is what the `r0.63` screenshot clock is for.

#### The r0.61 held opening enter (Nov 3, board `weston@192.168.1.33`)

The `r0.60` clock answered Steam's notice twelve times and the notice stayed up,
so the press was not the problem - its length was. The run that shows it is the
`r0.60` session the player ended by hand: `play-dstny7.log` holds the clock's own
`wayland-video input: opening enter answered (11 left)` down to `(0 left)`, and
the physical Enter that finally dismissed the notice is the window's next input
line, `wayland-video input: key 28 vk=0xd mods=0x0 pressed=1` at 11:15:46.301
with `pressed=0` at 11:15:46.333 - **32 ms** down. A cloud host samples its input
channel; a press and a release inside one sample is a press it never saw. `0033`
keeps the answer on the same clock and the same `VK_RETURN`, but sends the press
on one round and the release on the next, so the key is still down when the host
looks: `OPENNOW_POPUP_ENTER_HOLD_MS` is the hold (120 ms when unset), the answer
is logged before the press goes out and `opening enter held for %d ms and
released` when the release does, and the teardown releases a press the clock is
still holding, so a session that ends cannot leave Enter down in the game.

| Counter | Value |
| --- | --- |
| the answer as it fired (`play-dstny8.log`) | `wayland-video: stream window created` at 11:55:42.565 and `wayland-video input: opening enter clock armed: 30000 ms then 23 more at 5s` at 11:55:42.570, then **24** answers, first at 11:56:12.571 and last at 11:58:10.516, `(23 left)` down to `(0 left)`, 5.121-5.124 s apart |
| the hold | **24** `opening enter held for 120 ms and released` lines, one after every answer, each 120 ms later (11:56:12.571 → 11:56:12.691, 11:58:10.516 → 11:58:10.637), and `120 ms and released` is the only duration in the log |
| nothing else pressed it | **0** `wayland-video input: key` and **0** `wayland-video input: button` forwards in the whole session, and one pointer position (`pointer 740x573 of 1920x1080` at 11:55:42.588). The board was empty: all 24 Enters are the window's own |
| the picture it paid | 10 min 0 s of stream (11:55:42 → 12:05:42), last sample `wayland-video fps=56.7 committed=32906 dropped=0 flushFailed=0`, `opennow-frame-rate native=59.9/s delivered=56.9/s`, `opennow-present cost: pulls=566 presented=272`, and **0** `dropping a frame (the frame could not be exported as a DMA-BUF)` |
| what survived | **0** of `invalid fence`, `Protocol error`, `destroyed object`, `Bad file descriptor`, `buffer_fenced_release` in the streaming window. The log's only Wayland line is `The Wayland connection broke. Did the Wayland compositor die?` at 16:39:23.869, when this boot's weston was restarted four and a half hours later in log time |

What the run cannot say is whether the host read the hold: there is no picture of
the screen in it, so the notice's own disappearance is still the TV's answer.

#### The r0.61 pointer gap, measured in the next session (Nov 3, board `weston@192.168.1.33`)

The same release's next session found the other half of "the mouse does not
work": `play-dstny9.log` (PID 44011) created the window at 16:43:56.312 and only
logged `wayland-video input: the seat now has a pointer` at 16:44:03.843, with
the first position at 16:44:03.859 - **7.5 s** in which the window had no
`wl_pointer` at all, so a click in that window went nowhere. The clock kept
answering through it (`opening enter answered (23 left)` at 16:44:26.315,
`(22 left)` at 16:44:31.436, both held 120 ms), and the session then died on a
decode side the release does not touch (`dropping a frame (the frame could not be
exported as a DMA-BUF)` 92, `invalid fence` 1, `Protocol error` 1). `0034` is the
answer to the gap and to the click: the pointer is asked for when the seat is
bound rather than when the capability says so, and a button transition carries
the last position the compositor reported. See the `r0.62` section.

#### The r0.60 opening-enter clock (Nov 3, board `weston@192.168.1.33`)

GeForce NOW puts Steam's own notice in front of the game before it starts, and
only an Enter dismisses it. The app already had a way to answer that notice -
the stream window takes the seat's keyboard and forwards an Enter the host
sees - but it answers at the wrong moment and it answers only while someone
sits at the board: measured over the two Destiny 2 sessions of the `r0.59`
build, presses made at the start of the stream landed while the cloud boot had
not reached the pop-up yet, and a session started with the board empty got no
press at all. The same pair of runs shows why a physical press is the fragile
one: at 07:39:46.984 weston logged `event1 - Dell Dell USB Keyboard: device
removed`, 30 ms later the surface logged `wayland-video input: capture
inactive`, and in weston 13.0.1's own source that is `weston_seat_release_keyboard()`
answering a device leaving the seat with `weston_keyboard_set_focus(keyboard, NULL)`
plus `cancel_grab`, with only `weston_view_activate_input()` handing the focus
back - on a pointer motion. Item 13 keeps that class. `0032` stops leaning on
it: the surface already holds the streamer's own input hooks, the same key
calls `submitKey` makes, so a clock on those hooks sends the same `VK_RETURN` a
physical Enter becomes with the compositor not involved. `OPENNOW_POPUP_ENTER_MS`
says how long after the stream window became live the first press goes out,
`OPENNOW_POPUP_ENTER_TIMES` repeats it 5 s apart (4 times when unset), `OPENNOW_POPUP_ENTER_MS`
unset arms nothing - which leaves every earlier series' run unchanged - and
`destroySurface` stops the clock, so a session that ended cannot press Enter
into whatever the interface comes back to.

| Counter | Value |
| --- | --- |
| the answer as it first fired (`play-dstny6.log`, PID 17149) | `wayland-video: stream window created` at 10:23:31.051 and `wayland-video input: opening enter clock armed: 30000 ms then 11 more at 5s` in the same millisecond, then 12 answers at 10:24:01.052…10:24:56.066, 5.00-5.01 s apart, `wayland-video input: opening enter answered (11 left)` down to `(0 left)` |
| the board stayed empty | 0 `wayland-video input: key` forwards in the whole run - the only input line of the run is `wayland-video input: capture active` at 10:23:31.130 - so nothing the compositor owns made the press: all 12 Enters are the window's own, which is exactly what item 13's keyboard-focus class costs a physical one |
| the picture it paid | `wayland-video fps=55.8 committed=27323 dropped=0 flushFailed=0` at 10:31:54 against 8 min 23 s of picture, `opennow-frame-rate native=59.4/s delivered=55.4/s`, and 0 `dropping a frame (the frame could not be exported as a DMA-BUF)` in the run |
| what the run's cost says | the session lived past the ~10 min an idle session usually costs the provider: `opennow` alive 11 min 47 s after the launch (PID 17149) and still streaming, with `server-terminated`, `session_allocation_unresolved` and every crash class at 0 in the log |

The counters say the press left the app at the right distance and at the game's
own key rate; what it dismissed at the screen is the part only the TV can answer.


#### The r0.59 stream-window close on the server's end (Nov 2, board `weston@192.168.1.33`)

What an end the provider sends leaves behind is a window that is still mapped.
`server-terminated: The server ended the streaming session (0x80030007)` and the
`native-to-qt type=status status=stopped` that follows it make the runtime drop
the session (`streamSessionActive = false`, `setStreaming(false)`) and invalidate
the presentation, which decides that the *next* frames are unusable - it never
asks the stream window to go away. So the pull clock keeps running against nothing
and weston keeps presenting the one DMA-BUF the surface last committed: measured
across `ab91`…`ab97`, the teardown marker `wayland-video: stream window closed`
appears **0** times in all seven run logs, and the pull counter keeps going after
every verdict (`opennow-present cost: pulls=626 presented=0 stale=0 noframe=626`
in the five seconds after `ab95`'s end). `0031` follows the runtime's own answer
instead: `streamingChanged` with `streaming()` false, and calls the surface's
`dismiss()`, which commits one unmapped frame - the form that answers the pending
release fences - and destroys the window. It is deferred through
`QMetaObject::invokeMethod(surface, ..., Qt::QueuedConnection)`, the idiom the two
places that close the window already use, because the surface must not be
destroyed inside its own event dispatch. The teardown itself was measured **before**
the patch, through the development aid (`touch /tmp/opennow-close`; the aid is
compiled in, its path just is not greppable because `QStringLiteral` keeps it as
UTF-16), so `0031` only had to reach a path whose cost was already known. With the
window gone the interface underneath is what the screen comes back to - `0029` left
it visible for exactly that.

| Counter | Value |
| --- | --- |
| the teardown, measured before the patch (`ab97`, PID 49599) | `wayland-video: stream window closed` 1 at 2026-11-02T08:36:37.524 after the aid's trigger, app alive immediately after (`pidof opennow-qt` still 49599), 0 of every crash class, 0 coredumps, `CmaFree` 9568 kB before the close → 22180 kB just after → 40172 kB a minute later, so a close hands part of the capture pool back |
| the frozen window, measured before the patch | `wayland-video: stream window closed` **0** in the seven run logs of `ab91`…`ab97`; after an end the surface is still mapped and the pull keeps counting, `pulls=626 presented=0 stale=0 noframe=626` five seconds apart |
| the close as it first fired (`ab98`, PID 59770) | `opennow-server-end-closes-window` 1 at 2026-11-02T10:22:09.166 and `wayland-video: stream window closed` 1 at 10:22:09.167, 12 ms after the terminal event and 1.9 s of picture into the session (one `wayland-video fps=15.2 committed=20 dropped=0 flushFailed=0` sample). The terminal event is the path `0031` answers, but this boot's spent capture pool is what ended the session: the message names a decode failure, not the gateway - `native-to-qt type=error code=media-decode-error message=h264 decoder error: V4l2 decoder reference was lost: FFmpeg V4L2 request h264 packet submission failed`, then `media-consumer-closed`, then `type=status status=stopped` - and `CmaFree` read before the launch was 73508 kB with dmesg already carrying 6 of `hantro-vpu 480d0000.vdec: dma alloc of size 3657728 failed` |
| what survived the close (`ab98`) | the app: 50 s after the close it started a second session and put the pull clock back (`wayland-video input: host hides the cursor` and `wayland-video: frames are pulled from the interface thread` at 10:22:59.462/536), which is 0031's reinstall branch reaching. The death is **after** the close, at the last line of the log, and belongs to that second attempt: `buffer_fenced_release` 1, beside two `Failed to create buffer of type 9: Cannot allocate memory (12)` / `no frame!` pairs from the decode setup that the spent pool refuses, and 0 coredumps |
| what the close costs the interface | with the surface unmapped `NativeVideoWindow.mapped` is false, so 0005's `streamWindowRepaintTimer` (`running: NativeVideoWindow.mapped`) stops and the interface goes to `opennow-present fps=0.0 render=0.0 swapped=0` at 10:22:56.511 / 57.524 / 58.543 instead of the ~27/s render the `0029` state keeps while a window is mapped. That is the state the vendor's EGL-Wayland client leaves a swapchain image's release fence unanswered in, i.e. the family of the `buffer_fenced_release` assertion above - so `0031`'s own cost was unknown there; `ab101` is the run that measures the close clean at a real verdict, and what is still owed is a repaint clock that keeps the interface presenting once the stream window has gone away |
| `CmaFree` across the `ab98` attempt | 73508 kB before the launch, 85144 kB after the death: a close hands the pool part of what it took back, but this boot's largest free DMA run still refuses one contiguous 3657728-byte 1080p capture block, so only a reboot returns the pool well enough to measure the close against a real provider verdict |
| the fresh boot's first attempt (`ab99`, PID 2245) | the whole pool makes the close's own hazard visible instead of confounding it: `CmaFree` 523440 kB at the boot, `dmesg` carrying **0** of `hantro-vpu 480d0000.vdec: dma alloc of size 3657728 failed` and `stm32-dwmac … eth0: Link is Up - 100Mbps`, so neither the pool nor the network cut this session. It died 1 ms after `wayland-video: stream window created`, 4 lines after it in the run log, right behind `dmabuf buffer 1 created (NV12 1920x1080 …)` and `opennow-pull-gap: gap-ms=140 previous=noframe`, with `buffer_fenced_release` 1 and **0** `wayland-video fps=` samples. Neither close marker appears, and the session never left its first frame: `ab99` measured nothing of `0031` except that a first-frame death is not attributable to it |
| the fresh boot's second attempt (`ab100`, PID 3730) | the run that was clean until it ended: 1130 s of picture, 1110 samples, last `wayland-video fps=55.6 committed=60764 dropped=0 flushFailed=0`, the interface answering at 27/s (`opennow-present fps=0.0 render=27.0 swapped=0`), `buffer_fenced_release` **0**, and the pull clock still counting normally at the last sample (`pulls=574 presented=277 stale=0 noframe=297`). It ended at 12:04:33.201 with `EGL: errno=9 (Bad file descriptor)` and `The Wayland connection experienced a fatal error: Bad file descriptor`, and this boot's `native-streamer.log` carries **0** `server-terminated`, so the close was never reached - the media was still live (`media-ingress elapsed_ms=1130447 video=66909 audio=224283 … paused=0`, no `consumer stopped`) |
| the close at a real verdict, whole pool (`ab101`, PID 6875) | the run that settles it. `server-terminated: The server ended the streaming session (0x80030007)` at 1793622482109 = 12:28:02.109, 603.4 s into a session the AFK kick ended (`media-ingress consumer stopped elapsed_ms=603414 video=35605 audio=120177 keyframes=6 dropped=27 paused=0`), on a boot with 0 failed DMA allocations, and all three of the close's own lines sit in one millisecond: `opennow-server-end-closes-window: the server ended the session; unmapping the stream window`, `wayland-video input: capture inactive`, `wayland-video: stream window closed`, all at 2026-11-02T12:28:02.385, 276 ms after the verdict. 593 video samples, last `wayland-video fps=50.6 committed=32239 dropped=0 flushFailed=0`, p50 **54.2** (p1 38.5, p90 55.9, min 22.2, max 57.4) |
| what survived the close (`ab101`) | **0** of every crash class, in the whole log and again counted only after the close (invalid fence, Protocol error, destroyed object, Bad file descriptor, buffer_fenced_release, dmabuf-rejected, dropping a frame, The Wayland connection), 0 coredumps for the boot, and the app alive 4 min after the close (`opennow-qt` 6875 / `opennow-core` 6898 at 12:32:12, `VmRSS` 241548 kB), the run log continuing to 12:30:04. The pull clock stopped counting: the last `opennow-pull-gap` is at 12:28:01.718 and no `opennow-present cost` line exists after 12:28:02, against the `pulls=626 presented=0 noframe=626` five seconds apart the `r0.57`/`r0.58` runs kept after an end |
| still open after the close (`ab101`) | the interface goes quiet: `render` climbs to 25.0 at 12:28:01.932, then 0.5 at 12:28:04.047 and 0.0 at 12:28:19.349 / 12:28:34.273 / 12:29:04.304 / 12:30:04.015 with `fps=0.0 swapped=0`, because the repaint clock `0005` added and `0006` narrowed to `running: NativeVideoWindow.mapped && NativeVideoWindow.windowed` (interval 250 ms, `onTriggered: window.update()`) is the only thing that asks the interface to present, and the close removed the mapping it waits on. The screen therefore holds the interface's last frame, static and not repainted - the live rate `ab101` measured beside a mapped window was `render=25.0`-`31.0` against `swapped=0` throughout. `0032` would restart that clock once the stream window has gone away, which also keeps the vendor EGL client away from the idle state that precedes the release-fence family |
| `CmaFree` across the `ab101` attempt | 462716 kB before the launch, 372320 kB at 12:30:42 with the app still alive holding its capture objects: the close's teardown returned what it returned in the `ab97` aid measurement (9568 → 22180 kB), but three attempts on this boot left objects behind, so no per-close pool number is claimed here |
| the close at a verdict a player made (`ab102`, PID 20331/20357) | the repeat, and the first close measured on a session nobody engineered: the user joined at 16:24:54, played, died and exited. `server-terminated: The server ended the streaming session (0x80030000)` at 1793637620689 = 16:40:20.689 with `media-ingress consumer stopped elapsed_ms=572230 video=32680 audio=113933 keyframes=2 dropped=4 paused=0`, on a boot `dmesg` carries **0** of `hantro-vpu 480d0000.vdec: dma alloc of size 3657728 failed` in. The close's three lines are in one millisecond again: `opennow-server-end-closes-window` and `wayland-video input: capture inactive` at 16:40:20.967, `wayland-video: stream window closed` at 16:40:20.969, 278 ms after the verdict against `ab101`'s 276 ms. The close does not read the verdict code, so the `0x80030000` of a player leaving and the `0x80030007` of the AFK kick both unmap the window the same way. 561 video samples, last `wayland-video fps=50.6 committed=29068 dropped=0 flushFailed=0` at 16:40:20.353, p50 **53.4** (p1 13.1, p90 55.6, min 1.8, max 57.8) |
| what survived that close (`ab102`) | **0** of every crash class counted over the whole log (invalid fence, Protocol error, destroyed object, Bad file descriptor, buffer_fenced_release, dmabuf-rejected, dropping a frame, The Wayland connection), 0 coredumps for the boot, and the app alive 41 min after the close (`opennow-qt` 20331 / `opennow-core` 20357 at 17:21). The pull clock stopped: last `opennow-pull-gap: gap-ms=119 previous=presented` at 16:40:20.080, 489 ms before the close, and no `opennow-present cost` line after it. The interface went quiet exactly as in `ab101`: `render=29.4` at 16:40:20.080, `11.5` at 16:40:21.297, then 0.0 from 16:40:22.961 to the last sample at 17:18:01.462, which is 37.7 min of a static, never-repainted interface while the app stayed alive - the same `0032` item `ab101` left, now confirmed on a session the player drove |

| what the class's history says about `0031` | `buffer_fenced_release` 1 in all six runs of the `opennow-interface-hidden` era (`ab79`-`ab83`, `ab88`, `ab89`, with `0019`'s `rootWindow->hide()` in the build, which is the documented way fences go unanswered) and 0 in all seven runs of the `opennow-interface-visible` era (`ab91`…`ab97`, `r0.57`/`r0.58`). Under `r0.59` it is 1 in `ab98`, 1 in `ab99` and 0 in `ab100`, `ab101` and `ab102`, and `ab100` is still the only `r0.59` attempt whose stream reached full rate (`committed=60764` against `ab97`'s 89525 and `ab94`'s 38228, so it is not a series record, but it dwarfs the other four at 20, 0, 32239 and 29068 committed): so the class is still the same race that surfaces at an EGL swapchain recycle, `0031` does not create it and `0029` did not stop it - the two attempts that ended through the close, `ab101` and `ab102`, carry 0 of it in the whole log - but `r0.59` is where it appeared twice in five attempts, which is more than the two releases before it ever did |


#### The r0.57 crash-class pair and the r0.58 artwork guard (Nov 1, board `weston@192.168.1.33`)

The house lost voltage and both addresses moved with the reboot that followed it:
the board is `weston@192.168.1.33` and the build host is `ubuntu@192.168.1.35`,
so the gateway-ping logs of the runs after that reboot have to target the new
pair. `0028`/`0029` (built as `r0.57`) take the two crash classes this boot had
been carrying; `0030` (built as `r0.58`) closes the completeness gap in
`download()`. It is measured as store-door hygiene only: the `QFile` warning it
was aimed at turned out to be Qt's own JPEG plugin asking a `QFile` for one
block more than a whole file has, and the `r0.58` session below measures
`truncated-image` 0 with the warnings still in its log. Item 12 carries the
corrected class.

| Counter | Value |
| --- | --- |
| driven `r0.57` (`ab86`, ~9 min) | crash classes 0, `dropped=0`, p50 **43.6** fps against `r0.56`'s 55.9, load 2.28 → 6.12, `CmaFree` -111 MB across the run |
| `0029`'s measured cost | the two manual `r0.58` sessions pay it as p50 37.7 and **54.8** against `r0.56`'s 55.9, and the 54.8 is the clean-pool one: leaving the interface window visible costs ~1 fps. The 43.6 the driven `ab86` paid came from that boot's spent pool and a load of 2.28 -> 6.12, not from the revert |
| manual `r0.57`, fresh boot, whole pool | `session.create 1793555252518` → `game_launch: title=Dark and Darker appId=102303211` → streamer stop at `1793556244503`: `server-terminated: The server ended the streaming session (0x80030007)`, `media-ingress consumer stopped elapsed_ms=804681 video=47803 audio=160359 keyframes=3 dropped=5` |
| crash-class lines in that 805 s session | `buffer_fenced_release` 0, `Bad file descriptor` 0, `Protocol error` 0, `destroyed object` 0, `dmabuf-rejected` 0 - the first session on a whole pool that survived its own end |
| `CmaFree` on that boot | 504864/524288 kB at boot, 465492 kB after: this boot's deaths cost ~39 MB each, against the ~55 MB the `r0.56` classes used to cost |
| manual `r0.58` (`play-ab91.log`, PID 12246) | `session.create 1793565786618` -> `game_launch: title=Dark and Darker appId=102303211` -> `media-ingress consumer stopped elapsed_ms=663688 video=39334 audio=132049 keyframes=5 dropped=5`, 647 present samples, `committed=24677 dropped=4735`, `flushFailed=0` in all 647, `fps` p1 31.9 / p50 **37.7** / p90 39.9 (min 16.5, max 43.0) |
| crash classes in that `r0.58` session | 0 of every class; `dmabuf-rejected` **19** refusals (`opennow-diag: dmabuf-rejected: fd 92 covers 0 bytes, the planes need 2073600/3125760 at stride 1920/1920 height 1080/540, 1 of them so far; not sending the add`) inside 4275 `dropping a frame (the frame could not be exported as a DMA-BUF); continuing` lines |
| the artwork class in that `r0.58` session | `QFile::at: Cannot set file position 180224` / `147456` beside `qt.gui.imageio.jpeg: Corrupt JPEG data: premature end of data segment`, 2 pairs at 20:57:17.379 and 20:57:19.151 in the middle of the stream; `truncated-image` **0**, `Cannot open` 0 - so `0030` had no measured hit and does not answer these two lines, while all 167 `index.json` sizes matched their files on disk and every object ended `FF D9` |
| manual `r0.58`, pool re-armed by the kill (`play-ab92.log`, PID 18317) | `session.create 1793572164693` -> `game_launch: title=Dark and Darker appId=102303211` -> `server-terminated: The server ended the streaming session (0x80030007)` at `1793573029172` -> `media-ingress consumer stopped elapsed_ms=677563 video=40278 audio=134937 keyframes=3 dropped=8 paused=0` -> `session.ended: outcome=ok`, 665 present samples, `committed=36549 dropped=0`, `flushFailed=0` in all 665, `fps` p1 45.9 / p50 **54.8** / p90 56.2 (min 22.8, max 58.0) |
| what the two `r0.58` sessions say together | `ab91` ran on the boot's half-spent pool: p50 37.7, `committed=24677 dropped=4735`, `dmabuf-rejected` **19** inside 4275 `dropping a frame (the frame could not be exported as a DMA-BUF); continuing` lines. `ab92` ran on a pool the kill re-armed: p50 **54.8**, `committed=36549 dropped=0`, `dmabuf-rejected` **0** inside 0 such lines - so `0028` costs a healthy stream nothing, and what it refuses is exactly what a spent pool hands back |
| the artwork class in the `ab92` session | `QFile::at: Cannot set file position 131072` / `229376` beside `qt.gui.imageio.jpeg: Corrupt JPEG data: premature end of data segment`, 2 pairs at 22:43:49.543 and 22:43:51.321, both **after** the last video sample at 22:43:48.745 - the class sits in the interface's artwork reload that follows the session end, and it was 0 all through the 11 min of play; `truncated-image` **0**, `Cannot open` 0 |
| the seek positions across both `r0.58` sessions | 131072 = 32 x 4096, 147456 = 36 x, 180224 = 44 x, 229376 = 56 x - every one an exact multiple of the JPEG plugin's `max_buf = 4096`, which is what makes that mechanism certain rather than inferred |
| the two verdict codes the ends carry | `0x80030007` ended the `r0.57` manual run (804.681 s), `ab91` (663.688 s), `ab92` (677.563 s) and the driven `ab94` (988.407 s); `ab95` ended at 593.690 s with the other verdict, `server-terminated (0x80040004)`, and the streamer says why 30 ms before it: `nvst-video produced-frame stall idle_for=8.008076623s; requested a keyframe` beside `Recovering NVST packet gap: 148848..=151093`. So the gateway's own 8 s produced-frame stall is what costs a session - there is no fixed cap, and the app's Anti-AFK is not what ended any of them: `DesktopAntiAfkAcceptance.qml` asserts `sessions start with Anti-AFK off`, `ab94` and `ab95` both logged the `Ctrl+Shift+K` shortcut reaching the surface (`key 37 vk=0x4b mods=0x3`) and ended anyway |
| the two classes only the `ab93` run carried, both measured once | (a) `zwp_linux_surface_synchronization_v1@30: error 0: invalid fence fd` -> `The Wayland connection experienced a fatal error: Protocol error`, 1 occurrence at 23:16:32.461 and 0 in the four other runs, 46 ms after the interface's own `opennow-present fps=0.0 render=22.8 swapped=0` line: it is the interface's vendor EGL-Wayland client fencing a swapchain image the compositor never received a dmabuf for, i.e. the family `0029` avoids by keeping every fence answered, surfacing here as weston's protocol error instead of the vendor's `buffer_fenced_release` assert, which measured 0 in every `r0.57`/`r0.58` run. Not `0028`: a refused fd returns before `m_deferredFds.append`, before `zwp_linux_buffer_params_v1_add`, and `attachFrame` returns `queued = true` so `present()` neither commits nor closes it - no fence can name it. (b) `present record frame failed: RenderFailed detail=GPU frame recording failed: V4l2 operation failed: failed to map DMA-BUF for the CPU copy: Bad file descriptor (os error 9)`, 2 at 187.0 s and 532.2 s into that session, in `native-streamer.log` only and 0 in the run log: the streamer's recording path asks the capture pool for a buffer for its CPU copy, and with the pool spent the map hands back fd -1 -> EBADFD. Non-fatal, and that same run reads it as 3 `dmabuf-rejected` and 835 `dropping a frame` |
| what the capture pool does across the boot | `CmaFree` read with nothing running went 504864 kB at boot to 280644 kB at the third launch, 251840 at the fourth, 149644 at the fifth and 46184 kB by the end of `ab95` - each death costs it ~55 MB and a kill gives back only the running process's own ~100 MB, so what the later sessions see at the export is the pool, and only a reboot returns it whole |

#### The r0.56 held-stream measurement (1080p, driven)

This is the first hardware-decode measurement of the release fix (`0025`/`0026`),
on a fresh boot of `r0.56` with the app's own `resolution` back at `1920x1080`
and `measure73.sh` driving a real session for 535 s:

| Counter | Value |
| --- | --- |
| window | 535 s of driven play, first frame at 01:27:26 |
| fps | p1 51.9, p50 **55.9**, p90 57.0, p99 58.0, max 58.6; 0% of seconds under 50 |
| `committed` / `dropped` | **31019 / 0** |
| `flushFailed` | **0** (it was 2014 at `committed=2013` before `0027`) |
| `/proc/PID/fd` | first 41, last **97**, min 39, max 100, over 502 samples and 31k presents |
| `opennow-qt` VmRSS | 189.7 MB min, 242.8 MB max |
| `dmesg` | 0 `dma alloc of size`, 0 `core0 hang` for the whole session |
| input | ESC `key 1 vk=0x1b mods=0x0 pressed=1` then `pressed=0` against the live window at +150 s |

The descriptor count is the number that matters. It rises from 39 to ~97 while
the decoder's pool and the surface's 24-entry `wl_buffer` cache fill, and then
stays there: 31019 presented frames later it is still 97, with a maximum of 100.
The r0.53 session that closed nothing went 39 → 1024 (the old soft limit) in 901
presents, about 1.13 per present, and left the capture objects behind each time
it died. Nothing leaked into the capture pool in this run either: `dmesg`
carried no allocation failure and no GPU hang for the whole 535 s.

The one thing this run does not show is the close side of the window: ESC opens
the game's menu, it does not end the stream, so `opennow-interface-shown` is
still owed by a session that ends through the window itself.

The four axes the series was chasing hold while the stream is up, in the Nov-1
session `play-ab84.log` of the same build:

| Axis | Value |
| --- | --- |
| present rate | `NVST rx-stats elapsed=370.2s frames=21414` = **57.9 fps**, `dropped=0` |
| handoff lag | `deliveryToAdmission p50=1.71 p95=3.32 max=6.71`, `admissionToControlQueue p50=27.4`, `qosOwdUs=416-7145` |
| wire | `qosBweKbps=42852-47083`, 1.63 Mbit/s measured on `eth0` against a 100 Mbit/s link, `gaps=0`, gateway ping 1.6 ms at 0% loss |
| audio | `audio-jitter target_ms=200 queued=17`, `underflows` 15 to 16 over a 10 s window, no `queue-dropped` |
| input | user-verified on the board (controller and keyboard reach the game); the measured ESC press is item 10's `ab77-1-2.log` |

That session then died for the reason item 11 records, so these numbers say what
the release holds while it is up, not what ends it.



### OpenNOWLite

`recipes-apps/opennowlite` is the plan-B client: its own Wayland window, its own
GStreamer video and audio chain, and OpenNOW's Rust streamer used only to carry
input and the already-encoded media to Nvidia. It is a separate recipe, a
separate package and a separate source file from `opennow`: the two install side
by side - `/usr/bin/opennow` against `/usr/bin/opennowlite` (the launcher) and
`/usr/bin/opennow-lite` (the client), plus this recipe's own
`/usr/lib/opennowlite/libopennowlite_streamer_ffi.so` against OpenNOW's own
`/usr/lib/libopennow_streamer_ffi.so` - with no shared path, and nothing of the
opennow patch series is reused. OpenNOWLite pins OpenNOW
**v1.0.4-nightly.1515.1** (`SRCREV = "b3fe71026d8261d386c6f16fcdeb1596ebb85942"`)
in its own recipe with its own one-patch series: `0001-hand-encoded-media-to-the-host.patch`
adds `EncodedMediaKind`/`EncodedMediaPacket`/`EncodedMediaTap`, `MediaSink::with_tap()`,
`spawn_passthrough_linux()`, a `validate_backend()` bypass for a device-less engine,
and the `encoded_media_callback` plus `media_passthrough` fields of
`OpenNowStreamerConfig`. The library is dlopened by absolute path out of this
recipe's own directory and the app links `-ldl` only, so the process has no
`DT_NEEDED` entry for OpenNOW's SONAME and can never load, shadow or replace the
library the opennow recipe ships. The patched tree is also kept in a standalone
repository next to the layer, `/home/ubuntu/Dokument/Github/OpenNOWLite`, so the
same source can be read outside the build.

The design, in the order the pieces were measured:

* **The window is OpenNOWLite's own.** `opennow-lite.c` creates an xdg-shell
  toplevel (`app_id=opennowlite`, title `OpenNOWLite`, 1920x1080 by default) and
  the seat's `wl_keyboard` and `wl_pointer` listeners on the same `wl_display`.
  opennow-qt is not involved at all, and because the same connection owns the
  window, the seat's keyboard keeps focus on it: the opennow series' focus
  problem (open item 13) does not exist on this path.
* **waylandsink draws into that window** through `gst_wl_display_handle_context_new()`
  (`GstWlDisplayHandleContextType`, from `gstreamer-wayland-1.0`) for the `wl_display`
  and `GstVideoOverlay` for the `wl_surface`, the same hand-off this layer uses for
  YtGst, plus `gst_video_overlay_set_render_rectangle(0, 0, width, height)` before the
  first frame - without it the sink fails with `Window has no size set` and stops.
* **A view of that window only exists once the surface has content.** waylandsink
  renders into a *subsurface* (`gst_wl_window_new_in_surface`), and weston maps a view
  when the surface itself is painted, so nothing was visible until the app committed its
  own XRGB8888 shm frame (a dark slate, `0x00101820`) after the first configure. Stable
  xdg-shell has no `xdg_toplevel_set_size` - that call failed to link - so the size comes
  from `xdg_surface_set_window_geometry(0, 0, width, height)` plus the attached buffer.
* **The session is the shipped core's.** `opennow-core` is asked over its RPC, which is
  *two one-way pipes*, not a socketpair: the handshake is `core.hello {"protocolVersion":6,...}`
  and after `session.create` the client **must** answer `{"type":"ack","id":"lite-create"}`
  or the core never continues. Replies carry no `type` member, so they are matched by `"ok"`
  plus the id. `hello`, `game.get`, `launch.inspect`, `create` and `prepare` wait 60 s
  (`OPENNOWLITE_RPC_TIMEOUT`, because the core's own credential/Electron startup and the vendor
  round trips run before the first request is read), while `session.poll` keeps 25 s and is
  re-asked ~2 s apart; `launch.inspect` answers with `scope`/`status`, and the vendor holds
  `preparing` (`status=1`) for 100-140 s before `status` is 2 or 3. Shutdown is closing the
  request end.
* **Media tap hands over assembled units**, so each is pushed straight into its `appsrc`:
  one video packet is one complete H.264 access unit (caps `video/x-h264,stream-format=byte-stream,alignment=au`,
  and the decoder on this board is `v4l2slh264dec`, the V4L2 stateless element, which outputs
  NV12 straight into waylandsink with no `videoconvert` anywhere in the chain), one audio packet
  is one complete Opus frame already unwrapped from RFC 2198 RED by `NvstAudioReceiver::depacketize`.
  The audio chain is `appsrc ! queue leaky=downstream max-size-buffers=200 ! opusdec ! autoaudiosink`
  with caps `audio/x-opus,channel-mapping-family=0` - the channel count is left to the packet,
  since pinning `channels=2` disagreed with a mono stream and made `opusdec` report a stream error
  on the first packet - and audio is fed at 200 frames a second (5 ms) against the 60 video units
  a second. `opusdec` needed the `gstreamer1.0-plugins-base` opus split package (`gstreamer1.0-plugins-base-opus`,
  which `layers/meta-watermelon-wine`'s bbappend turns that feature on for) and `libopus` in `RDEPENDS`.
* **An `appsrc` defaults to `GST_FORMAT_BYTES`, and that is what killed the audio.** The
  segment event it posts says "bytes", `GstAudioDecoder` answers `unsupported format;
  ignoring` and the appsrc itself posts `Internal data stream error` / `streaming stopped,
  reason error (-5)` - which reads as a video-bus error because it comes from the appsrc.
  The fix is `"format", GST_FORMAT_TIME` in `media_source_new`: the PTS on these buffers is
  already nanoseconds, so time is also the honest format. Verified offline on the seat against
  the real first packet (`/tmp/audiotest.py` plus `/tmp/opennowlite-audio1.bin`, 295 bytes):
  pushed with the default it errored, pushed with a time segment it decoded. `gst-launch`/`filesrc`
  tests are *not* a valid stand-in - they produce a BYTES segment anyway.
* **`block` on the appsrc stays FALSE**, and this is load-bearing: the push is made from the
  streamer's own media-callback thread, so a blocking push past `max-bytes` stops the feed for
  the rest of the session (measured on r0.3: the picture froze after 7 min, the thread named
  `opennow-media-c` sat in a `g_cond_wait` inside `libgstapp-1.0.so` under `gdb`, the tallies
  stayed at `video units=25200`/`audio packets=85500` for the next 40 min while the streamer's
  own `NVST rx-stats` kept counting 35808 frames per 600 s). A refused push is counted instead
  (`video refused=N`), and the appsrc's own queue depth (`current-level-bytes`, not
  `gst_app_src_get_size`, which answers `uint64 -1` here) is printed beside every tally, in
  bytes: at 60 units a second a chain that keeps up holds under one unit of picture (~30 KiB),
  so a growing `queued=` is what a falling-behind decoder reads as, rather than a frozen picture.
* **Audio failures must not end the session.** `drain_audio_bus` logs every message with
  `GST_MESSAGE_SRC_NAME` (the first error read is usually the appsrc's "internal data stream
  error", which only says something downstream failed), and `media_audio_teardown` sets
  `audio_ready = false` and puts the pipeline in `GST_STATE_NULL` while keeping the objects
  alive until exit, because the streamer's media thread may still be inside
  `gst_app_src_push_buffer`.
* **The seat's input numbering is evdev plus a vendor mapping.** A `wl_keyboard.key` event
  carries the Wayland code, which is the evdev code plus 8; the authoritative table is upstream's
  `PhysicalKeyMapData.h` `Layout.virtualKeys[128]` indexed by **evdev**, and the specials come from
  `StreamVideoItemInput.cpp` (Return `0x0d`, Escape `0x1b`, Backspace `0x08`, Tab `0x09`, the
  modifiers `0xa0`-`0xa5`, Super `0x5b`/`0x5c`, arrows, F-keys, keypad). The ad-hoc `evdev + 8`
  table this client had before had **no Return at all** - Enter was silently dropped - and mapped
  Escape as Tab, so the exit shortcut could never fire. Wire modifiers come from upstream's
  `inputModifiers`: `0x01` shift, `0x02` control, `0x04` alt, `0x08` meta (xkb bits 0, 2, 3, 6),
  which is why the quit shortcut mask is now `0x0d` (the old `0x7` asked for shift, caps-lock and
  control and could never fire).
* **Buttons are the seat's evdev codes, not 1/2/3.** A `wl_pointer.button` event carries BTN_LEFT
  `0x110` (272), BTN_RIGHT 273 and BTN_MIDDLE 274; the FFI validates `1..=5` and upstream's
  `windows_raw_input.rs` `BUTTON_FLAGS` maps left to 1, middle to 2 and right to 3, so this client
  maps 272 to 1, 274 to 2 and 273 to 3. Sending 0 for the left button was measured as `button
  submit refused (2)` (`INVALID_CONFIG`), and comparing the event against 1 meant the click never
  reached the game at all.
* **The pointer is absolute, not relative.** This was the measured bug: a client that sent relative
  deltas had 100 pointer buttons and 28 keys reach the FFI with no refusal and the seat's own arrow
  move, while nothing in the stream ever did. The remote sends a cursor notification
  (`NVST cursor capture tx: command=0x0308 ... reason=cursor-notification`), OpenNOW's own default is
  absolute (`relativeMouse` is an opt-in QML setting), and the FFI publishes it with
  `submit_mouse_absolute(handle, x, y, width, height)` in video pixel coordinates: upstream clamps
  the point inside the aspect-fit viewport and sends the viewport's extent in the same packet, and the
  FFI rejects `width == 0 || height == 0`. weston reports the cursor inside *the surface it hovers*, so
  the coordinates only mean something while that surface is this window (`a->pointer_surface == a->surface`),
  and the window keeps the video's aspect ratio, so window pixels need no rescaling. Upstream publishes
  a position on hover, on every button transition (press *and* release, position first, because no move
  event is guaranteed before a click) and on wheel, so the vendor's hit-testing and its clicks agree; a
  wheel step is 120 units of a turn on weston, on Windows (`WHEEL_DELTA`) and in GFN's packet, so the raw
  value goes through unchanged - dividing by 120 made the scroll invisible.
* **The client drives the cloud game's own notices** (`--auto-pilot`), and the measured shape of that
  chain is one event per notice, ~135 s apart: extra presses of one clock close the host's own windows
  and leave a bare desktop. The armed defaults are the measured offsets, `--pilot-at=63:r,212:r`: one
  Return at +63 s answers the consent notice and one Return at +212 s answers the splash's PRESS ENTER
  TO PLAY. **Every event after the notice list is a pointer jiggle, not a key** - `--pilot-gap=60`,
  `--pilot-times=64`, so the first is at +272 s and the last at +4052 s, past a free-tier hour - one
  absolute submit at the window's middle plus or minus 40 px, the side alternating with the event number,
  logged as `opennowlite-pilot: jiggle N x= y= at +Ns due=+Ns` and counted in the exit tally as
  `jiggle=%lu`. Keys after the list are measured to fail, and the failure is the game's own: on the r1.3
  round of Nov 7 the Escape armed at +270 s reached the corner hint that character screen carries,
  `Esc Exit to Desktop`, which opened Destiny 2's own `ARE YOU SURE? ... quit your current session and
  exit to desktop` over the guardian (the witness reads it at +280 s and +290 s, the screen's build line
  `9.7.0.6` still in the corner), and the *next* Enter of the same clock, the one armed at +330 s,
  answered that confirm - the session went back to the Steam desktop (the store page at +299 s) and the
  vendor then sent no media at all, `NVST rx-stats elapsed=320.1s ... frames=18298` being that round's
  last line, while `session.poll` kept answering `ok` in the core's own diagnostics and the client kept
  taking input. A window holding one frame for the next ten minutes is what reads on the TV as an
  "extremely slow" picture rather than as a stopped one, which is why a pointer move - activity the
  host's idle timer counts, though the round measured below shows that once a minute of it is not
  enough to hold the hour unattended - replaced the keep-keys in `r1.4`.
* **The clock's own gap was a bug of its own** (found and fixed between r1.2 and r1.3): the keep events
  after the list were armed on a sliding `last + gap` that the main loop re-based, so they fired at
  +282 s, +330 s, +390 s, +450 s, ... instead of exactly 60 s apart. r1.3 arms each event at its own
  `due` and the round killed from pids 267195/267200/267245 shows the presses at +630 s, +690 s, +750 s
  ... +1050 s, exactly 60 s apart (`stopping ... submits=34 refused=0 skipped=0 pilot=16`).
* **The window presents the game at 59.33 fps, so neither GStreamer nor the window is what makes the
  picture look slow.** `fpswin.sh` (seat copy `/tmp/fpswin.sh`) feeds `/tmp/clip-h264.h264`, a slice of
  the real 60 fps stream cut out of a witness: 1801 frames, md5 `1f3f5dbdc13f320f42132835c0b2f403`.
  Through **the client's own window** with the chain
  `filesrc location=CLIP ! h264parse ! v4l2slh264dec ! waylandsink name=sink` and `sync=true` (the sink
  waits until the compositor has taken the frame, so the wallclock is the rate the window showed):
  **1801 frames in 30355 ms = 59.331 fps**. The same chain through `gst-launch-1.0`'s own top-level
  waylandsink: 30244 ms = 59.549 fps. So the client's hand-off costs 0.2 fps of its own window. With
  `sync=false` the same client window is fed at 123 fps (14615 ms), so the decode is not the limit
  either. The other numbers on the table are benches of other shapes, not of the game: decode into
  `fakesink` (`fpsbench.sh`, `fpscmp.sh`: 47.33 s for 1801 frames = 38.05 fps, and 47.35 s on the
  repeat) is a chain with no element holding the decoder's output, so the stateless decoder recycles
  its own buffers and that rate is the bench's shape - the same chain with a `queue` in front of the
  sink ran 17.28 s = 104 fps - and `avdec_h264` into `fakesink` costs about the same (48066 ms = 37.47
  fps), so the two decoders are interchangeable here; and `--test-pattern --pipeline` demos read under
  6 fps because `videotestsrc`'s own 1080p drawing is what is in that chain (`fpsbench2.sh`: 1800 frames
  of `pattern=clock` did not finish in 600 s), not the game. What is slow on this seat is a *stopped*
  window, and the pilot of `r1.4` is the fix for it.
* **The picture is recorded so a screen nobody is looking at can still be read**
  (`--record=FILE --record-seconds=S`). The tap's units are whole access units with their start codes,
  so writing them out in order gives a raw H.264 stream: `ffprobe` reads it as 1920x1080 yuv420p at
  60 fps (a 480 s record is 269660160 bytes) and `ffmpeg -i FILE -vf "select='eq(n\,16200)'" -vsync
  vfr out.png` gives a frame that can be looked at. `-ss` does not work on that file - the raw stream
  has no index, so ffmpeg answers `could not seek to position 55.000` - which is why the witness is
  selected by frame number in one decode pass. This is the witness for the seat, because
  `weston-screenshooter` answers `Output capture error: unauthorized` while this client holds the
  window.

Measured on the board, the current release being `opennowlite 1.0.4+nightly.1515.1+git0+b3fe71026d-r1.4`
(deb md5 `6135e38dea5a8b70a838ab2f95e1a159`, client `6b0af66da05cef5ed45527fbd48183ee`, launcher
`d0102c5535a4eb16dd28e2402796007a` and FFI library `0c79eeec9a9a2a1e33fbe444dfe297f3` unchanged since
r1.0, verified host to board by md5 at both hops; the PKGR progression is r1.0 (absolute pointer)
`e2279a6249be103e6c26f32fe0291903`/`e6db9ac0196792e2b686934f498749a0`, r1.1 (plus `--record`)
`eb2d0e0437fb338375869714a4590e32`/`12742f9b5211735965118f40ce4764e5`, r1.2 (list-based pilot clock)
`69cdaa592c50262b6acce3e8fa20f366`/`f92d3f251a7786c036428cb91b4a0b42`, r1.3 (per-press key letters,
keep-gap fix, deduped `--help`) `140666db4506a3cb13dbdeb5fe33bdf2`/`c9d2cf1ebc0e7219c60c5d3c9c81fc71`,
r1.4 (pointer-jiggle keep events) `6135e38dea5a8b70a838ab2f95e1a159`/`6b0af66da05cef5ed45527fbd48183ee`):

`/home/weston/lite-run.sh` is the reproduction that takes a seat session up and keeps the vendor queue
visible, and `/home/weston/lite-pilot.sh` (md5 `38182802c0c96fa3cc1729a59354b582`) is the unattended
version of the same thing: `ROUND_TIMEOUT`, `MAX_ROUNDS`, `MAX_PLAY` and `REC_SECONDS` are overridable
from the environment, and each round writes its own `/home/weston/.runs/lite-pilot-<TAG>-<round>.log`
plus its `.h264` witness, where `<TAG>` is the seat's own start time, so a relaunch never truncates the
evidence of a previous one. It moves the stale `unresolved-allocation.json` aside to
`/home/weston/backup/unresolved-allocation.json.pilot-<TAG>` before every relaunch (the core refuses
every new session while that record exists - the Nov 7 launches left one behind at 12:06, 12:24, 12:49,
14:09, 14:35, 15:12, 16:17 and 18:24) and only ever kills the PIDs it recorded for its own client and
core, never a pattern. The marker lines the log carries are the ones listed in the source's own header:
`window ready`, `rpc <method> line=<n>`, `reply for <id> in <n> ms`, `session ready for <id>`, `engine
started for <id>`, `encoded video unit 1` / `encoded audio packet 1` (with `bytes=290 channels=2
rate=48000`), `video units=N queued=N` / `audio packets=N queued=N`, `recording N s of picture into
FILE`, `pilot armed with N events, first at +Ns`, `pilot: press N vk=0x.. at +Ns due=+Ns`,
`pilot: jiggle N x= y= at +Ns due=+Ns`, `pointer outside the window ignored`, `<kind> submit refused
(<n>)` and the exit tally `stopping keys= motions= submits= refused= skipped= pilot= jiggle=`.

The free hour was held end to end by an unattended r1.2 round: script pid 193561, client pid 193566,
core pid 193612, round 1 started 15:12:34 seat time, 3907 s of wall, and the streamer's own counters
ended at `NVST rx-stats elapsed=3600.6s frames=213596` - 59.4 frames a second reaching the client for a
full hour - with the exit tally `opennowlite: stopping keys=12 motions=1734 submits=2111 refused=0
skipped=0 pilot=13`, `opennowlite: record closed bytes=358425830`, `opennowlite: stream reply lite-stop
ok` and `opennowlite: engine stopped`. The witness came back to this repository's own tree byte-identical
(`/tmp/opencode/work/witness-r12.h264`, md5 verified against the seat's copy), and the seat's keyboard
reached the client during it (`keys=12`). A human at the seat also confirmed the picture's colours read
brighter than the opennow series' own window, which is consistent with open item 6: this client's NV12
goes from `v4l2slh264dec` to `waylandsink` with no `videoconvert` and no tone map on the board, so the
difference is the TV's own picture processing on the SDR signal it is handed.

The r1.4 build's own round of Nov 7 (script pid 309057, client pid 309062, core pid 309108, round 1
started 20:11:19 seat time, `/home/weston/.runs/lite-pilot-1107-2011-1.log` plus its witness) is that
pilot fix measured: the log armed `pilot armed with 66 presses, first at +63s`, answered the consent
notice at +63 s and the splash at +212 s, and then logged jiggles at +272 s, +332 s, ... +1532 s. At
media +1550 s the tallies read `video units=91800 queued=0` (a third of one unit of picture held in
the appsrc, so the decoder kept up) against `NVST rx-stats elapsed=1550.2s frames=92151` - 59.4 frames
a second arriving for 25 1/2 min, where the r1.3 round of the same day had already stopped arriving at
+320 s. That round's witness (269660160 bytes for the first 480 s of picture, md5 `383b3f3d6758e29bcdd78a03f89ca908`
verified seat to local) reads: Steam's store carousel at +90 s, Destiny 2's own dark network loading art
at +220 s - the PLAY answer at +212 s is what opened it - and the character screen at +270 s, +330 s,
+420 s and +470 s, the guardian standing with its `Hunter`/`ELO` class card and the corner's only key
hint reading `Esc Exit to Desktop`, with no quit confirmation anywhere in those frames and the guardian's
idle pose different between the +270 s and the +420 s one, so the window was advancing rather than
holding a frame. The round ran to its full 3900 s cut-off and answered `SIGTERM` with
`opennowlite: stopping keys=0 motions=88 submits=164 refused=0 skipped=0 pilot=2 jiggle=58`,
`opennowlite: record closed bytes=269661582`, `opennowlite: stream reply lite-stop ok` and
`opennowlite: engine stopped`: the pilot pressed exactly twice, moved the cursor 58 times, and 88 pointer
positions rode the wire with 0 refused. The media, though, stopped on its own at 1720 s of picture -
101810 frames, 59.2 a second up to that line, `qosUtilPercent=3` on the `control-stats` line after it and
no further `rx-stats` line at all for the next 32 min, while the jiggles kept logging to +3692 s and the
core kept answering `session.poll: outcome=ok` - so the unattended r1.4 round held 28 1/2 min of picture
against the r1.2 hour round's 3600.6 s, and the two rounds differ on one thing: 88 pointer positions here
against 1734 there, which is what a human's mouse makes and a jiggle clock does not.

Open items for OpenNOWLite, recorded so they are not mistaken for done:

1. **An unattended seat held 28 1/2 min of picture on r1.4, not the hour, and two things are owed.** The
   media stops on its own at 1720 s with the session still alive and the jiggles still logging, while the
   attended r1.2 round above held 3600.6 s of picture - the difference the logs can see is the pointer:
   88 positions sent here against 1734 there. So the first thing owed is a keep clock that reads like a
   hand (a cursor that moves every second or two rather than once a minute, which is what the 60 s jiggle
   clock is), and the second is the character screen, which waits for the player's own choice: `--pilot-click`
   with `--pilot-point` on the class card the witness can name is this client's only lever for that step,
   and whether such a click leaves the game's own menus is unmeasured. A key is not a substitute for either
   - keys after the notice list are what opened the exit shortcut above.
2. **The input has no in-log proof of crossing the wire.** The FFI answers `OPENNOW_STREAMER_OK` for an
   event it dropped (the gate refuses while its owner is inactive or the NVST feedback `input_available`
   is false), and the opennow series' `NVST embedded input tally:`/`native input drain:` lines belong to
   *its* patch series, not this one, so a lite run's log cannot say how many presses rode the wire. What
   it can say is which calls were refused and that the input handshake happened
   (`NVST cursor capture tx: command=0x0308 enabled=true reason=input-activation`,
   `NVST remote cursor tracking tx: command=0x030d enabled=true`). An input tally in this recipe's own
   patch series is the next thing to land.
3. **`weston-screenshooter` is unauthorized while this client holds the window**, so the picture is
   witnessed by the recorded H.264 file, not by a screenshot, and reading a frame costs a full decode
   pass over the file.
4. **The seat's `autoaudiosink` warns `Failed to connect: Connection refused`** (no PulseAudio on the
   board) and still plays through ALSA; whether the TV's own speaker is the one that carries the game's
   audio is a matter for a human ear.
5. **The seat's free-tier hour and its free disk pull against each other.** A 480 s witness is ~270 MB
   and the board's root filesystem has ~1.2 G free, so a fetched witness is deleted on the seat after its
   md5 has been verified against the local copy; that is what keeps a second round's witness inside the
   space.

### YtGst

Start `ytgst`. Requires GStreamer plugins, the `yt-dlp` tool and PulseAudio
for audio output.

---

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| OpenNOW build fails on Cargo | Ensure crates.io is reachable during `do_compile` |
| OpenNOW configure fails with `unknown feature` | `OPENNOW_CARGO_FEATURES` names a feature the upstream crate does not define. Only the three in the table above exist |
| OpenNOW does not start at all | The launcher's `QT_QPA_PLATFORM=wayland-egl` is what this board needs; Qt's default wayland plugin cannot create a Vulkan instance |
| A stream dies at the first presented frame with `buffer_fenced_release` | The VeriSilicon egl-wayland client rejects a swapchain image whose previous release fence is still unanswered. Patch `0014` makes the interface window's swap interval default to 2 vsyncs for exactly this; `OPENNOW_SWAP_INTERVAL=1` selects upstream's behaviour and reproduces the crash |
| The picture is smooth but `flushFailed` is nearly `committed` | Fixed by patch `0027`. `wl_display_flush` returns the number of bytes it sent, so testing `== 0` read every present as a failure. On `r0.56` `flushFailed` stays at 0 for 31019 presents; if it moves again, read it as a real flush failure |
| `QFile::at: Cannot set file position N` beside `qt.gui.imageio.jpeg: Corrupt JPEG data: premature end of data segment` | Cosmetic, artwork only, and no layer fix is needed: the stream keeps its rate through them (2 pairs at 20:57:17.379 and 20:57:19.151 inside an `r0.58` session still streaming at p50 37.7 fps with `flushFailed=0`; 7 pairs inside one second in `c53` at 53.0 fps). The positions are Qt's own JPEG-plugin blocks past the end of a **whole** file - `180224 = 44 x 4096`, `147456 = 36 x 4096`, and `src/plugins/imageformats/jpeg/qjpeghandler.cpp` reads the device in `max_buf = 4096` blocks - so libjpeg's entropy stream reached EOF before its own EOI (that is the notice), `resync_to_restart = jpeg_resync_to_restart` asked the `QFile` for the next restart marker, the block-boundary seek landed past EOF, `QFile` refused it and warned, and the plugin installed its fake EOI and finished the decode. The objects there are baseline JPEGs ending `FF D9` (145090 and 175462 bytes) with `index.json` sizes matching disk, so there is no short object and nothing to avoid. The file the interface cannot open at all prints `Cannot open: <url>` instead, which the app already avoids through the empty `resolvedUrl` in `ArtworkSource.qml`; this boot logged 0 of those |
| The picture stops and the streamer's last line is `server-terminated: The server ended the streaming session (0x80030007)` | Not an app-side end and not a layer's fix: the provider closed the session. `media-ingress consumer stopped elapsed_ms=804681 video=47803 audio=160359 keyframes=3 dropped=5` says the stream carried its frames up to that verdict, and the app answers with `qt-to-native id=qt-4 type=stop`, `engine-command end id=qt-4 type=stop success=true` and `session.ended: outcome=ok`, so nothing crashes and the window used to stay on screen with the last frame; `0031` now closes it, see the row below. The answer is a new session, and `--auto-launch-ms=150000` presses Start for you |
| The stream window stays on screen with a frozen picture after `server-terminated` | Fixed by patch `0031` (built as `r0.59`): the end answers with `native-to-qt type=status status=stopped`, so the app follows that answer and unmaps the window. Both sides are greppable - `opennow-server-end-closes-window: the server ended the session; unmapping the stream window` and the surface's own `wayland-video: stream window closed` - and the interface, which `0029` leaves painting underneath, is what the screen comes back to |
| The cloud game opens behind a pop-up that wants an Enter, and nobody sits at the keyboard | Fixed by patch `0032` (built as `r0.60`): the stream window answers with an Enter of its own on a clock, `OPENNOW_POPUP_ENTER_MS=30000 OPENNOW_POPUP_ENTER_TIMES=12` arms it 30 s after the window became live and repeats the press 5 s apart, 12 times. The press is the streamer's own key API, so it is the same `vk=0xd` Enter a physical key becomes - the `r0.59` run proved that half from the other side, logging `wayland-video input: key 28 vk=0xd mods=0x0 pressed=1` for ydotool's evdev 28 - and it needs nothing from the compositor, which is what item 13 records the compositor doing to the keyboard behind a press. Both sides of the answer are greppable: `wayland-video input: opening enter clock armed: %d ms then %d more at 5s` and `wayland-video input: opening enter answered (%d left)`. `OPENNOW_POPUP_ENTER_MS` unset arms nothing, which leaves every earlier series' run unchanged, and the teardown stops the clock, so a session that ended cannot press Enter into whatever the interface comes back to |
| The clock answered the pop-up and the notice is still up | Fixed by patch `0033` (built as `r0.61`): a press the cloud host reads is a press that is still down when it samples. The `r0.60` clock answered 12 times through the window's own key API - `wayland-video input: opening enter clock armed: 30000 ms then 11 more at 5s` beside `wayland-video: stream window created`, answers 5.00-5.01 s apart - and the notice stayed up until the player pressed Enter at the board, which the window logged as `wayland-video input: key 28 vk=0xd mods=0x0 pressed=1` at 11:15:46.301 with `pressed=0` at `.333`: 32 ms of key held down. `0033` keeps the answer on the same clock but sends the press on one round and the release on the next, whose interval is `OPENNOW_POPUP_ENTER_HOLD_MS` (120 ms when unset), so the press is a hold the host can read; greppable side by side, `wayland-video input: opening enter held for %d ms and released` next to `wayland-video input: opening enter answered (%d left)`. The teardown releases a press the clock was still holding, so a session that ended cannot leave Enter down in the game |
| The stream window appears and closes again ~150 ms later with `the compositor connection failed: Socket operation on non-socket` | Fixed by patch `0036` (built as `r0.63`): ENOTSOCK is what sendmsg answers when the descriptor is not a socket, so the surface's own connection number had been closed and recycled by then; `present()` closes every frame descriptor no request carried, and one of those numbers is the compositor connection. It refuses that number now and says so once, `opennow-diag: frame fd %d is the compositor connection; not closing it` |
| Nothing on the board can show what the cloud host put on the screen | Answered by patch `0035` (built as `r0.63`): the app's screenshot shortcut cannot be pressed while a stream is up, because the stream window holds the seat's keyboard and forwards the key to the host. `OPENNOW_STREAM_SCREENSHOT_MS` arms the same capture path on a clock (`OPENNOW_STREAM_SCREENSHOT_TIMES` is how many to keep, 4 when unset) and the picture is written where the shortcut writes it, under `Pictures/OpenNOW/Screenshots`; the two ends are `opennow-stream-screenshot: armed every %d ms, %d times` and `opennow-stream-screenshot: saved %s` |
| The pointer moves over the stream but a click does nothing in the game | Fixed by patch `0034` (built as `r0.62`): the window asked the seat for its `wl_pointer` only when the seat advertised the pointer capability, and on this board that arrives with the first pointer event, so the window had no pointer at all for the first seconds of the stream (`play-dstny9.log`: window created 16:43:56.312, `wayland-video input: the seat now has a pointer` 16:44:03.843). It asks for it at bind now, and the capability event replaces the inert object weston hands out for a seat that never had a pointer (`unbind_pointer_client_resource` nulls the user data before it touches it). The click also carries the last position the compositor reported immediately before the transition, because the streamer clears captured input until the host reports its input channel ready and a dropped motion otherwise leaves the button wherever the host cursor still was. Both ends are greppable: `wayland-video input: streamer capture requested=%d inputAllowed=%d` and `wayland-video input: button %u submit refused status=%d` |
| The window logged its presses, 0 of them refused, and the drain says it forwarded none | Both halves are counters now, and the middle is measurable: `0038` (built as `r0.65`) prints `NVST native input drain: available={} clearedPolls={} keys={} absolutes={} moves={} buttons={}` once a pass, and `0039` (built as `r0.66`) prints `NVST embedded input tally: active={} pushed={} dropped={} keys={} … queue={:p}` at the queue's own gate and the same address on the drain's line. Armed together with `OPENNOW_NVST_TRACE=1`, which is what makes the streamer's HID wire lines print at all (`nvst.rs:198 verbose_diagnostics_enabled()`; without it a count of 0 for `NVST native input rx` proves nothing), the `r0.66` run says the presses never entered the queue: `tally: active=false pushed=1 dropped=48 keys=48`, the one absolute the seat made went in with `active=true`, and both sides name one queue `0x295638d0`. The gate is the app's own doing - `StreamVideoItem::syncCaptureState` calls the streamer's `setCaptureActive` directly and silently, and the stream window's own state says active, so nothing re-arms it; `0040` of `r0.67` answers it - the window re-asserts the capture state it asked for before each press and once a second, and the streamer prints the gate's own state changes |
| A stream dies 78 s to 3.5 min in with `zwp_linux_surface_synchronization_v1@30: error 0: invalid fence fd` and `The Wayland connection experienced a fatal error: Protocol error` | Not fixed, and the series keeps meeting it (open item 14 below): the r0.65 attempt died there after 78 s of picture, the r0.66 attempt 101 s in, and each attempt has to be relaunched because of it. The `buffer_fenced_release` assert of the r0.57/r0.58 runs is the same family of death from the other side |

| The stream shows `ARE YOU SURE? … quit your current session and exit to desktop` with `Enter Confirm` / `Esc Cancel` | Not a fault, and not the host: an Esc reached the character screen, whose only visible key hint is the corner's `Esc Exit to Desktop`, so the press asked the game to quit. The notice waits there for the rest of the session; `Esc` answers its "Cancel" and keeps the session, `Enter` ends the cloud game. The measured chain arms only the consent and the PLAY clocks, which never reaches this notice |
| The stream dies with `zwp_linux_surface_synchronization_v1@30: error 0: invalid fence fd`, with `The Wayland connection experienced a fatal error: Bad file descriptor`, or with the vendor `buffer_fenced_release: Assertion 'buffer->release_fence_fd == -1' failed` | One family, three readings: the process's descriptor table is shared ground. The board's EGL driver carries weston's egl-wayland client and hands the compositor a release FENCE for every swapchain image, as a file descriptor, and until `0043` every DMA-BUF duplicate the streamer made asked for the lowest free number (`, 3` and `, 0`) - 55 times a second inside a table of about a hundred numbers that also holds `anon_inode:sync_file`, `/dev/galcore`, the driver's event fds, the compositor's sockets and 37 `/dmabuf:` objects. A fence arrives naming a number the exports just took, or an import arrives naming one just closed. `0043` gives the duplicates a band of their own (`EXPORT_FD_BAND` = 4096, one line per process: `opennow-diag: dma-buf export fd band: duplicates start at 4096`); the driver's band and the streamer's never overlap again. A session that dies minutes in at a clean 50/s, with `dropped=0 flushFailed=0`, is this and not a rate problem |

| A launch freezes minutes in: only `opennow-present fps=0.0 render=0.0 swapped=0` grows, the log stops growing while both apps stay alive (`opennow-qt` in `wchan=do_sys_poll`, `opennow-core` on `pipe_read`), and `dmesg` shows `[galcore]: GPU[0] core0 hang, automatic recovery.` | Not fixed: the same Vivante/driver death family as the starved capture pool (open item 8) - the lost compositor reply after a driver core hang leaves the interface unpainted and the launch never reaches a stream window. `r0.67`'s first attempt died exactly there; the session has to be relaunched, and the attempt driver now calls a log that stops growing a dead launch instead of waiting out its ten-minute budget |

| `Failed to import plane 0 as R8`, then the stream window closes |
 The decoder retired the frame before the compositor imported it. Patch `0012` pins exactly one handoff frame; a wider ring pins the capture pool and breaks the stream instead |
| `invalid arguments for zwp_linux_buffer_params_v1.add` after ~20 s | The compositor's 1024-descriptor limit. The surface reuses one `wl_buffer` per decoder buffer, so check whether the decoder's pool changed shape |
| `dma alloc of size … failed` / `page allocation failure: order:10` in `dmesg` | The decoder needs CMA and a contiguous pool: `cma=512M` on the kernel command line, and headroom above ~756 MiB available |
| Audio arrives but plays late | Not fixed yet. The wire is clean (0 dropped, 200 packets/s in) and OpenNOW's low-latency queue is what throws the stale packets — see the open items below |
| `ESC` never reaches the game | Not fixed yet. Every other key and the mouse do, and `F11` reaches the app, so the key is consumed inside Qt rather than dropped on the floor — see the open items below |
| No hardware-accelerated video in OpenNOW | OpenNOW reports no usable backend. With the default build it needs a V4L2 memory-to-memory node offering NV12 or I420; with `linux-vaapi` it needs a stateless node. Confirm with `v4l2-ctl -d /dev/video0 --list-formats-ext` |
| QtWebEngine build is slow / OOM on 8 GB host | Lower build parallelism via `PARALLEL_MAKE = "-j<N>"` in `local.conf` |

### Open items on this board

Recorded here so the next reader does not mistake them for fixed:

1. **`ESC` does not reach the game** - fixed by patch `0017` and verified on the
   board: `ydotool key 1:1 1:0` against the stream window logs
   `wayland-video input: key 1 vk=0x1b mods=0x0 pressed=1` and then `pressed=0`, so
   the key does reach the host.
2. **`F11` during the stream** used to end the session. The r0.47 run toggled the
   stream window from windowed to fullscreen with `F11` and kept presenting
   (`wayland-video fps` stayed at 48-52, `dropped=0`), so the swap-interval 2
   default from `0014` covers this path as well; it is kept here in case the vendor
   assert comes back from another direction.
3. **Audio plays late** - patch `0016` fixed the sink's device and format, and patch
   `0018` fixes the reason the queue behind it stayed full. The r0.47 session
   measured on a fresh boot does not use that ALSA sink: the launcher hands the app
   `PIPEWIRE_RUNTIME_DIR=/run/user/1000`, so `pw-cat` opens and the PipeWire sink is
   the one that runs. That session dropped 186 of the 200 arriving packets a second
   (`media-ingress` reports `audio=15740` at 80 s, i.e. ~197/s), the jitter buffer
   grew from `target_ms=45 queued=15` to `target_ms=125 queued=26` with
   `underflows=4`, and the log holds 4223 `queue-dropped` events. The arithmetic
   says why: the play queue holds 12 packets of 5 ms, `push_latest` evicts the
   oldest when a stall fills it, and `conceal_before` filled the resulting timestamp
   hole with up to 100 ms of PLC. Writing PLC takes as long as the audio it replaces,
   so the worker consumed 14 packets a second and 14 x 71 ms is one second of audio a
   second - a fixed point in which ~95 per cent of what the board played was
   concealment. Patch `0018` fixes both halves: a full queue now drops the arriving
   packet instead of the oldest, so what the worker pops stays contiguous and the
   queue drains, and the worker marks the decoder when the queue reports a drop so
   the hole that drop opens is played through instead of concealed. Measured on r0.50,
   in a session three hours later in the same boot that *did* decode: `media-ingress`
   ends at `elapsed_ms=130187 video=7372 audio=25187`, so 193.5 audio packets a second
   arrived and the log holds 651 stale packets reported over 35 drop events - each
   audio drop is reported twice (`media=audio` and `media=linux-audio` at the same
   instant), so ~2.5 a second were lost against 186 a second on r0.47. The transport
   jitter buffer now reads `target_ms=85 queued=0` with `underflows=2` against
   `target_ms` climbing 45→125 and `queued=26` before, and the log holds 60
   `opennow-audio-gap-skip` lines totalling 2.94 s of holes played through, the
   largest 445 ms. The stream itself presented at 54 fps with `dropped=0`, and the
   game's audio was heard to be correct.
4. **The interface hide and the exit export are verified in a real session.**
   Patch `0020` wrote `opennow-diagnostics-<ms>.txt` (245 KiB) 2 s after the interface
   of the r0.50 session closed, and `/usr/bin/opennow-core` with a closed stdin prints
   `opennow-diagnostics-export: <path>` after 439 ms, inside the 1.5 s the Qt side
   waits before it escalates; the five newest exports are kept. Patch `0019` logged
   `opennow-interface-hidden: the stream window has a frame` with the first presented
   frame, `opennow-present fps=0.0 render=0.0 swapped=0` for the whole session, and
   the interface's render thread advanced 0 CPU ticks over the 122 s sampled window
   while the stream window kept presenting at 54 fps. What has not been seen yet is
   `opennow-interface-shown`, the restore when the stream window closes: the session
   was ended by killing the interface, so no close event went through the surface.

5. **The stream is not as fast as the video decoder.** Measured on r0.51 with `0021`'s
   counters, the present path is cheap: over a 5 s window the Rust handoff sums to 16.8-21.4 ms
   across 566-589 pulls (29-38 us each, max 0.18-3.8 ms on one) and the compositor commit sums
   to 55-82 ms across the ~289 commits of the same window (190-284 us each, max 0.5-6.2 ms). The
   two halves together use a quarter of a millisecond of the 16.7 ms a 60 Hz frame has, so
   neither of them can pay the 77-130 ms `max-gap-ms` a window showed.
   What the run lost is between the notification and the drain: `native avg 59.92/s` against
   `delivered avg 56.21/s` while the surface committed at ~57.8/s and ingress held ~60 video
   packets a second, with `stale=0` throughout. Still open, and the claim left on the
   table is that a session the keyboard and mouse drive dips while one they cannot drive looks
   perfectly smooth. The logs already answer that for the density they contain: in `c53.log` the
   23 seconds that carried 70 input events (up to 16 a second) averaged 55.03 fps against 55.54
   fps in the 571 input-free seconds, and the eight worst seconds of each of `c50.log` (its worst
   is 11.4 fps), `c53.log` (40.9 fps) and `s3.log` (11.9 fps) saw zero input events, so the dips
   are not the input's and a few dozen events a second cost at most half a frame a second. What
   has not been measured is a real mouse at its polling rate, where the code says the cost would
   scale with the events: `send_captured_input` is `bundle.queue_input(bytes)`, one SCTP message
   to the host per event and `captured_input_packet` allocates for each, `EmbeddedInputCapture::submit`
   takes the gamepad mutex and the queue lock per event, and the session worker that drains up to
   32 input samples per 250 us pass is the same thread that forwards the video's media feedback,
   so a flooded input stream delays the frame progress the host needs to keep the picture coming.
   That run has not produced a number yet: the attempts at 18:07, 18:13 and 18:25 reached
   `opennow-autolaunch: waiting 1000ms of 90000ms` and logged nothing for the 12 1/2 min each was
   waited, because the grid's discovery endpoint was unreachable (`provider: DIG discovery endpoint
   is unreachable; using fallback https://latam-west.dig.geforcenow.nvidiagrid.net/`) and `wget` to
   that fallback returns a bad response while `https://example.com` answers 200, so the board's
   network is fine and the endpoint is not.
   The clock's own numbers are on the table now too, and patch `0022` (built as r0.52) answers the question it asks: measured on r0.52 in `ab68-2.log`, the clock runs at 107-114 pulls a second against the 125/s its 8 ms timer asks for, each 5 s window holds `gaps16+=5-10` with `max-gap-ms=141-175`, and `after-presented=2-8` of them dominate while `after-stale=0` and `after-noframe=1-3`. The starvation waits on the presentation and on the compositor's reply to the commit, not on the thread that owns the clock and not on either half of the handoff, whose together cost 0.4-0.5 ms of the 16.7 ms a 60 Hz frame has.
6. **The picture reads dark next to the TV's own copy of the game.** Measured, and it is not the
   board darkening it: with `OPENNOW_FRAME_DUMP` the decoded 1920x1080 frame's PPM spans bytes
   0..255 with luma percentiles `min=0 p1=0 p10=1 p50=27 p90=89 p99=126 max=235 mean=37.2`, 53.2
   per cent of samples below luma 32 and 0.0 per cent above 223, so the frame is already dark
   before Qt, weston or the TV touch it. weston 13.0.1 (`backend=drm`) applies plain Rec.709
   offset-range coefficients - its `gl-renderer.so` carries the 1.164 (= 255/219) and 1.596
   constants and has zero hits for `pow`, `mix` or `2.2` - so there is no tone map on the board to
   tune, and the surface sends NV12 through legacy `zwp_linux_buffer_params_v1_add` with no
   pixfmt/modifier/transfer arguments, which is all that call carries: libweston-13 has no
   `wl_surface_set_colorspace` and no `trc` to set one. HDR cannot be raised either: negotiation
   reports `nvst-color color=8bit_420 hdr=false`, `settings.json` reads `"colorQuality":"8bit_420",
   "enableHdr": false`, and the HDR path is gated on `HdrOutput.supported` plus a ten-bit
   membership tier the compositor cannot offer. What is left is the TV receiving an SDR signal and
   applying its own HDR/auto picture processing to it; the only lever left on this side is the
   device-side post-process `videoShader` block, which is `enabled: false` with `brightness: 100`
   and `contrast: 100`.

7. **The launch stall is the app's own `launchInConsoleMode`, not a layer's setting.** Every attempt on Oct 31 logged `shell-mode startup console=true`, printed exactly one `opennow-autolaunch: waiting 1000ms of 90000ms` countdown line and then nothing for the 12 1/2 min waited, in a 385-byte log. `Main.qml`'s `initializeStartupMode()` sets `startupConsoleRequested` from `ShellStore.settings.launchInConsoleMode` once at settings-load time, and the auto-launch countdown is a `Timer` in `DesktopHomeScreen.qml` that runs only while `AppController.route === "home"` - the console shell has no home route, so the countdown prints once and Start is never pressed. No layer ships a settings seed (`git grep launchInConsoleMode` and `git ls-files` for `settings.json` both come back empty), so this is the app's own runtime config on the board: `/home/weston/.config/OpenNOW/settings.json` said `launchInConsoleMode: true` since Oct 29 18:53, while the runs that played 577 s (`c50.log`, `c53.log`) had it `false`. Flipping that file on the board produced `startup console=false` and a full countdown with Start 85 s in; the previous file is kept as `/home/weston/.config/OpenNOW/settings.json.console-true.bak`. `--desktop` is not a substitute: it leaves `startupConsoleRequested` true while asking for the desktop surface, and those attempts started the countdown, reached the first presented frame and died there with the vendor `buffer_fenced_release` assert. This is not restored by a flash: a new boot starts from whatever the app's default is, so which default that is decides whether the countdown runs at all.
8. **The V4L2/hantro capture pool is starved for the rest of the boot, and only a reboot clears it.** From kernel time 60787 s (about 00:07 board time on Oct 31) dmesg carries 15 copies of `hantro-vpu 480d0000.vdec: dma alloc of size 3657728 failed` together with `cma: cma_alloc: reserved: alloc failed, req-size: 893 pages, ret: -12`, and every session after that instant prints `[AVHWFramesContext] Failed to create buffer of type 9: Cannot allocate memory (12)` and `[h264] Failed setup for format drm_prime: hwaccel initialisation returned error.`, so the decoder gets no capture buffer, `no frame!` follows, and the run ends with zero presented frames. The Oct-30 sessions that played for 577 s carry zero of either line. `free -m` reports 845 MB used while every live process sums to ~200 MB of RSS: the difference is DMA-BUF capture buffers that crashed sessions took and never released, so the leak and the starvation are one state and it is cumulative across the runs that crash. Dropping the page cache as root (`echo 3 > /proc/sys/vm/drop_caches`, 405 MB -> 917 MB free) does not help: `Node 0 DMA free:135116kB` with a largest free run of 2048 kB and `free_cma:21784kB` against a request for one contiguous 3657728-byte block, so what fails is fragmentation, not capacity. Nothing runtime reclaims it - the devpower knobs (`/sys/devices/genpd:0:48280000.gpu/power/{control,autosuspend_delay_ms}`) report `control=auto` and `runtime_status=suspended` and change nothing - By Oct 31 20:4x the boot had cratered further: the r0.54 attempts at 20:48 and 20:53 log `opennow-fd-limit: the file descriptor limit was raised from 1024 to 65536` and then die at decode with `Failed to create buffer of type 9: Cannot allocate memory (12)` even at the 1280x720 size the r0.53 sessions of 05:1x and 06:1x got through with. The runs explain each other: a session that dies at the 1024-descriptor limit dies after ~900 presented frames, and under 0023 every cached present left one dma-buf object unreleased too, so each crashed run added ~900 capture objects (about 1.2 GB at 720p) to the same pool that answers the next session's first allocation. The descriptor leak and the capture-pool starvation are one cumulative state, and 0024 moving the descriptor ceiling does not change that: at 65536 descriptors the session would instead run out of capture memory mid-stream. A 5-10 minute hold therefore still needs the release ownership sorted out - the surface should let go of the cached dma-bufs it no longer shows (its eviction already destroys the `wl_buffer`) - Patches `0025` and `0026` (built as r0.55) are the release side of the fix: the streamer no longer treats a dead descriptor as the device's verdict, so the surface can close every frame no request carried again and the objects are released instead of accumulating. That stops new damage but cannot reclaim what a boot's crashed runs already left in the pool. The first hardware-decode measurement of the fix is the r0.56 run above (Nov 1, 535 s at 1080p): the descriptor count plateaued at 97-100 and dmesg stayed at zero allocation failures and zero GPU hangs for the whole session. The census of where the accumulation actually lives came with `r0.72` (Nov 5): the seat's own processes answer it, with the compositor at `pid=2698 comm=weston fds=252 dmabuf=217` against 11 fds and 3 `/dmabuf:` for each seat helper, so about 24 capture objects a session of our surface's `wl_buffer` cache are pinned for the whole boot - 3.5 MB each, and this boot down to `free_cma:10206` against a request for one contiguous 3657728-byte block. Restarting the seat's own session with consent (`systemctl restart weston-graphical-session.service`) closes them with the compositor: the new one reads `pid=350600 comm=weston fds=38 dmabuf=3` and `free -m` goes from 560 MB free to 1337 MB (`used` from 1055 MB to 253 MB). The launches after that answer `Failed to create buffer of type 9` no more, so the accumulated half is reclaimed without a reboot; the cratered half is not, and eleven launches in a row then stop at 480 or 74 bytes with `opennow-qt` in `wchan=do_sys_poll` while `dmesg` collects one `GPU[0] core0 hang, automatic recovery.` each, 32 of them. The reboot that follows (Nov 5, the seat's owner's own) closes the cratered half and the fresh boot measures it: `dmesg` carries two `GPU[0] core0 hang, automatic recovery.` for a whole hour-long run against the cratered boot's 31, and the retry loop reaches the stream window on its third attempt after that boot's eleven launches in a row stopped at 480 or 74 bytes. What the reboot does is restart the driver's own state, which nothing runtime reaches - the accumulated half is the compositor's and the session restart already reclaims that.

9. **These Oct-31 measurements ran at 1280x720 and the 1080p setting has to come back.** Every attempt on this boot died at decode with `Failed to create buffer of type 9: Cannot allocate memory (12)` because hantro refuses a contiguous 3657728-byte capture block while the boot's largest free DMA run is 2048 kB (open item 8), so `settings.json`'s `resolution` - the app's own `Exact stream size` setting, SettingsScreen.qml - was flipped from `1920x1080` to `1280x720` at 05:11: the same capture allocation is 1382400 B = a 512-page request, which fits. It is the same kind of runtime config on the board as the `launchInConsoleMode` flip of open item 7, not a layer change; the previous file is kept at `/home/weston/.config/OpenNOW/settings.json.resolution-1080.bak` and the restore after the measurement is `sed -i 's/"resolution": "1280x720"/"resolution": "1920x1080"/` against the live file (or copy the backup back). Everything on the table for r0.53/r0.54 below is at 1280x720: cheaper CPU copy, smaller hantro allocation, and if it survives at 720p it still has to be re-measured at 1080p on a boot whose capture pool is not starved. The setting is back at `1920x1080` and the r0.56 measurement above is that 1080p re-measurement: 535 s, p50 55.9 fps, no decode failures.
10. **ESC against a live stream window and the 5-10 minute held stream are measured.** The press that closes this item is in `ab77-1-2.log`: 150 s into a driven 1080p window presenting at ~56 fps, `ydotool key 1:1 1:0` logged `wayland-video input: key 1 vk=0x1b mods=0x0 pressed=1` and then `pressed=0` (`key1-events=2 vk0x1b=2 pressed=1 released=1`), and the window kept presenting afterwards, so the key reached the game instead of being consumed by the app. The same run is the held stream: 535 s of picture, `committed=31019`, `dropped=0`, p50 55.9 fps, and `/proc/PID/fd` flat at 97-100 for the last 30k presents (the `ab71-1-1` press found the app already dead at `key1-events=0 vk0x1b=0`, and the r0.53 sessions never held past 17 s of picture because of the 1024-descriptor limit `0024` moved). What is still owed is the close side: ESC opens the game's menu rather than closing the stream window, so `opennow-interface-shown` still has to be produced by a session that ends through the window itself.

11. **The interface dies by signal, not by exit, and there are two crash
    classes.** `coredumpctl list` for the Nov-1 boot is unambiguous: seven
    runs, seven crashes, zero clean exits - six `SIGABRT` and one `SIGSEGV`,
    all of them `/usr/bin/opennow-qt`. What looks like "the app just closed"
    is a process that died in the middle of the seat's own teardown.

    (a) **`SIGABRT` in the vendor EGL-Wayland client.** Every one of those
    logs ends with the same line:

    ```
    opennow-qt: api/gc_egl_wayland.c:2699: buffer_fenced_release:
      Assertion `buffer->release_fence_fd == -1' failed.
    ```

    The interface window is the only window that reaches EGL - the stream
    window goes through this series' own `wl_surface` plus `linux_dmabuf`
    and never touches it - so the crash is never in the video path. The
    vendor client asserts when it recycles a swapchain image whose previous
    release fence was never answered, and patch `0019`'s `rootWindow->hide()`
    is what leaves fences unanswered: hiding destroys the interface
    window's `wl_surface` and its `wl_egl_window`, so weston never sends the
    release for the buffers that were still in flight, and the swapchain
    keeps images marked as pending. The poison then surfaces the next time
    that EGL client runs, which is why the crash reads as a session end:
    `ab81` asserted 33 ms and `ab82` 35 ms after `opennow-interface-shown`,
    while `ab80` and `ab83` asserted about a session-end later, during the
    seat's artwork reload (`Corrupt JPEG data: premature end of data
    segment` and `QFile::at: Cannot set file position 180224` in both).
    Every crashed run logged `opennow-interface-hidden`; the 535 s decisive
    run that never left the streaming state had 0 asserts.

    (b) **weston's protocol error, then `SIGSEGV`.** The run's own chain, in
    `play-ab84.log`:

    ```
    09:03:26  wayland-video: dropping a frame (the frame could not be
                exported as a DMA-BUF)                                x33
    09:03:28  [destroyed object]: error 6: invalid offset 0 for plane 0
    09:03:28  wayland-video: the compositor connection failed: Protocol error
    09:03:28  opennow-interface-shown: the stream window is gone
    09:03:33  SIGSEGV
    ```

    `error 6: invalid offset 0 for plane 0` is weston's own validation of a
    `zwp_linux_buffer_params_v1_add`: it `fstat`s the plane's fd and rejects
    a plane whose `offset + size` does not fit what it sees, so the fd that
    request carried had already been closed when weston read it. That is the
    same fd-number reuse `0011` wrote about - the decoder opens a fresh dma-buf
    descriptor for every frame it exports, and this board's capture pool
    churns the numbers at frame rate - and `0026` closing what no request
    carried is what makes the window for it. `ab84` got there 370 s into play,
    at `replay=164` and `nackRecovered=499`; `ab81` and `ab82` within 9-17 s
    of the first frame, with `exportFail=59` and `exportFail=12`.

    The two classes feed each other through the DMA pool. `CmaFree` read while
    nothing was running went 287 MB (08:26) to 232 MB (08:29) to 126 MB
    (09:08), and dmesg carries `cma_alloc: reserved: alloc failed, req-size:
    893 pages, ret: -12` at 08:57:20 and 09:03:18 - 893 x 4096 is 3.5 MB, one
    1080p NV12 set, which is what the surface asks for per buffer. So every
    crash costs the pool about 55 MB, and after a few of them the next
    session fails at (b) rather than at (a). Only a reboot returns the 512 MB
    pool.

    What a fix has to do about each: (a) keep the interface window's EGL
    swapchain whole across the hide, or drop the hide - `0014`'s swap-interval
    2 alone already keeps that window safe, and the same series measured the
    stream at 58.6-60 fps with the interface painting; (b) make the surface
    refuse an `add()` whose plane does not fit the fd it just `fstat`ed, which
    is the check `bufferFor` already makes for the descriptor being alive and
    has to make for the size as well.

Caveats on the numbers above: single runs per phase, not an average of many
sessions; `presents/s` is only available for sessions launched with
`OPENNOW_LOG_PRESENT=1` and captured to a file; the board has no swap device and
only ~432 MB free on the rootfs, so headroom, not spill, is the limit.

One more caveat, and it is the reason the r0.47 numbers are not a full table yet,
and the reason the r0.48 session was never heard: a session that has been started and
crashed several times in one boot can leave the
V4L2 capture pool and the DMA-BUF allocator holding what the crashed sessions took,
and the next session then fails at *decode* rather than at present:

```
[AVHWFramesContext @ …] Failed to query buffer 0 of type 9: Bad file descriptor (9)
[h264 @ …] Failed setup for format drm_prime: hwaccel initialisation returned error.
[V4L2RequestContext @ …] Failed to allocate request for OUTPUT buffer 0: Inappropriate ioctl for device (25)
```

which is what `opennow-linux-video` panics over (`unexpected error during closedir:
Bad file descriptor`) and why no frame reaches the surface. That state is not
permanent: the r0.48 session at 08:07 failed to decode, while the r0.50 session at
11:04, three hours later in the same boot, decoded and presented at 54 fps with 0
decode failures and 0 dropped frames. What a reboot is still needed for is the
ethernet PHY the board leaves un-armed, not this decoder state, and it is why a
full r0.47 measurement - present rate, `queue-dropped`, the memory tally and the
thread CPU - had to wait for one session. The r0.56 boot repeats the pattern on a fresh boot: its first launch died at the first keyframe with `[V4L2RequestContext] Failed to allocate request for OUTPUT buffer 0: Inappropriate ioctl for device (25)`, then `Failed setup for format drm_prime`, a `closedir` panic and the vendor `buffer_fenced_release` assert, and the retry 85 s later streamed 535 s at p50 55.9 fps with no decode error at all. The retry in `measure73.sh` is therefore load-bearing, and a first attempt that dies this way says nothing about the build.

    The Nov-1 boot answers the pair, and `r0.57` is the release that carries it:
    `0028` refuses a dma-buf whose planes do not fit the descriptor weston just
    `fstat`-ed (the `(b)` family, `[destroyed object]: error 6/7` → `the
    compositor connection failed: Protocol error` → SIGSEGV) without closing the
    descriptor a refusal must keep, and `0029` reverts `0019`'s `hide()` - Qt
    6.8's `setVisible(false)` already reaches `reset()` → `invalidateSurface()`,
    so the hide only added the unanswered fences the vendor `(a)` assert then
    trips. The driven `r0.57` session carried neither class for ~9 minutes
    (crash-class counters 0, `dropped=0`, p50 43.6 fps), and the first manual
    session on a fresh boot with a whole pool ended at the provider's own
    verdict and survived it: `server-terminated (0x80030007)` after 805 s of
    picture with 47803 video and 160359 audio packets in and `dropped=5`, and
    0 of either class in the run.

12. **The `QFile` warning and the corrupt JPEG are Qt's own JPEG plugin asking a
    `QFile` for one block more than a whole file has, and the class is cosmetic.**
    The terminal of this boot's runs carries a warning none of the diagnostics logs
    has, because Qt writes it to stderr:

    ```
    qt.gui.imageio.jpeg: Corrupt JPEG data: premature end of data segment
    QFile::at: Cannot set file position 180224
    QFile::at: Cannot set file position 147456
    ```

    `c53` logged seven pairs inside one second (17:28:00.150..17:28:01.094) while the
    stream around it presented at 53.0 fps, and the `r0.58` run logged two pairs at
    20:57:17.379 and 20:57:19.151 in the middle of a session that streamed 664 s at
    p50 37.7 fps with `flushFailed=0` in all 647 present samples. The positions are
    block boundaries of Qt's own plugin, not of the cache: `180224 = 44 x 4096` and
    `147456 = 36 x 4096`, and `src/plugins/imageformats/jpeg/qjpeghandler.cpp` reads
    the device in `max_buf = 4096` blocks. The object neighbourhood they sit in is
    whole - 145090 and 175462 bytes, both baseline JPEGs (SOS `FF C0`) ending `FF D9` -
    and all 167 `index.json` entries matched their file size on disk, so there is no
    short object and no index/disk disagreement to find. What happens instead: libjpeg's
    entropy stream reaches EOF before its own EOI (that is the `premature end of data
    segment` notice), the source manager's `resync_to_restart = jpeg_resync_to_restart`
    asks the device for the next restart marker, the plugin's next block-boundary seek
    lands past the end of the file, `QFile` refuses it and prints the warning, and
    `qt_fill_input_buffer` installs its fake EOI (`qjpeghandler.cpp` lines 101-105) so
    the decode finishes. The class is the decoder on a good file, and it costs at worst
    the last scanline groups of one poster. No session this boot died from it, and the
    two sessions that showed it ended at 0 of every crash class.

    `0030` was landed against the hypothesis that the cache had stored a short body, and
    the `r0.58` run measures that hypothesis as false: `truncated-image` 0 across a
    session that did carry the warnings. The guard stays, because the gap it closes is
    real - `download()` in `native/opennow-core/src/artwork_cache.rs` had no
    completeness check at all, rejecting only an object *over* `max_object_bytes` -
    so it is store-door hygiene with a measured 0 hits, not what answers those two
    lines. An availability check is not the answer either: the file that is not there
    prints `Cannot open: <url>`, the app already routes that through the empty
    `resolvedUrl` in `ArtworkSource.qml` (and the acceptance harness whitelists that
    message form beside `ArtworkEvictionUrls`), and this boot logged 0 of them. The
    change that would silence both lines is on the decode path, not on availability:
    `RoundedArtwork.qml` hands Qt a `source: artworkSource.resolvedUrl`, a `file://`
    device, and the plugin's device branch is the one that fails; handing the decoder
    the cache bytes instead (a `QBuffer`, which takes the plugin's `memDevice` branch at
    lines 92-95, where no `QFile::at` exists) leaves nothing to fail. Not landed - the
    class costs nothing measurable, and the one release it would need has to be
    measured against the p50 the interface pays today. What is still owed elsewhere:
    `setWindowHasContents(Never)` while the stream window is mapped, and refusing
    `::close` of the number `wl_display_get_fd(m_display)` reports.

13. **The seat keyboard does not survive a device leaving it, so a press the
    player makes can reach nothing, and nothing restores it on its own.** The
    stream window asks the seat for the keyboard and answers the presses it gets
    while weston gives that window keyboard focus, and weston focuses one window
    at a time - the interface behind it never has the keyboard. The churn was
    measured on the `r0.59` board: at 07:39:46.984 weston logged
    `event1 - Dell Dell USB Keyboard: device removed`, and 30 ms later the surface
    logged `wayland-video input: capture inactive`. In weston 13.0.1's own source
    that pair is `weston_seat_release_keyboard()` answering a device leaving the
    seat with `weston_keyboard_set_focus(keyboard, NULL)` and `cancel_grab`, and
    the only path that hands the focus back is `weston_view_activate_input()`,
    which does it when a pointer motion activates the window's surface again. So
    a press made at the board after such a churn lands on nothing until the
    pointer moves, and the cloud game's own pop-up - Steam's notice, which only an
    Enter dismisses and which appears some way into the cloud boot rather than at
    the start of the stream - gets no press at all when the session started with
    the board empty. What answers that pop-up is not the seat: `r0.60`'s `0032`
    sends the Enter through the streamer's own key hooks the surface already
    holds, which is the press `submitKey` makes without the compositor in the way.
    The other half was measured from the same board before the patch: ydotool's
    evdev 28 while the focus was alive logged 20 forwards of
    `wayland-video input: key 28 vk=0xd mods=0x0 pressed=1` 14 s apart beside a
    picture that held `fps=48.7 committed=13125 dropped=0`, so the key that is
    sent does reach the host, and what loses it is the focus, not the wire. Still
    open: why the seat's keyboard device left the seat at all - udev had tagged it
    and the board kept the keyboard for everything that came after the window -
    and that the window's own keyboard capture goes inactive across it, which
    makes a player's keys the fragile side rather than the app's answer.

14. **The host's cursor is visible in the video and does not follow the mouse, and
    a click over the stream changes nothing** - the player reported both halves on the
    `r0.61` and `r0.62` builds. `r0.62` answers the app's half: the window forwarded
    10 button transitions and 9 pointer positions in one second of play with 0
    `submit refused`, so the events leave the app and reach the streamer. What is not
    answered is where the host's cursor was when they arrived. The host keeps its own
    cursor state in the control channel - `NVST cursor wire rx: ... cursorId=Some(1)
    position=Some((32784, 32797)) visible=Some(false)` in the native streamer's log,
    which is centre-screen and not moving while the mouse does - and the surface hides
    weston's cursor over the stream window (`wl_pointer_set_cursor(pointer, serial,
    nullptr, 0, 0)`), so nothing on the board says where the player is pointing. `0035`
    of `r0.63` is what turns the picture into a file the next reader can compare with
    those numbers; still owed are the surface's own cursor image for the case where
    the host reports its cursor invisible, and reading the host's cursor position back
    into the log.

15. **The stream window's own presses are dropped by the queue's gate, and the
    app's side never says it happened** - measured on `r0.66` in `play-d2b-2.log`.
    `EmbeddedInputCapture::submit` refuses every event while its owner is inactive
    (`embedded_input.rs:139`), and its status is `OPENNOW_STREAMER_OK` either way,
    so a press the app made is silent on the app's side and only the new
    `NVST embedded input tally:` line says 48 key transitions were dropped. The
    app armed capture at the window's creation (`wayland-video input: streamer
    capture requested=1 inputAllowed=1` at 01:16:07.355 beside `capture active`)
    and never logged another capture call, so the flip came from the app's own
    Qt video item: `StreamVideoItem::syncCaptureState` calls
    `s_nativeRuntime->setCaptureActive(desired, …)` with `desired` false for an
    item that has no active Qt window focus, bypassing the logging wrapper in
    `setVideoInputCapture` entirely, and `WaylandVideoSurface::setCaptureActive`
    will not re-arm while its own `m_captureActive` still says active. Still owed:
    the gate's own log line on the streamer's side, so the next reader does not
    have to read the code to find the caller, and the app-side answer that keeps
    the owner armed while the stream window is the front window - without it the
    game never sees the Enter that answers the cloud game's opening notice, which
    is what the `0032`/`0033` clock and a player's own keyboard both depend on.
    `0040` of `r0.67` answers both halves of it: the streamer prints the gate's
    own state changes (the `NVST embedded capture gate:` line names the state,
    the arguments the caller passed, and the queue by the same address the
    tally and the drain print), and the window re-applies the state it asked
    for before every key it submits and once a second while it is live,
    without overriding a keyboard that left the window. What remains is the
    measurement: the re-armed presses have to be seen reaching the wire
    (`NVST native input rx: events=[key...]` and `NVST encoded input tx`),
    and the verdict on the pop-up has to be read off the `OPENNOW_FRAME_DUMP`
    PPMs, because the delivered rate cannot tell Steam's window from the
    Main Menu and the `0035` screenshot clock never saved a file on this
    board. The first `r0.67` attempt never streamed - it froze in the Vivante
    driver two minutes and a half in with `galcore` core hangs beside it -
    the second attempt measured it end to end: 48 key transitions pushed with
    0 dropped, 48 forwarded by the drain, 24 Enter presses on the HID wire,
    and the pop-up gone in the witness frames. What is still owed is the game:
    the answered presses never launched anything (open item 16 below).

16. **The pop-up is answered and the presses reach the host, but the cloud
    game is never launched** - measured on the second `r0.67` attempt
    (`play-d2c-4.log`): the gate, the wire and the witness all say the window's
    24 Enters went through and the pop-up is gone, yet Steam sat the whole
    session on the store's Featured & Recommended carousel, slide 6 of 10,
    cursor at rest, until the host's ~14 min idle timeout ended the session
    through `0031`'s close. The host accepts the presses but nothing navigates:
    either Steam waits for a click on a launch button (the `r0.62`
    pointer-with-position path is armed and a click with a known rest position
    is available) or the app-id opens the store page rather than the game
    itself. The time-lapse did tell them apart, and neither candidate is
    right: ONE Enter armed after the consent notice answers it and the game
    launches (the stills read the consent window with its highlighted "ok",
    then the Destiny 2 splash waiting "PRESS ENTER TO PLAY" about 135 s
    later), while extra presses of the same clock close the host's own
    windows and leave a bare desktop. What remains owed is a clock that can
    hold that shape - one press per notice, notices ~135 s apart, which the
    5 s follow-ups of the `0032`/`0033` clock cannot do; `0041` of `r0.68`
    arms the PLAY press on a clock of its own, and the first `r0.68` attempt
    that streamed measured it: the consent answer at +64 s and the PLAY answer
    at +213 s both crossed the gate (`drain … keys=4`) and rode the wire (2
    `key-down(3)` and 2 `key-up(4)`), and the stills 25 s later read the splash
    answered. The chain is deeper than it was thought: the notice the PLAY press
    opens is **SIGN UP / JOIN THE FIGHT // EARN EXOTIC GEAR** with two options
    bound to keys - `⏎ Sign Me Up` or `Esc Not Now` - and it waited there
    unpressed to the host's ~14 min session end. What is owed is a third clock
    of the same shape, armed farther still, answering the signup notice with the
    key it names rather than only with an Enter; the Main Menu itself is still
    unwitnessed. `0042` of `r0.69` armed that third press and the attempt that
    streamed measured what it did, and the launch half of this item is now
    closed: two presses are the whole budget. The PLAY press lands the game
    into a loading screen and the character screen renders 40 s after it - the
    guardian standing with a class card, the corner's only key hint reading
    `Esc Exit to Desktop` - which is the Main Menu this item asked for. The
    third press is what broke in: the Esc reached that corner hint, the next
    still reads the quit confirmation, and it waited there to the session's
    end. What the chain still owes is not more presses but the rate (open item
    17) and, only if a player does press the corner's Esc, one more Esc to
    answer that notice's "Cancel".

17. **The window presents at 0.6/s while the stream is delivered at 59-60/s** -
    measured across the `r0.67`, `r0.68` and `r0.69` sessions: `wayland-video
    fps=0.6` with a histogram of 0.4 to 0.7/s for the whole session against
    `opennow-frame-rate native=59.6/s delivered=0.7/s`, and the surface's own
    counters now read `committed=412 dropped=2 flushFailed=116` where the
    `r0.67` attempt had read `dropped=0 flushFailed=0`. The clean `r0.69` run
    splits this into two things, which one session alone could not: the Main
    Menu session presents at 0.5-0.7/s with `committed=448 dropped=0
    flushFailed=0`, so its slow pull neither refuses nor fails a commit and the
    suspect is the window's pull clock being off while everything it does is
    accepted - which reads back through the surface's own `damage`/`wl_surface_commit`
    pairing, the interval it asks the compositor for, and the same starved
    capture pool as open item 8; and separately, the 116 failed flushes belong
    to a session where a player's own mouse went through the queue
    (`moves=539 absolutes=730 buttons=18` there against `keys=4 absolutes=1`
    in the clean one), which is a pointer-traffic symptom of its own and a
    second thing to read. Both matter to the seat: at 0.6/s the picture and
    the sound reach it but advance once every 1-2 s, so the cloud game cannot
    be played even once its Main Menu renders. That reading is a degraded-session
    symptom rather than an inherent rate: the seat's own sessions present at 42-55/s
    (`wayland-video fps=42.7-55.0` with `flushFailed=0` across `play-manual-21/22/23`),
    and the 0.4-0.7/s histogram belongs to sessions whose GPU had cratered, the same
    condition as the `galcore` `GPU[0] core0 hang` launches of open item 8. A session
    that presents at 50/s and then dies minutes in is a fault, not this item.

18. **The `r0.70` band leaves one descriptor open per refused present, and a playing session refused 1200 of 65,936** - measured on the seat's own session as it ran (`play-manual-23.log`: 19:06 read `committed=16483 dropped=282`, 19:23 read `fps=51.1 committed=65936 dropped=1200 flushFailed=0`) against `dropped=0` on every run that played before the band. The refusals and the descriptor growth are the same count, and the process's own table says so: watched over that session it went from `live=103 highest=4113` at 8 minutes to `live=1303 highest=5312` at 17 minutes, about one number a second, while the app had raised its ceiling to 65536 (`opennow-fd-limit: the file descriptor limit was raised from 1024 to 65536`, open item 9). The reason sits in the refusal's return value: `bufferFor()` sets `*queued = true` when it refuses a frame whose planes do not fit, `attachFrame` returns that flag, and `present()` closes a descriptor only when the frame was NOT handed over - so a refused frame's duplicate stays open for the rest of the session. Below the band that caution was right (the number might already be the decoder's or the compositor connection's, which is why `0023` and `0026` made the close so careful); above it the number is always this crate's own `F_DUPFD_CLOEXEC` duplicate, so the close is simply ours to make. Two things are owed there: the close of a refused band duplicate, and a `dropped` counter split into the refusal that means the planes are not there - the capture pool retiring a frame across `0011`'s one-frame handoff ring, the same starvation as open item 8 - and the one that means the surface had no `wl_buffer` for it, because today one line (`opennow-diag: dmabuf-rejected`) covers both and printed 5 times across those 1200 refusals.

19. **`0044`'s close and `0045`'s keep clock are installed and marked, and neither has been watched on a session that played** - the r0.71 session that presented at 39-51/s for 4.5 minutes measured the counter half (`planes=92 dropped=92 dead=0`, the refusals all answering as the planes-not-there kind with 324 `could not be exported as a DMA-BUF` in the same log) and ended with the family's third reading, and the r0.72 attempts have not reached the stream window at all on this boot (eleven in a row, 480 or 74 bytes, one `GPU[0] core0 hang, automatic recovery.` each). What is owed is one run on a fresh boot to measure both halves: the close keeping the app's own table bounded through an hour (the band's `live=1303 highest=5312` at 17 minutes on the r0.70 session is what it has to beat), and the keep clock's 60 Enters crossing the gate and the drain (`NVST native input drain: ... keys=`) well enough that an unattended seat survives the free tier's hour instead of being ended by the host's ~14 min idle timeout. Both halves are measured on the `r0.72` run of the same day (Nov 5, fresh boot): the stream ran 3,599 s at 42-56/s for 178,233 presents and the whole log carries zero death markers and zero failed flushes, the app's table stayed at `live=98-101 highest=4111-4112` through the hour against the r0.70 session's `live=1303 highest=5312` at 17 minutes, the compositor's stayed at `fds=96-99 dmabuf=56-59` against the cratered boot's `fds=252 dmabuf=217`, and 60 keep answers printed one a minute from 11:01:24 to 12:00:32 with the drain and the embedded tally reading them (`keys=6` at the first, `keys=127 absolutes=774` at the 58th). The item's remaining tail is the refusals, not the death: 3246 of 178,233 presents answered as `planes` (the capture pool retiring the picture across `0011`'s one-frame handoff ring, the same starvation as open item 8), so one present in 55 costs nothing to the seat but still names a ring that is one frame deep while the pool cycles through more.

20. **`0046` is installed, its naming measured, and the r0.73 hour attempt read on the cratered boot** - the line prints once with `Limited`/`Bt709`/`Bt709`/`Sdr`, the frame reads `minL=0` with 9.7% of pixels at true-black-or-below, and the colour work is closed on the app side. The clocks, measured, arm ONLY when their env is set: the gate is `if (armMs <= 0) return;` on `OPENNOW_KEEP_ENTER_MS` (`EVERY_MS`/`TIMES`/`HOLD_MS` default to 60000/60/120), on `OPENNOW_POPUP_ENTER_MS` (`TIMES` default 4) and on `OPENNOW_PLAY_ENTER_MS` (`TIMES` default 1) - which is why the bare probe sessions never armed a clock while every hour launch carries the r0.72 hour's measured values 63000/212000/270000. The r0.73 attempt with those armed all three at its window (00:08:47): popup answered at 00:09:52, PLAY at 00:12:21, and the keep clock ran 60 of 60 presses, the last `keep-alive answered (0 left)` at 01:13:51, with zero death markers and zero failed flushes - the app survived the whole hour of presses even after `opennow-server-end-closes-window: the server ended the session; unmapping the stream window` at 00:34:19, 25 minutes in, with `committed=886 dropped=0 planes=0 dead=0 flushFailed=0` the last wayland-video line and 889 `opennow-pull-gap:` in the log. That 25-minute end is this boot's cratering - ten `GPU[0] core0 hang, automatic recovery.` by then - and not the app's doing: the free tier's whole hour is owed again on a fresh boot, as is a fresh witness for the NV12 plane dump, which lands no `nv12.y`/`nv12.uv` under `OPENNOW_DUMP_NV12` on r0.73 while the PPM dump keeps landing frames.

21. **The slim `r0.74` play set is installed and starts on the board** - 35 patches
    in `SRC_URI`, the 11 retired ones in `files-unused-playset/`, `0040` regenerated
    to carry its own `AtomicUsize` import so `do_patch` is fuzz-free, and the bare
    launch (no clock env) reached `wayland-video: stream window created` on the first
    retry-loop attempt with the stream at 50-55 fps and zero death markers. What this
    set still owes is the hour itself on a fresh boot with a human answering the
    notices, since `0045`'s keep-alive clock is no longer in the build and an
    unattended seat now idles away on its own; and the pre-window instant-death
    launch family stays vendor-side on this boot, so the retry loop is still the
    way in.

22. **The shipped core's RPC is two one-way pipes, and OpenNOWLite's answer to it is measured** - what a
    new reader needs before writing another client: the core reads its requests on one end and answers on
    the other, so a `socketpair` gets `sendmsg ... No such file or directory` and nothing is answered. The
    handshake is `core.hello {"protocolVersion":6,...}`, and after `session.create` the client **must**
    post `{"type":"ack","id":"lite-create"}` or the core sits on its credential work and never continues.
    Replies carry no `type` member, so they are matched on `"ok"` plus the id. `hello`, `game.get`,
    `launch.inspect`, `create` and `prepare` need a 60 s wait because the core's own Electron/credential
    startup and the vendor round trips run before the first reply is read, while `session.poll` keeps the
    shipped 25 s and is re-asked ~2 s apart; `launch.inspect` answers with `scope`/`status` and the vendor
    holds `preparing` (`status=1`) for 100-140 s before `status` is 2 or 3, so those seconds are the vendor's
    and not a client's stall. Closing the request end is what shuts the core down. All of this is in
    `opennow-lite.c`, whose log prints `rpc <method> line=<n>` and `reply for <id> in <n> ms`.
23. **This recipe names `--target aarch64-unknown-linux-gnu` for cargo, and it has to** - the opennow
    recipe's own idiom (no `--target`) is not what this graph needs. The lite build selects no streamer
    backend, so the vendored OpenH264 C++ is compiled from source by `openh264-sys2` through cc-rs, and
    cc-rs takes the architecture from cargo's `TARGET` build-script variable. `rust-1.89.0-host`'s rustc
    prints no `Target:` line in `--version --verbose`, so with no `--target` that variable is the *host*
    triple and cc-rs appends the host bitsize switch to every aarch64 compile:
    `aarch64-ostl-linux-g++.real: error: unrecognized command-line option '-m64'`, which fails the
    `openh264-sys2` build command. The opennow recipe never hits it because `linux-ffmpeg-bundled` takes
    the H.264 codec from the bundled FFmpeg instead of compiling C++. rustc already produced aarch64 code
    through its built-in default target, so naming the triple only makes cargo state it - which also puts
    the artifacts one directory deeper, in `rust-target/aarch64-unknown-linux-gnu/release/`.
24. **The Opus element the audio path needs is `gstreamer1.0-plugins-base`'s own split package for its
    opus feature**, `gstreamer1.0-plugins-base-opus`, with `libopus` as the runtime library that element
    and this recipe's own streamer library link against. Before that split was pulled in, `opusparse`,
    `opusdec` and `opusenc` grepped 0 in `gst-inspect-1.0` and there was no `libgstopus.so` in
    `/usr/lib/gstreamer-1.0/` even though `libopus0` was installed - `layers/meta-watermelon-wine`'s
    bbappend is what turns that feature on, and it stays an OS-level change rather than an app recipe's.
25. **An `appsrc` defaults to `GST_FORMAT_BYTES`, and on this media path that is what makes the audio
    fail** - recorded because the error it produces reads as a video problem: the segment event the appsrc
    posts says "bytes", `GstAudioDecoder` answers `unsupported format; ignoring`, and the appsrc itself
    posts `Internal data stream error` / `streaming stopped, reason error (-5)`. The fix is
    `"format", GST_FORMAT_TIME` in the client's own `media_source_new`, since the PTS on these buffers is
    already nanoseconds. Offline proof on the seat against the real first packet
    (`/tmp/audiotest.py` plus `/tmp/opennowlite-audio1.bin`, 295 bytes): pushed through the default it
    errored, pushed with a time segment it decoded. A `gst-launch`/`filesrc` test is **not** a valid
    stand-in, since it produces a BYTES segment anyway.
26. **The seat's keyboard and pointer are held by the same connection as the window, so the open item 13
    focus problem does not exist on this path** - `opennow-lite.c` puts its `wl_keyboard` and `wl_pointer`
    listeners on the same `wl_display` that owns the window, so weston keeps that window focused and the
    seat's own keys reach the game (the hour round above logged `stopping keys=12` with a human typing at
    the seat during it). What is *not* answered here is whether the seat's keyboard device stays attached to
    the seat at all, which is open item 13's own open half.
27. **A launch that dies leaves `unresolved-allocation.json` behind, and the core refuses every new
    session while that record exists** - the Nov 7 launches left one behind at 12:06, 12:24, 12:49, 14:09,
    14:35, 15:12, 16:17 and 18:24. `/home/weston/lite-pilot.sh` moves it to
    `/home/weston/backup/unresolved-allocation.json.pilot-<TAG>` before every relaunch, where the tag is
    that round's start time, and the copies of the Nov 2 stall and of the Nov 7 launches sit in
    `/home/weston/backup/` where a new reader can read them; nothing runtime reclaims the record itself.
28. **The lite client's own input numbering and absolute pointer are measured, and both were bugs of the
    client's** - Wayland key codes are evdev codes plus 8, the authoritative table is upstream's
    `PhysicalKeyMapData.h` `Layout.virtualKeys[128]` indexed by evdev and the specials come from
    `StreamVideoItemInput.cpp`, so the ad-hoc table this client first had carried no Return at all (Enter
    was silently dropped) and mapped Escape as Tab; buttons are the seat's evdev codes mapped to the
    FFI's `1..=5` (left 272 to 1, middle 274 to 2, right 273 to 3, and sending 0 was answered
    `button submit refused (2)` with `INVALID_CONFIG`); and the pointer is absolute, since a relative
    client measured 100 pointer buttons and 28 keys reaching the FFI with `refused=0` while nothing in
    the stream moved. See the OpenNOWLite section for the same finding in full, including
    `submit_mouse_absolute`'s `width == 0 || height == 0` rejection and the `a->pointer_surface == a->surface`
    gate that logs `pointer outside the window ignored`.
29. **A lite run's log cannot say how many presses rode the wire, and that is a gap in this recipe's own
    patch series** - the FFI answers `OPENNOW_STREAMER_OK` for an event it dropped (the gate refuses while
    its owner is inactive or the NVST feedback `input_available` is false), and the opennow series'
    `NVST embedded input tally:` and `native input drain:` lines belong to *its* patches, not to this
    one's, so nothing like them is compiled into an `opennowlite` build. What a lite log can say is which
    calls were refused, that the input handshake happened (`NVST cursor capture tx: command=0x0308
    enabled=true reason=input-activation` and `NVST remote cursor tracking tx: command=0x030d
    enabled=true`) and that the game answered - which is what the pilot clocks and the witness frames read
    instead. An input tally in this recipe's own series is what to land next.
30. **The pilot clock's own gap was wrong in r1.2 and fixed in r1.3** - the keep events after the notice
    list were armed on a sliding `last + gap` that the main loop re-based, so a clock armed to press once
    a minute fired at +282 s, +330 s, +390 s, +450 s and +510 s instead of 60 s apart, and a clock whose
    events crowd one another is what closes the host's own windows. r1.3 arms every event at its own
    `due`, and the round stopped from pids 267195/267200/267245 shows the presses at +630 s, +690 s,
    +750 s ... +1050 s, exactly 60 s apart (`stopping keys=0 motions=0 submits=34 refused=0 skipped=0
    pilot=16`).
31. **Keys after the notice list are what quit the game, and r1.4's pointer jiggles remove that quit** - the
    Escape armed at +270 s in the r1.3 round reached the `Esc Exit to Desktop` hint that Destiny 2's own
    character screen carries, which opened `ARE YOU SURE? ... quit your current session and exit to
    desktop`, and the next Enter of the same clock at +330 s answered that confirm: the session went back
    to the Steam desktop and the vendor sent no media at all after `NVST rx-stats elapsed=320.1s ...
    frames=18298`, while `session.poll` kept answering `ok` and the client kept taking input, so the window
    held one frame for the next ten minutes. That is the "extremely slow" the seat was read as, and the
    pointer jiggle that replaced the keep-keys in `r1.4` is what keeps the quit from happening: the r1.4
    round's own witness reads the character screen with no confirmation in it and the guardian's idle
    pose moving between frames. What the jiggle did **not** do is hold the hour unattended - that round's
    media stopped on its own at 1720 s with the session alive (88 pointer positions sent against the
    attended hour round's 1734), which is the item this one leaves open.
32. **The lite window's own presentation rate is measured at 59.33 fps, so open item 17's 0.4-0.7/s reading
    is a degraded opennow session and not an inherent rate of this board's compositor** - the same 1801
    frames of the real 60 fps stream through `opennow-lite`'s own window (its display context, its window
    handle, its render rectangle, `sync=true` so the wallclock is the rate the window showed) ran in 30355
    ms, and through `gst-launch-1.0`'s own top-level `waylandsink` in 30244 ms, so the client's hand-off
    costs 0.2 fps of its own window. The benches that read slow are other shapes: the stateless decoder
    into `fakesink` with nothing holding its output ran 47.33 s for the same 1801 frames (38.05 fps) because
    it recycles its own buffers, the same chain with a `queue` in front of the sink ran 17.28 s (104 fps)
    and `avdec_h264` costs about the same (48.07 s), and a `--test-pattern --pipeline` demo of
    `videotestsrc` is limited by that element's own 1080p drawing, which is why the clock demos did not
    finish in 600 s. None of them is the game path. `fpswin.sh`, `fpsbench.sh`, `fpsbench2.sh` and
    `fpscmp.sh` (seat copies under `/tmp/`, logs under `/home/weston/.runs/lite-fps*.out`) are the
    reproduction, and the clip they measure is `/tmp/clip-h264.h264`, md5 `1f3f5dbdc13f320f42132835c0b2f403`.

33. **A launch that waits for the vendor queue is cut by the poll budget at its own boundary, and r1.5 raises that budget** - the r1.4 round armed 60 polls 2 s apart, and the round that needed them did not get them: the poll replies grow as the vendor's queue opens (`reply=1846 ... rtsps=2` on poll 18 against `reply=2315 ... rtsps=117` on poll 36 of the r1.6 round), that queue was still `phase=preparing` at attempt 36 and came back `phase=ready` at attempt 37, and a round that runs out of polls mid-transition leaves the vendor still holding the instance, which is what made the *next* round answer `session_conflict`. So `--poll-attempts=N` sets `session_poll_attempts` (default 300, 2 s apart) and the budget is no longer the thing that ends a launch. The same round shows the queue is worth letting grow: 117 rtsp endpoints is what a ready session carries.

34. **A free-tier hour needs a keep clock 720 events deep, and the pilot's arrays were sized for 96** - a free-tier hour is 3600 s, and the idle timer the host counts only goes down on events, so holding it down every 5 s of that hour needs 720 keep events on top of the two notice answers; the r1.4 round sent 88 positions from its 66-deep list and the media died after 1720 s, while the attended round's 1734 positions held a full hour. r1.6 sizes `pilot_press_at`/`pilot_press_key` at 1200 and parses and appends to that, so `--pilot-gap=5 --pilot-times=760` arms the whole hour rather than silently truncating at 96.

35. **The client can now measure its own input path and its own sound, and both are written to be read after the session** - `--debug-input` stamps every submit with `opennowlite-input: <kind> submit no=N at +Nms took N us` on the session clock (capped at 400 lines), which splits the complaint that a press of a pop-up's OK closed a few seconds later into what the seat's own key-up path costs (separate, and already visible as `opennowlite-input: key code=`), what the streamer's own accept call cost (the `took N us` half, measured at 4-6 us on this board), and what the picture answered, which is read off the video witness on the same clock. `--debug-audio=FILE` with `--debug-audio-seconds=S` (60 by default) writes the first S x 200 Opus frames the tap hands over as 4-byte little-endian length-prefixed packets, announces itself as `opennowlite-audio: witnessing N s of sound into FILE`, ticks as `opennowlite-audio: witness frames=N bytes=N at +Nms` every 6000 frames and closes as `opennowlite-audio: witness closed frames=N bytes=N`. It is a pure `fwrite` inside `media_push_audio`: no second pipeline, no decode, a bounded frame count rather than a bounded number of seconds, so a session does not pay for it. The seat has `opusdec` and `oggmux` but no `oggmuxenc`, no `audiovolume`, no `trimmer` and no `audioframecutter`, so the framed dump is what gets muxed offline. r1.6's own deb is md5 `abaf4b77a2251d2812bbe6175edd4a7b`, its client binary on the seat is md5 `4e8f82e65ce700ab4cbfe23fa1e59ac4`, and the launcher `d0102c5535a4eb16dd28e2402796007a` and the FFI `0c79eeec9a9a2a1e33fbe444dfe297f3` are unchanged from r1.5.

36. **The session's account credentials live in `fallback-sessions/`, and moving that record aside is what ends authentication** - that record is not only a session pointer: its JSON carries a `tokens` object (`accessToken`, `idToken`, `clientToken` and their expiry fields) next to `provider` and `user`, and the core reads it to answer `catalog.game.get`. Measured on Nov 8: with the record present the round reaches `session ready` (poll attempt 37) and streams, and with it moved to `/home/weston/backup/` every round dies in 15 s at `opennowlite: launch catalog.game.get authentication_required` after `auth: saved session unavailable: OS credential store is unavailable or locked`, with no `auth.session.get` line in the diagnostics log at all - the three attempts logged `outcome=authentication_required durationMs=11623`, `11458` and `11485`. The stale `unresolved-allocation.json` the lite pilot moves aside each round is a different file (384 bytes, its own name) and carries no credentials; only that one is ever moved. The `auth: migrated N Electron account session(s) into the OS credential store` line and the `provider: DIG discovery endpoint is unreachable; using fallback https://latam-west.dig.geforcenow.nvidiagrid.net/` line appear in every working round as well, so neither of them says a launch is broken - the strings that do are `saved session unavailable` and `authentication_required`.

37. **A stream can hold 59.5 fps of one picture, so a frozen game is not a stalled stream** - the r1.5 round of Nov 7 was read as "no picture" and its witness says otherwise: media ran `elapsed=1230.7s ... frames=73197` (59.5 fps), `video units=73200 queued=72`, `audio packets=244500` at 199/s, `qosBweKbps=20964`, and the picture cost about 900 bytes a frame, which is a static scene and nothing else. The storyboard of the round's 22597632-byte witness (`select=not(mod(n,600))`, one frame per 10 s, 66 frames) shows busy Steam-store frames from 0 to 80 s, one fully black frame at n=4680/4740 (+78/+79 s), then a byte-identical dark frame (md5 `39c1b31b`) from +90 s to the end of the 660 s cap, which carries no text and no loading bar even raised for contrast (`eq=brightness=0.18:contrast=1.5:gamma=2.2`) - Steam had launched the game and the game sat on one frame for ten minutes while the Return armed at +212 s had no visible effect. The appsrc's own `queued` stayed near 72 bytes, so the decoder was not stalled. What the next rounds are being watched for is therefore the input side and not the media side: the picture only moves if the press reaches the game and the game acts on it.

38. **A session whose picture stopped is ended by the client, not held - `--media-dead=S` reads the client's own tally** - the r1.9 round of Nov 8 showed what a dead stream looks like from the client's side: media stopped at `NVST rx-stats elapsed=460.1s ... frames=26917` while the vendor kept the session alive and kept answering input, which the pilot logged as `jiggle 186 at +1142 s` and `submit no=192 at +1142019ms took 5us`, so 680 further seconds of that round's budget carried one frame and nothing else. r1.7's `media_watch()` ends the session on the client's own `media.video_units` tally rather than the streamer's stats, because the tally is the client's own state: once the count has stopped advancing for `--media-dead=S` seconds it prints `opennowlite: media stopped at units=N for Ns at +Nms -- ending the session` and leaves. Measured on the two rounds that used it the same day: the r20 attempt that had media for 72009 units answered `media stopped at units=72009 for 120s at +1367s` and the r21 attempt that had media for 62696 units answered `media stopped at units=62696 for 120s at +1201s`, both of which end their round in about 23 min rather than holding one frame to the round's 26 min cut-off, so the next attempt pays its own vendor prepare. The guard is `--media-dead=0..900`, and 0 leaves the watchdog off.

39. **An entry of `--pilot-at` with no suffix is a pointer move, because the plain seconds of a dense clock are the keep clock and not a notice** - the r20 rounds armed `63:r,212:r` plus a grid of 247 bare seconds and the client logged `opennowlite-pilot: press 115 vk=0x0d at +777s` every 5 s from +217 s on: the list parser's default key was Return, so the whole keep clock pressed Return. What that costs is visible in the witness: Destiny 2's title screen really does say `PRESS [Return] TO PLAY`, but once the game reaches its character screen Return is the game's own text chat (`Press enter to open text chat`), so 250 Returns typed newlines into a chat box - the character model animates and the class card, the light number and the chat lines stay in place from +220 s to +300 s of session and the game never leaves the screen. r1.8 parses a bare second as a pointer move (the same key 0 the `--pilot-gap` fillers already used) and adds two acts to the suffix set: `:s` is Space, the platform's Select on that screen, and `:c` is a left click at `--pilot-point`, the middle by default, which pilot_send sends as the position first and then `streamer.button(engine, 1u, pressed)` because an absolute-mode click cannot land without a position. The lite pilot's `ACTS` arms those at `307:s,322:s,337:s,352:s,367:s,382:c,402:c,422:c,452:c,502:c,602:s,702:s,802:s,902:c,1102:c,1302:c`, which is over the window the character screen was measured to sit in.

40. **The stream's own sound arrives at +99 s, and its first 99 s are the encoder's silence floor rather than a broken pipe** - r1.9's `--debug-audio` dump of 120 s was measured offline through the board's own libopus: 24000 packets over 120 s = 200/s, every one 240 samples per channel at 48 kHz with 2 channels (5 ms each, `opus_packet_get_nb_channels`=2 and `opus_packet_get_nb_samples`=240), mean 70.1 bytes a packet = 14.0 kB/s, and **24000 of 24000 decoded with 0 failures**, so nothing was lost on the way in. The level says what the stream carried: for the first 99 whole seconds the loudest sample sits at -84 dBFS and rms at -96.1 dBFS, which is Opus's own silence floor, and at +100 s the sound is there (`rms=-43.2 dBFS peak=-23.4 dBFS`, then -33.1/-21.9 at +110 s), mean over the whole dump -35.8 dBFS rms and loudest single sample -12.4 dBFS. So "no sound" during the Steam-store part of a session is the stream's silence, not the client's. Reading the dump needs the calling convention the board's `libopus.so.0` actually wants, which the headers on the seat do not show: `opus_decoder_create(Fs, channels, &err)` wants the rate first - (2, 48000) answers `err=-1` - and `opus_decode(dec, data, len, out, frame_size, ecm)` will not take `frame_size=0` (it answers -1), so the packet's own length has to be asked first and passed. `opus-measure.py` (this machine) and `opusprobe.c` are what does that; `opusdec`/`oggmux` on the seat cannot mux length-prefixed packets and there is no `oggmuxenc`, `audiovolume`, `trimmer` or `audioframecutter`.

41. **The streamer's own accept call costs 3-7 us, so the input delay that feels slow is not inside it** - `--debug-input` stamps every submit with `opennowlite-input: <kind> submit no=N at +Nms took N us` on the session clock, and the measured stamps are `press submit no=3 at +63041ms took 5us`, `press submit no=4 at +64045ms took 4us`, `position submit no=56 at +462003ms took 3us` and 4-7 us for the rest: the call into the vendor's streamer is one part in a thousand of the seconds a user reports. The other two halves are already separate and measurable: the seat's own key path shows as `opennowlite-input: key code=` lines when the keyboard is at the client, and what the game answered is read off the round's witness on the same clock, so a press's delay is (seat key-up) + (streamer accept, 3-7 us) + (whatever the game does with it), and only the last of those is the game's own.

42. **A refused launch spends none of the hour, so the pilot counts sessions and retries conflicts on their own clock** - the r20 runner of Nov 8 armed 8 rounds and got `opennowlite: launch session.create session_conflict` in 15-21 s from rounds 2 and 4-8 after the two long ones, and exited with 3268 s spent and 6 of its 8 rounds having done nothing but be refused: after a session ends, the vendor holds the instance for a couple of minutes, and a launch 30 s later is refused. The lite pilot now separates the two clocks - `MAX_SESSIONS` counts only attempts whose log carries `session ready`, `MAX_ATTEMPTS` caps the attempts, and `ROUND_GAP` (150 s) waits between them. Measured on the r22 runner of Nov 8: attempt 1 at 04:23:17 was refused, attempt 2 at 04:26:12 came back `phase=preparing` on poll 3 and reached `session ready`, so one wait was enough.
