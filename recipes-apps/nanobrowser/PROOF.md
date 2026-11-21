# NanoBrowser - the record behind the recipe

Detail file for `recipes-apps/nanobrowser/nanobrowser_git.bb`: the pin, what the
seat's package carries and what the board was measured to do. The load and use
half lives in [README.md](README.md).

## The pin, and what makes that pin the build

Upstream is [DanielMartensson/NanoBrowser](https://github.com/DanielMartensson/NanoBrowser),
pinned at `b5e01869c10140799085a20d2c18cadb37c3f977` on `branch = "main"`, MIT.
QtWebEngine on `aarch64` is built with the Clang toolchain, which is why
`meta-clang` has to be on `BBLAYERS` next to `meta-qt6` before this recipe will
configure at all.

## What the seat has

`dpkg -l` answers `nanobrowser 0.1+git21+b5e01869c1-r0.32 arm64`, and the payload
is one executable, `/usr/bin/nanobrowser` at 265,944 B: the QML module is compiled
into it by `qt_add_qml_module`, so there is no data directory beside it and nothing
to keep.

## What the board was measured to do

The timings are `NANOBROWSER_TIMING=1` numbers from the seat, OpenGL against Vulkan,
same pages and same session conditions:

| Page | OpenGL | Vulkan |
| --- | --- | --- |
| first frame | 1.3 s | 3.1 s |
| `example.com` | 1.7 s | 4.1 s |
| DuckDuckGo | 3.7 s | 6.7 s |
| Wikipedia | 5.5 s | 8.3 s |
| YouTube | 15.0 s | 17.5 s |
| exit | status 0 | segfault in the VeriSilicon driver |

OpenGL is hardware rendering either way: Qt Quick and Chromium share one GL context,
Chromium rasterises on the GPU and hands frames over by zero copy, which is why
scrolling Wikipedia reaches 29 fps where the fallback path manages 18. The build
chooses that renderer with `-DNANOBROWSER_DEFAULT_RENDERER=opengl`;
`NANOBROWSER_RENDERER=vulkan` is still one environment variable away, and it is the
Vulkan column above that made it a choice rather than a preference.

There is a 100 MB HTTP cache on disk in the WebEngine profile, so a second visit to
a page costs only what changed - the reason to relaunch rather than reload when a
page is only there to be re-read.

## What the window is, and why closing is one path

NanoBrowser asks for `client_side` decorations (`Qt.FramelessWindowHint` →
`xdg-decoration client_side`) and draws its own title bar with the minimize/maximize/close
buttons, so the window gets exactly one set of buttons. This seat's weston 13 has
no `xdg-decoration` support at all and draws no frames, so a browser that did not
draw its own bar would have nothing to grab.

Closing is one path - the X button, `Alt+F4`, `NANOBROWSER_EXIT_AFTER` - which ends
the session without Chromium's teardown; on this board that teardown is what left
libwayland reporting buffer releases still attached to a destroyed window queue.

`upower` is in `RDEPENDS` because Qt WebEngine's battery monitor asks the system
D-Bus for UPower when a page calls `navigator.getBattery()`, and YouTube does:
without the service Chromium prints three
`org.freedesktop.UPower was not provided by any .service files` errors on the
console. The board has no battery, and UPower answers "on line power".
