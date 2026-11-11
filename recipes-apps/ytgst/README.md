# YtGst - the YouTube client, and how to drive it on the seat

Detail file for `ytgst_git.bb`, same shape as the other applications in this
layer: the recipe keeps the pin history and the build switches, this file keeps
what a human on the seat needs to run the client and what the board was measured
to do.

## What is on the seat

`dpkg -l` says `ytgst 0.1+git7+9cb2a60806-r0.48 arm64`. The payload is two parts:
`/usr/bin/ytgst` (794440 B) and the QML module in `/usr/lib/qml/Ytgst/`
(`qmldir`, the `qml/` pages, `ytgst.qmltypes`). Both are needed: `main.cpp` calls
`loadFromModule("Ytgst", "Main")` and `loadFromModule("Ytgst", "PlayerWindow")`,
which resolve against the QML import path (`${libdir}/qml` on this distro), so a
client without its module aborts with `module Ytgst is not installed`.

Upstream is [DanielMartensson/YtGst](https://github.com/DanielMartensson/YtGst),
pinned at `9cb2a60806907c6ac91fa3924ea17f910f2172af` on `branch = "main"`, MIT.
Since `r0.47` the pin is pristine: every change that used to arrive as a patch is
part of the upstream repository now, so the recipe has no patches and no vendored
source.

## How to use it on the seat

Start it from a terminal in the seat's weston session, keeping the runtime dir the
socket lives in:

```sh
XDG_RUNTIME_DIR=/home/weston WAYLAND_DISPLAY=wayland-1 ytgst
```

The client queries YouTube with `yt-dlp`, streams the video through GStreamer and
draws a control panel plus a picture window of its own. What it can do, as the
recipe's round notes say:

- search/play a video, choose the resolution, set the playback speed, show
  subtitles
- download the resolution the panel shows as one plain mp4 (H.264 + AAC), which is
  why `ffmpeg` is in `RDEPENDS`
- panel sizes small, medium, large, extra large: the panel is fitted to the output
  it lands on and the XL scale is clamped to the screen width, so XL is 2.5x on a
  1920 px screen and smaller on smaller ones
- 10 s of video are held in the queue between the demuxer and the parser. At
  GStreamer's default of about a second an HLS segment hiccup emptied the queue,
  the sink starved for about 2 s and the stream jumped when the segment arrived;
  a window of 480 frames arrived carrying 11.65 s of content where 480 frames at
  60 fps is 8 s. With 10 s the content position advances by exactly one second a
  second in every round

The switches the installed binary answers to, read out of it with `strings`:

| Variable | What it does |
| --- | --- |
| `YTGST_RENDERER=vulkan` / `=opengl` | overrides the renderer for one run; the build's default is OpenGL (`-DYTGST_DEFAULT_RENDERER=opengl`) |
| `YTGST_VIDEO_DECODER` | the GStreamer decode element used for the picture; the build asks for `v4l2slh264dec`, the V4L2 stateless hardware decode this platform ships a plugin for |
| `YTGST_VIDEO_SINK` | points the picture at a different sink than `waylandsink`, for a measurement run |
| `YTGST_AUDIO_SINK` | points the audio branch at a file, which is how the sound was verified rather than guessed |
| `YTGST_NO_AUDIO=1` | plays the picture alone, to prove the audio branch is not what keeps the pipeline alive |
| `YTGST_DEBUG_FPS` | prints the frame rate for a measurement run |
| `YTGST_HEIGHT` | sets the panel height for a measurement run |

The window: weston 13's desktop-shell has no `xdg-decoration` support and no frame
code, so every window on this board is bare, and the sink's own toplevel could
neither be moved nor resized. The client therefore draws its own frame - a title
bar with the title, fullscreen and close buttons, drag and resize areas - and the
picture goes into a Wayland *sub*-surface of the application's window: the window's
`wl_surface` and the connection behind it are handed to the sink through a
`GstContext` (`GstWlDisplayHandleContextType`) and the `GstVideoOverlay` window
handle before the first frame, so the video fills exactly the area inside the frame
(`render-rectangle`).

## What the board actually does

The panel is OpenGL on this SoC, measured: first frame 0.79 s against 4.05 s on
Vulkan, and 39.1 % of a core instead of 46.6 % for the same panel. The picture is
drawn by `waylandsink` either way, so the renderer choice only moves the QML
scene graph - which is also why the earlier qml6glsink/GstGL freeze is gone:
`waylandsink` never enters the scene graph.

The sound was verified rather than changed: pointed at a file with `YTGST_AUDIO_SINK`
the audio branch decodes 44100 Hz stereo at -23 dB mean, its sink-input is live and
uncorked at 100 % in PipeWire, and the chain is the one `youtube-play.sh` uses,
`audioconvert ! audioresample ! 48 kHz S16LE ! pulsesink`. Sound, picture, seeks and
base time stay in step by construction because they are one pipeline: two pipelines
cost real desync - the sinks' positions showed the audio 660 ms behind the picture,
because the audio pipeline's own PAUSED→PLAYING change is not instant.

`gstreamer1.0-plugins-bad-waylandsink`, `yt-dlp`, `ffmpeg` and `pulseaudio` are in
`RDEPENDS` because the client reaches each of them at runtime.
