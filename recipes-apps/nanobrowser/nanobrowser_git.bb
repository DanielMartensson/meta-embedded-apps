SUMMARY = "NanoBrowser - minimal Qt6 QML browser built on Qt WebEngine"
DESCRIPTION = "NanoBrowser is a lightweight QML browser for embedded Linux built \
on Qt WebEngine. It renders via Vulkan with an OpenGL fallback."
HOMEPAGE = "https://github.com/DanielMartensson/NanoBrowser"
BUGTRACKER = "https://github.com/DanielMartensson/NanoBrowser/issues"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=19b5c44dd600b05d43b5bb78ad0bd491"

SRC_URI = "git://github.com/DanielMartensson/NanoBrowser.git;protocol=https;branch=main"
SRCREV = "d5c0e2aa0d33cb004229bcd420455ace0d7acb21"

PV = "0.1+git${SRCPV}"
S = "${WORKDIR}/git"

# fe28e6a (init before QGuiApplication) is not on the remote yet; the user
# will push it. Until then the build applies this patch on top of SRCREV.
PATCHES += "files/nanobrowser_init_before_qguiapp.patch"

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

RDEPENDS:${PN} += " \
    qtbase-plugins \
    qtdeclarative-qmlplugins \
    qtwebengine \
    qtwebengine-qmlplugins \
"