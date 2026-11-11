# NanoBrowser - the QtWebEngine browser, and how to drive it on the seat

Detail file for `nanobrowser_git.bb`, in the same shape as the other
applications in this layer: the recipe's own comments keep the pin history,
this file keeps what a human on the seat needs to run the browser and what the
board was measured to do.

## What is on the seat

`dpkg -l` says `nanobrowser 0.1+git21+b5e01869c1-r0.32 arm64`, and the payload is
one executable, `/usr/bin/nanobrowser` (265944 B): the QML module is compiled
into it by `qt_add_qml_module`, so there is no data directory to keep beside it.

Upstream is [DanielMartensson/NanoBrowser](https://github.com/DanielMartensson/NanoBrowser),
pinned at `b5e01869c10140799085a20d2c18cadb37c3f977` on `branch = "main"`, MIT.
QtWebEngine on aarch64 is built with the Clang toolchain, which is why
`meta-clang` has to be on `BBLAYERS` next to `meta-qt6`.

## How to use it on the seat

Start it from a terminal in the seat's weston session, which is what gives it the
Wayland socket it needs (`XDG_RUNTIME_DIR=/home/weston` is where that socket lives
on this image, so keep it rather than replacing it):

```sh
XDG_RUNTIME_DIR=/home/weston WAYLAND_DISPLAY=wayland-1 nanobrowser
```

Open a page at startup instead of typing it into the address bar, which is what a
kiosk-style launcher wants and what makes the run measurable:

```sh
nanobrowser --page https://duckduckgo.com
nanobrowser --page duckduckgo.com    # a bare host gets the https:// the address bar gives it
```

Running as root, or anywhere the Chromium sandbox cannot be set up, needs the
sandbox off, otherwise WebEngine refuses to render:

```sh
QTWEBENGINE_DISABLE_SANDBOX=1 nanobrowser
```

The switches the binary answers to, read out of the installed executable with
`strings`, are these:

| Switch / variable | What it does |
| --- | --- |
| `--page URL` | opens the page at startup |
| `NANOBROWSER_TIMING=1` | prints every milestone against the instant `main()` started: the QML, the first frame, the load, what the page measured itself (`ttfb`, `DOMContentLoaded`, `load`, bytes, resources) and the frame rate counted by scrolling the page |
| `NANOBROWSER_EXIT_AFTER=n` | closes through the same path as the window's X button, `n` in seconds |
| `NANOBROWSER_EXTRA_FLAGS` | appends Chromium switches (the ones measured on this board are already compiled in) |
| `NANOBROWSER_RENDERER=vulkan` | forces the Vulkan path; the build's default is OpenGL |
| `NANOBROWSER_CHROMIUM_GPU` | present in the binary; upstream's own Chromium GPU switch plumbing, not something this layer sets |
| `QTWEBENGINE_DISABLE_SANDBOX=1` | needed when running as root |

Window behaviour: NanoBrowser asks for `client_side` decorations
(`Qt.FramelessWindowHint` → `xdg-decoration client_side`) and draws its own title
bar with the minimize/maximize/close buttons, so the window gets exactly one set
of buttons. This seat's weston 13 has no `xdg-decoration` support at all and draws
no frames, so a browser that did not draw its own bar would have nothing to grab.

Closing is one path - the X button, `Alt+F4`, `NANOBROWSER_EXIT_AFTER` - which ends
the session without Chromium's teardown; on this board that teardown is what left
libwayland reporting buffer releases still attached to a destroyed window queue.

## What the board actually does

The timings are `NANOBROWSER_TIMING=1` numbers from the seat, OpenGL against
Vulkan, same pages and same session conditions:

| Page | OpenGL | Vulkan |
| --- | --- | --- |
| first frame | 1.3 s | 3.1 s |
| `example.com` | 1.7 s | 4.1 s |
| DuckDuckGo | 3.7 s | 6.7 s |
| Wikipedia | 5.5 s | 8.3 s |
| YouTube | 15.0 s | 17.5 s |
| exit | status 0 | segfault in the VeriSilicon driver |

OpenGL is hardware rendering either way: Qt Quick and Chromium share one GL
context, Chromium rasterises on the GPU and hands frames over by zero copy, which
is why scrolling Wikipedia reaches 29 fps where the fallback path manages 18. The
build chooses that renderer with `-DNANOBROWSER_DEFAULT_RENDERER=opengl`;
`NANOBROWSER_RENDERER=vulkan` is still one environment variable away.

There is a 100 MB HTTP cache on disk (the WebEngine profile), so a second visit to
a page costs only what changed - which is the reason to relaunch rather than reload
when a page is only there to be re-read.

`upower` is in `RDEPENDS` because Qt WebEngine's battery monitor asks the system
D-Bus for UPower when a page calls `navigator.getBattery()`, and YouTube does:
without the service Chromium prints three
`org.freedesktop.UPower was not provided by any .service files` errors on the
console. The board has no battery, and UPower answers "on line power".
