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

#### Patches on top of upstream

`0001`-`0038` in `recipes-apps/opennow/files/` are the only changes to the
upstream tree. (`0022` is not part of the series: it was an abandoned
experiment and never entered the tree.)

| Patch | What it does |
| --- | --- |
| `0001` | Ask the Vulkan driver whether it really exposes `VK_KHR_video_decode_h264`/`_h265`/`_av1` before offering hardware decode, and keep the driver's Vulkan chatter off `stderr`. |
| `0002` | Anchor the launcher badge to `launcherRow` instead of `parent`. The delegate is briefly parentless while the model is rebuilt, and `parent.verticalCenter` then logged a `TypeError` for every row, on every rebuild. |
| `0003` | Ask FFmpeg only for the window-system extensions this session can have. A Wayland-only board has no X11 surface, and FFmpeg reported every missing extension it had been asked for. CUDA is not offered without `/dev/nvidiactl`. |
| `0004` | Drop the VeriSilicon driver's orphaned `wl_compositor` queue report, and leave every other `libwayland` diagnostic on `stderr`. |
| `0005` | `OPENNOW_RENDERER=opengl` selects the OpenGL RHI at run time. Empty keeps upstream's Vulkan. |
| `0006` | Keep SDL3 from installing its own `SIGINT`/`SIGTERM` handlers, which swallowed `kill` and `systemctl stop`. |
| `0007` | Leave the process with `_Exit()` instead of running Qt's teardown when no restart is pending. |
| `0008` | Draw the sign-in card and its primary button directly instead of as QML layers. See below. |
| `0009` | Do not let one slow region block a launch. |
| `0010` | Print the native error message instead of a generic one. |
| `0011` | Copy decoded dma-buf frames on the CPU when the GPU cannot import them. |
| `0012` | Log presented frames and dump the first copied frame. |
| `0013` | Import the decoder's linear NV12 dma-buf frames as Vulkan buffers, without a copy. |
| `0014` | Cache imported dma-buf buffers and log the render rate. |
| `0015` | Count the frame notifications and offer a forced repaint, so a session can be instrumented without a debugger. |
| `0016` | Run the stream window opaque and sample the video 1:1 (`OPENNOW_OPAQUE_WINDOW=1`, `OPENNOW_VIDEO_NEAREST=1`). |
| `0017` | `OPENNOW_VIDEO_MODE_FILE` switches the video pass while streaming, to attribute its GPU cost within one session. |
| `0018` | Hand the decoder's DMA-BUF to a Wayland surface of its own instead of converting it (`OPENNOW_WAYLAND_VIDEO=1`). |
| `0019` | Opt-in development capture: `OPENNOW_FRAME_DUMP=<dir>` for the decoded frame, a trigger file for the interface window. |
| `0020` | Describe the decoder's 1080p buffer at its visible height; the padded height made the compositor kill the connection. |
| `0021` | `F11` switches the stream window between a fullscreen toplevel and a decorated 1280x720 window whose close button ends the stream. |
| `0023` | Install every `wl_keyboard` listener (a missing one desynchronised the connection), re-enable `F11` during a stream, and fix the frame dump's buffer sizes. |
| `0024` | Repaint the interface on a clock of its own while the stream window exists, and add `/tmp/opennow-close` for the compositor's close path. |
| `0025` | Pull and present frames from the interface thread on a clock of its own, so the interface can no longer throttle the video. |
| `0026` | The stream window becomes the default presentation path; `OPENNOW_WAYLAND_VIDEO=0` opts out. |
| `0027` | The stream window takes the pointer and the keyboard and forwards them to the host. Before this, only `F11` reached the game, because the interface window was never active behind the stream window. |
| `0028` | Close the frame descriptor only once Wayland has taken it, and close the descriptor of a recorded frame that is not presented. A stream that keeps producing frames while the picture is refused no longer leaks a descriptor per frame. |
| `0029` | Keep one `wl_buffer` per decoder buffer instead of building one per frame. Weston 13 never closes the descriptors behind an imported dma-buf buffer, so a stream at 50 fps exhausted the compositor's 1024 descriptors in about twenty seconds and the connection died with `invalid arguments for zwp_linux_buffer_params_v1.add`. |
| `0030` | Stop repainting the interface while the stream window covers it, and report the fourcc, size, modifier and plane layout of every buffer the surface hands to the compositor. The repaint clock was left over from `0024`, when each frame was pulled during an interface repaint; it kept an invisible window in the compositor's buffer queue, and the one assertion seen in the VeriSilicon driver's Wayland WSI came from that swapchain. |
| `0031` | Let the mouse turn the game: while the stream window is fullscreen the pointer is locked to it and only the movement goes to the host, so a game turns instead of the cursor running into the edge of the screen. |
| `0032` | Switch the window mode once per `F11` press. The release repeated the switch, so one tap went fullscreen, windowed and fullscreen again. |
| `0033` | Treat a frame the compositor cannot take as a dropped frame instead of a failed session. The first unpresentable frame after a stream starts used to stop the transport and reconnect; now two seconds without a presentable frame is the failure. |
| `0034` | Open the streamer's input path from the stream window, and let the host decide whether it wants a position or movement. Without `opennow_streamer_set_capture_active` the input queue discarded every event, so the cursor in the game never moved and no key reached it. Locking the pointer for every fullscreen stream also sent movement to menus and login screens, which are aimed with a position; the window now follows the host's cursor state. |
| `0035` | Give the stream window a Wayland connection of its own. It used Qt's display, which Qt 6 dispatches on an event thread of its own, and libwayland allows one dispatcher per queue: the video path raced Qt on every event, the VeriSilicon WSI saw a second release fence for a buffer it was still using (the `buffer_fenced_release` assertion that froze a session after a few minutes), and a frame descriptor closed after a full socket became "the Wayland connection experienced a fatal error: Bad file descriptor". The surface now connects for itself, drains its socket without blocking from the frame clock, and only closes a frame descriptor once the flush that carries it succeeded. |
| `0036` | Anchor a feature icon to its row instead of to a parent that is briefly gone. Every rebuild logged `DesktopGameLabels.qml:138: TypeError: Cannot read property 'verticalCenter' of null` for each icon. |
| `0037` | Let the interface draw with OpenGL ES while the video keeps its own Vulkan device. The `buffer_fenced_release` assertion that ended a session came from the only Vulkan swapchain in the application, the interface's own: the VeriSilicon WSI asserts when a release fence is still set on a buffer the compositor hands back. The stream window presents decoder DMA-BUFs through `wl_dmabuf` and never involves the WSI, so the streamer now creates the Vulkan device it needs for the decoder and the interface runs on OpenGL ES (`OPENNOW_RENDERER=opengl`), which is also the better-tested driver path on this board. |
| `0038` | Create a buffer-export Vulkan device when the board has no Vulkan Video. The stream window presents the decoder's DMA-BUF through `wl_dmabuf` and records no GPU work, but the native frame producer still validates the adopted instance, physical device, logical device and graphics queue before it exports a frame. The STM32MP25's VeriSilicon device has neither `VK_KHR_video_queue` nor a decode extension and exposes one graphics queue family, so the shared-device probe rejected every candidate and the interface ended up without a device: the stream window reported "The streamer has no Vulkan device for the video" and presented nothing. Such a board decodes through V4L2 Request or VA-API, so the device is now built as a plain buffer-export device - first graphics queue family, no decode profiles - and `ApplicationStartup` tries it on every board that needs its own device instead of probing for Vulkan Video first. Qt wraps the native instance only when the scene graph draws with Vulkan, so with OpenGL it no longer takes over an instance it never uses. FFmpeg is also asked only for the surface extensions this session has, which removes its "extension not found, excluding" warnings on a Wayland-only board. |
| `0039` | Keep the decoder's DMA-BUF handle open until the presenter has copied the frame. The FFmpeg v4l2request decoder closes the dma-buf handle as soon as it recycles the decode output, which can happen before the presenter maps the object for the CPU copy in `DmaBufFrame::copy_nv12_planes`; the mmap then failed with `Bad file descriptor (os error 9)` and the frame was dropped. `drm_frame` now duplicates every object handle with `F_DUPFD_CLOEXEC`, hands the duplicates to `DmaBufObject`, and owns them through a small wrapper that closes them when the frame goes away, so the buffer stays addressable for exactly the frame's lifetime and the handle count stays balanced. |

