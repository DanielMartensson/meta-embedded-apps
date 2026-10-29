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
| Patches applied | 14, `recipes-apps/opennow/files/0001`…`0014` |
| Package version | `1.0.4+git1+b3fe71026d-r0.45` |
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

`0001`-`0014` in `recipes-apps/opennow/files/` are the only changes to the
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

Errors and warnings seen in a session, verbatim, with what is known about each:

| Line | Count | Assessment |
| --- | --- | --- |
| `opennow-qt: api/gc_egl_wayland.c:2699: buffer_fenced_release: Assertion 'buffer->release_fence_fd == -1' failed.` | once per `F11` press during the stream | vendor VeriSilicon WSI assert; ends the session (also seen at `src/wsi/gc_wsi_surface_wayland.c:611`, same assertion) |
| `wayland-video: flush failed: File exists (display error 0)` | ratio `flushFailed/committed = 0.985` | our flush is the second flush of the same connection (`display error 0` = connection healthy); picture stays smooth and `dropped=0`, so this is a double-flush artifact, not lost frames |
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
| The picture is smooth but `flushFailed` is nearly `committed` | Not lost frames. It is the second flush of the same connection; check `dropped=` and the picture instead |
| `Failed to import plane 0 as R8`, then the stream window closes | The decoder retired the frame before the compositor imported it. Patch `0012` pins exactly one handoff frame; a wider ring pins the capture pool and breaks the stream instead |
| `invalid arguments for zwp_linux_buffer_params_v1.add` after ~20 s | The compositor's 1024-descriptor limit. The surface reuses one `wl_buffer` per decoder buffer, so check whether the decoder's pool changed shape |
| `dma alloc of size … failed` / `page allocation failure: order:10` in `dmesg` | The decoder needs CMA and a contiguous pool: `cma=512M` on the kernel command line, and headroom above ~756 MiB available |
| Audio arrives but plays late | Not fixed yet. The wire is clean (0 dropped, 200 packets/s in) and OpenNOW's low-latency queue is what throws the stale packets — see the open items below |
| `ESC` never reaches the game | Not fixed yet. Every other key and the mouse do, and `F11` reaches the app, so the key is consumed inside Qt rather than dropped on the floor — see the open items below |
| No hardware-accelerated video in OpenNOW | OpenNOW reports no usable backend. With the default build it needs a V4L2 memory-to-memory node offering NV12 or I420; with `linux-vaapi` it needs a stateless node. Confirm with `v4l2-ctl -d /dev/video0 --list-formats-ext` |
| QtWebEngine build is slow / OOM on 8 GB host | Lower build parallelism via `PARALLEL_MAKE = "-j<N>"` in `local.conf` |

### Open items on this board

Recorded here so the next reader does not mistake them for fixed:

1. **`ESC` does not reach the game** in Dark and Darker. Every other key and the
   mouse work, and `F11` reaches the app (it toggles the stream window), so the
   key makes it to Qt and is consumed there rather than dropped on the floor.
   Suspect the seat/key handling in `opennow-qt/src/app/ApplicationStartup.cpp`
   and Qt's own Escape handling in the interface, which owns the key before the
   stream window's keyboard seat gets it.
2. **`F11` during the stream still ends the session** in both renderers. The
   default swap-interval 2 throttle did not remove the vendor assert, so the
   `F11` path reaches the fence from somewhere other than the interface window's
   own present cycle.
3. **Audio plays late** (`queue-dropped` ~2/s, `underflows` 3–5) in both renderers.
   The wire is clean and PipeWire is not starved, so the decision is still open
   between OpenNOW's play-queue policy (app side) and the interface repaint
   clock that burns `QSGRenderThread`'s CPU (app side, patch `0013`/`0014`).

Caveats on the numbers above: single runs per phase, not an average of many
sessions; `presents/s` is only available for sessions launched with
`OPENNOW_LOG_PRESENT=1` and captured to a file; the board has no swap device and
only ~432 MB free on the rootfs, so headroom, not spill, is the limit.
