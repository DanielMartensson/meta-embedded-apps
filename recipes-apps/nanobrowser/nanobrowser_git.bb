SUMMARY = "NanoBrowser - minimal Qt6 QML browser built on Qt WebEngine"
DESCRIPTION = "NanoBrowser is a lightweight QML browser for embedded Linux built \
on Qt WebEngine. It renders via Vulkan with an OpenGL fallback."
HOMEPAGE = "https://github.com/DanielMartensson/NanoBrowser"
BUGTRACKER = "https://github.com/DanielMartensson/NanoBrowser/issues"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=19b5c44dd600b05d43b5bb78ad0bd491"

SRC_URI = "git://github.com/DanielMartensson/NanoBrowser.git;protocol=https;branch=main"
SRCREV = "fb28881261e0a61017bbbf251f05b4934de08ee7"

PV = "0.1+git${SRCPV}"
S = "${WORKDIR}/git"

# Need Qt 6.8 with WebEngine. QtWebEngine on aarch64 additionally requires the
# Clang toolchain (meta-clang) to build the engine itself.
DEPENDS = " \
    qtbase \
    qtdeclarative \
    qtwebengine \
"

inherit qt6-cmake
DEPENDS:append = " qtdeclarative-native"

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