#### The stream window

A running stream has two surfaces on the board's Weston: the application's own
window, which draws the interface, and a toplevel of OpenNOW's own that carries
the decoder's frames. The second is created from Qt's existing compositor
connection with `xdg-shell` and `linux-dmabuf`, so one connection serves both,
and OpenNOW owns its lifetime.

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
frame that was already in flight arrived without its descriptor and came back as
`invalid arguments for zwp_linux_buffer_params_v1.add`, which Qt reports as a
fatal Wayland error and turns into an abort. Restarting Weston clears the count;
the numbers here come from a freshly started compositor.

Patch `0029` keeps the compositor out of that corner by giving it fewer objects
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
window was inactive, and OpenNOW's own input path - which only forwards events
while its window is active - dropped every pointer and key event. Only `F11`
arrived, because the stream window handled that key itself.

Patch `0027` reads input where the compositor sends it. The stream window takes
the whole surface as its input region, keeps a `wl_pointer` next to the
`wl_keyboard` it already had, and forwards positions, buttons, the wheel and keys
to the host through the streamer's input API. Positions are expressed in the
frame's own pixels - the surface carries the whole frame, so fullscreen is 1:1
and the windowed form is scaled - keys are translated from Linux key codes to the
Windows virtual keys the host speaks, and the local cursor is hidden because the
game draws its own. Keys still held when the window goes away are released
first, so the game is not left walking.

