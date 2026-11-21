# YtGst - the record behind the recipe

Detail file for `recipes-apps/ytgst/ytgst_git.bb`: the pin, what the seat's package
carries and what the board was measured to do. The load and use half lives in
[README.md](README.md).

## The pin, and what makes that pin the build

Upstream is [DanielMartensson/YtGst](https://github.com/DanielMartensson/YtGst),
pinned at `9cb2a60806907c6ac91fa3924ea17f910f2172af` on `branch = "main"`, MIT.
Since `r0.47` that pin is pristine: every change that used to arrive as a patch is
part of the upstream repository now, so the recipe carries no patches and no
vendored source.

## What the seat has

`dpkg -l` answers `ytgst 0.1+git7+9cb2a60806-r0.48 arm64`, and the payload is two
parts: `/usr/bin/ytgst` at 794,440 B and the QML module in
`/usr/lib/qml/Ytgst/` (`qmldir`, the `qml/` pages, `ytgst.qmltypes`,
`ytgst_qml_module_dir_map.qrc`). Both are needed: `main.cpp` calls
`loadFromModule("Ytgst", "Main")` and `loadFromModule("Ytgst", "PlayerWindow")`,
which resolve against the QML import path (`${libdir}/qml` on this distro), so a
client without its module aborts with `module Ytgst is not installed`.

## What the board was measured to do

The panel is OpenGL on this SoC, measured: first frame 0.79 s against 4.05 s on
Vulkan, and 39.1 % of a core instead of 46.6 % for the same panel. The picture is
drawn by `waylandsink` either way, so the renderer choice only moves the QML scene
graph - which is also why the earlier qml6glsink/GstGL freeze is gone:
`waylandsink` never enters the scene graph.

The sound was verified rather than changed: pointed at a file with `YTGST_AUDIO_SINK`
the audio branch decodes 44100 Hz stereo at -23 dB mean, its sink-input is live and
uncorked at 100 % in PipeWire, and the chain is the one `youtube-play.sh` uses,
`audioconvert ! audioresample ! 48 kHz S16LE ! pulsesink`.

Sound, picture, seeks and base time stay in step by construction because they are one
pipeline: two pipelines cost real desync - the sinks' positions showed the audio 660 ms
behind the picture, because the audio pipeline's own PAUSED→PLAYING change is not instant.

The queue between the demuxer and the parser holds 10 s of video. At GStreamer's default
of about a second an HLS segment hiccup emptied the queue, the sink starved for about 2 s
and the stream jumped when the segment arrived; one measured window of 480 frames arrived
carrying 11.65 s of content where 480 frames at 60 fps is 8 s. With 10 s the content
position advances by exactly one second a second in every round.

The panel's XL scale is clamped to the screen width, so XL is 2.5x on a 1920 px screen
and smaller on smaller ones.

## What the window is

weston 13's desktop-shell has no `xdg-decoration` support and no frame code, so every
window on this board is bare and the sink's own toplevel could neither be moved nor
resized. The client therefore draws its own frame - a title bar with the title,
fullscreen and close buttons, drag and resize areas - and the picture goes into a Wayland
*sub*-surface of the application's window: the window's `wl_surface` and the connection
behind it are handed to the sink through a `GstContext` (`GstWlDisplayHandleContextType`)
and the `GstVideoOverlay` window handle before the first frame, so the video fills
exactly the area inside the frame (`render-rectangle`).

`gstreamer1.0-plugins-bad-waylandsink`, `yt-dlp`, `ffmpeg` and `pulseaudio` are in
`RDEPENDS` because the client reaches each of them at runtime.
