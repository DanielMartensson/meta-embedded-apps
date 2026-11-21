# YtGst - the YouTube client, and how to drive it on the seat

Detail file for `recipes-apps/ytgst/ytgst_git.bb`. The recipe keeps the pin history
and the build switches; the numbers and the measured rounds live in
[PROOF.md](PROOF.md).

## Load it

```bash
bitbake ytgst
```

## Use it on the seat

Start it from a terminal inside the seat's weston session, keeping the runtime dir the
socket lives in:

```sh
XDG_RUNTIME_DIR=/home/weston WAYLAND_DISPLAY=wayland-1 ytgst
```

The client queries YouTube with `yt-dlp`, streams the video through GStreamer and
draws a control panel plus a picture window of its own. What it can do, and what it
was measured to do:

- search and play a video, choose the resolution, set the playback speed, show
  subtitles
- download the resolution the panel shows as one plain mp4 (H.264 + AAC)
- panel sizes small, medium, large, extra large - the XL scale is clamped to the
  screen width
- 10 s of video are held in the queue between the demuxer and the parser, which is
  what keeps the content position at one second a second through an HLS segment
  hiccup

The switches the installed binary answers to, read out of it with `strings`:

| Variable | What it does |
| --- | --- |
| `YTGST_RENDERER=vulkan` / `=opengl` | overrides the renderer for one run; the build's default is OpenGL |
| `YTGST_VIDEO_DECODER` | the GStreamer decode element used for the picture |
| `YTGST_VIDEO_SINK` | points the picture at a different sink than `waylandsink`, for a measurement run |
| `YTGST_AUDIO_SINK` | points the audio branch at a file, which is how the sound was verified rather than guessed |
| `YTGST_NO_AUDIO=1` | plays the picture alone, to prove the audio branch is not what keeps the pipeline alive |
| `YTGST_DEBUG_FPS` | prints the frame rate for a measurement run |
| `YTGST_HEIGHT` | sets the panel height for a measurement run |

The window draws its own frame, and the picture goes into a Wayland sub-surface of it,
so the video fills exactly the area inside the frame. Why the compositor makes that
necessary - and what the panel, the sound and the queue were measured to do - is in
[PROOF.md](PROOF.md).
