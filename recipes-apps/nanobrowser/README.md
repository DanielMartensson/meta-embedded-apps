# NanoBrowser - the QtWebEngine browser, and how to drive it on the seat

Detail file for `recipes-apps/nanobrowser/nanobrowser_git.bb`. The recipe keeps the
pin history and the build switches; the numbers and the measured timings live in
[PROOF.md](PROOF.md).

## Load it

```bash
bitbake nanobrowser
```

## Use it on the seat

Start it from a terminal inside the seat's weston session, which is what gives it the
Wayland socket it needs - `XDG_RUNTIME_DIR=/home/weston` is where that socket lives on
this image, so keep it rather than replacing it:

```sh
XDG_RUNTIME_DIR=/home/weston WAYLAND_DISPLAY=wayland-1 nanobrowser
```

Open a page at startup instead of typing it into the address bar - what a kiosk-style
launcher wants, and what makes a run measurable:

```sh
nanobrowser --page https://duckduckgo.com
nanobrowser --page duckduckgo.com    # a bare host gets the https:// the address bar gives it
```

Running as root, or anywhere the Chromium sandbox cannot be set up, needs the sandbox
off, otherwise WebEngine refuses to render:

```sh
QTWEBENGINE_DISABLE_SANDBOX=1 nanobrowser
```

The switches the installed binary answers to, read out of it with `strings`:

| Switch / variable | What it does |
| --- | --- |
| `--page URL` | opens the page at startup |
| `NANOBROWSER_TIMING=1` | prints every milestone against the instant `main()` started: the QML, the first frame, the load, what the page measured itself (`ttfb`, `DOMContentLoaded`, `load`, bytes, resources) and the frame rate counted by scrolling the page |
| `NANOBROWSER_EXIT_AFTER=n` | closes through the same path as the window's X button, `n` in seconds |
| `NANOBROWSER_EXTRA_FLAGS` | appends Chromium switches (the ones measured on this board are already compiled in) |
| `NANOBROWSER_RENDERER=vulkan` | forces the Vulkan path; the build's default is OpenGL |
| `NANOBROWSER_CHROMIUM_GPU` | present in the binary; upstream's own Chromium GPU switch plumbing, not something this layer sets |
| `QTWEBENGINE_DISABLE_SANDBOX=1` | needed when running as root |

The window draws its own title bar with the minimize/maximize/close buttons, and
closing is that X button, `Alt+F4` or `NANOBROWSER_EXIT_AFTER` - the reason both are
true of this seat is measured, and is in [PROOF.md](PROOF.md).
