SUMMARY = "NanoBrowser - minimal Qt6 QML browser built on Qt WebEngine"
DESCRIPTION = "NanoBrowser is a lightweight QML browser for embedded Linux built \
on Qt WebEngine. It renders through the GPU (OpenGL by default on this board, \
Vulkan as an option)."
HOMEPAGE = "https://github.com/DanielMartensson/NanoBrowser"
BUGTRACKER = "https://github.com/DanielMartensson/NanoBrowser/issues"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=19b5c44dd600b05d43b5bb78ad0bd491"

SRC_URI = "git://github.com/DanielMartensson/NanoBrowser.git;protocol=https;branch=main"
SRCREV = "c60c0d45095364dec2d86dc132f717d0f5874b81"

PV = "0.1+git${SRCPV}"
S = "${WORKDIR}/git"

# PKGR: the PR server counter (PRAUTO) resets whenever the recipe changes; pin it
# so the deb stays >= the version installed on the board.
#
# r0.31 is about the time a page takes to appear, and about leaving quietly:
#   * --page URL opens a page at startup, so the browser can be measured (and
#     used from a kiosk-style launcher) without typing; a bare host name gets
#     https:// like the address bar gives it.
#   * NANOBROWSER_TIMING=1 prints every milestone against the instant main()
#     started: the QML, the first frame, the load, what the page measured itself
#     (ttfb, DOMContentLoaded, load, bytes, resources) and the frame rate counted
#     by scrolling the page. NANOBROWSER_EXIT_AFTER=n closes through the same
#     path as the X button, NANOBROWSER_EXTRA_FLAGS appends Chromium switches.
#   * 100 MB HTTP cache on disk (WebEngineProfile), so a second visit to a site
#     only costs what changed.
#   * the Qt Quick backend is chosen at build time and this board picks OpenGL,
#     measured against Vulkan on the same pages: first frame 1.3 s against 3.1 s,
#     example.com 1.7 s against 4.1 s, DuckDuckGo 3.7 s against 6.7 s, Wikipedia
#     5.5 s against 8.3 s, YouTube 15.0 s against 17.5 s, and status 0 on exit
#     against a segfault in the VeriSilicon driver. OpenGL is still hardware
#     rendering: Qt Quick and Chromium share one GL context, so Chromium
#     rasterises on the GPU and hands frames over by zero copy (29 fps scrolling
#     Wikipedia against 18 without). Vulkan stays one environment variable away
#     (NANOBROWSER_RENDERER=vulkan).
#   * closing is one path (X, Alt+F4, NANOBROWSER_EXIT_AFTER) that ends the
#     session without QtWebEngine's Chromium teardown, where libwayland reported
#     the window queue it destroyed with buffer releases still attached.
#   * Chromium runs with the switches measured on the board (see the README), and
#     the unused second QWebEngineProfile in main.cpp is gone.
#
# r0.20 and earlier carried the VPU handover (which moved a YouTube video to a
# gst-launch pipeline); r0.19 added the reload button and the implicit sizes.
PKGR = "r0.31"

# Need Qt 6.8 with WebEngine. QtWebEngine on aarch64 additionally requires the
# Clang toolchain (meta-clang) to build the engine itself.
DEPENDS = " \
    qtbase \
    qtdeclarative \
    qtwebengine \
"

inherit qt6-cmake
DEPENDS:append = " qtdeclarative-native"

# OpenGL is the measured winner on this SoC; see the note above PKGR.
EXTRA_OECMAKE:append = " -DNANOBROWSER_DEFAULT_RENDERER=opengl"

# The project has no install() rule; the QML module is compiled into the binary
# via qt_add_qml_module, so a single executable is enough.
#
# NanoBrowser picks its graphics API at startup (pickGraphicsApi()): Vulkan is
# used only when a supported GPU is present and the Vulkan instance reports
# version 1.2+ (required by QtWebEngine), otherwise it falls back to OpenGL
# (GCNANO EGL) and shows a startup banner naming the graphics API and, for
# a fallback, the reason. This keeps the broken GCNANO/VeriSilicon ICD path
# out of QtWebEngine on the weston target.
do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/nanobrowser ${D}${bindir}/nanobrowser
}

# upower: Qt WebEngine's battery monitor asks the system D-Bus for UPower when a
# page calls navigator.getBattery() (YouTube does). Without the service, Chromium
# prints three "org.freedesktop.UPower was not provided by any .service files"
# errors on the console. The board has no battery, but UPower answers "on line
# power" and keeps that noise out of the terminal.
RDEPENDS:${PN} += " \
    qtbase-plugins \
    qtdeclarative-qmlplugins \
    qtwebengine \
    qtwebengine-qmlplugins \
    upower \
"
