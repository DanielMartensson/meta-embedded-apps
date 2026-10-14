SUMMARY = "NanoBrowser - minimal Qt6 QML browser built on Qt WebEngine"
DESCRIPTION = "NanoBrowser is a lightweight QML browser for embedded Linux built \
on Qt WebEngine. It renders via Vulkan with an OpenGL fallback."
HOMEPAGE = "https://github.com/DanielMartensson/NanoBrowser"
BUGTRACKER = "https://github.com/DanielMartensson/NanoBrowser/issues"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=19b5c44dd600b05d43b5bb78ad0bd491"

SRC_URI = "git://github.com/DanielMartensson/NanoBrowser.git;protocol=https;branch=main"
SRCREV = "9e59762688b3a0dfa8d41e4caacfb290603e51fa"

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
# NanoBrowser auto-detects a Vulkan RHI (vulkanHardwareAvailable()) and falls
# back to OpenGL only when Vulkan is absent. The STM32MP257F VeriSilicon ICD
# is wrongly reported as a suitable device, but the Vulkan loader rejects its
# interface (v2 < v5) and cannot export VkImage file descriptors to weston,
# so the Vulkan pipeline aborts. The upstream NANOBROWSER_RENDERER escape
# hatch forces the OpenGL (GCNANO EGL) backend, which works on weston. The
# wrapper only applies it when the user did not set their own renderer.
do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/nanobrowser ${D}${bindir}/nanobrowser-qt
    cat > ${D}${bindir}/nanobrowser <<EOF
#!/bin/sh
# Wrapper for nanobrowser-qt: force the OpenGL RHI backend by default.
#
# On this image the auto-detection picks Vulkan (GCNANO/VeriSilicon ICD), but
# the Vulkan loader rejects the ICD's loader interface (v2 < v5) and it
# cannot export VkImage file descriptors to weston, so the Vulkan pipeline
# aborts. OpenGL (GCNANO EGL) is the working backend on the weston display.
# NANOBROWSER_RENDERER is the upstream override; this only applies it when
# the user has not already chosen a renderer.
[ -z "\${NANOBROWSER_RENDERER}" ] && export NANOBROWSER_RENDERER=opengl
exec ${bindir}/nanobrowser-qt "\$@"
EOF
    chmod 0755 ${D}${bindir}/nanobrowser
}

RDEPENDS:${PN} += " \
    qtbase-plugins \
    qtdeclarative-qmlplugins \
    qtwebengine \
    qtwebengine-qmlplugins \
"