The video target links Qt Gui only, so it does not call the streamer itself: it
takes a small table of input hooks that the render callback fills in with the
streamer's input calls. That keeps the Rust FFI and Qt Quick out of a target that
has no business including them.

`OPENNOW_LOG_INPUT=1` logs what the stream window forwards; pointer positions at
most twice a second, keys and buttons one line each.

Three development aids exist because Weston 13 refuses the screen capture
protocol and the stream window is not part of the interface window:

| Aid | Effect |
| --- | --- |
| `OPENNOW_FRAME_DUMP=<dir>` | Write the newest decoded frame to `<dir>/frame.ppm`, at most once a second, converted on the CPU. |
| `touch /tmp/opennow-grab` | Write the interface window to `/tmp/opennow-grab.png`. |
| `touch /tmp/opennow-close` | Do what the compositor's close button does to the stream window. |

#### Why the Ui looks right on the STM32MP257F

The board did not only render slowly, it rendered wrong. Patch `0008` is the
fix that matters for how the application looks: the sign-in card and its primary
button were QML layers (`layer.enabled: true`) with a `MultiEffect` drop shadow,
and the VeriSilicon Vulkan driver segfaults in `QSGRhiLayer::grab()` when the
card re-renders while the QR panel appears. Both are drawn directly now, so the
card no longer renders itself into an offscreen buffer on every repaint. Patch
`0004` takes the driver's own `libwayland` error off the console, where it read
like the application failing.

Patch `0005` matters for how the Ui *feels*: on Vulkan the driver compiles the
Qt Quick shader for every material on the fly, so any surface that has not been
drawn before stalls for about a second the first time it appears. OpenGL avoids
that. The streaming path still hands its textures to Qt through the Vulkan
device, so `OPENNOW_RENDERER` only moves the Ui, never the video.

#### Measured stream throughput

The stream no longer travels through the Qt scene graph. The decoder's DMA-BUF
is handed to a Wayland surface of its own, which the compositor composites - and
scans out when the format allows - without the GPU converting a pixel. It is the
same path YtGst reaches through `waylandsink`.

With `OPENNOW_LOG_PRESENT=1` both halves log themselves, about once a second:

```
wayland-video fps=55.0 committed=4226 dropped=0
opennow-present fps=0.0 render=28.6 swapped=0
```

`wayland-video fps=` is how often a decoded frame reached the compositor,
`dropped=` counts frames that were superseded before they could be shown, and
`committed=` is the running total. `opennow-present render=` is how often the
interface repainted; the video no longer depends on it.

Measured in a live session (Ontario zone, 1280x720 stream, fullscreen on the
1920x1080 output, Vulkan decoder, package `r0.33`):

| Reading | Value | Source |
| --- | --- | --- |
| Frames the server sent | ~55 /s | `NVST rx-stats ... frames=` |
| Frames presented | 54-55 /s | `wayland-video fps=` |
| Frames dropped | 0 | `wayland-video dropped=` |
| Interface repaints | ~29 /s | `opennow-present render=` |
| Audio | armed, no underruns logged | `NVST control/audio bundle armed` |
| Decoder in use | `/dev/video4`, `st,stm32mp25-vdec-dec` | `/proc/<pid>/fd` |

`F11` between fullscreen and windowed does not change the rate, and closing the
stream window ends the session: `native-streamer.log` records
`status=stopped message=User stopped the session`.

The same session presented 6-8 frames a second while the video went through the
Qt scene graph (patches `0013`/`0014`, package `r0.20`), and about 28 once the
decoder's DMA-BUF was imported into that scene graph as a Vulkan buffer. Two GPU
passes - NV12 to RGBA, then a sampled texel copy - cost more than everything
else in the frame together.

Two things are worth knowing before trusting a low number in a session log:

- `OPENNOW_FRAME_DUMP` is not free. Converting one 1080p frame from NV12 to RGB
  on the two A35 cores costs about a second, so a session with the dump enabled
  presents about one frame a second. It is a diagnostic, not a setting.
- The decoder still needs CMA. With CMA down to 10 MB of 128 MB it can only keep
  a couple of capture buffers queued, and `dmesg` shows allocations failing
  outright:

```
cma: cma_alloc: linux,cma: alloc failed, req-size: 394 pages, ret: -12
```

  so the image should reserve 256 MB of CMA on the kernel command line
  (`UBOOT_EXTLINUX_KERNEL_ARGS:append = " cma=256M"`, as
  `meta-watermelon-wine` does).

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
| OpenNOW | Own Wayland toplevel, DMA-BUF to the compositor | 1280x720, fullscreen | 54-55 | `wayland-video fps=` |
| YtGst | GStreamer `waylandsink`, own subsurface | 1920x1080 | not measured yet | GStreamer `fpsdisplaysink` |
| NanoBrowser | QtWebEngine | - | not measured yet | compositor frame count |

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
