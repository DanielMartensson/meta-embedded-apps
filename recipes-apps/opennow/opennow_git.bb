SUMMARY = "OpenNOW - open source cloud gaming client (GeForce NOW style)"
DESCRIPTION = "OpenNOW is an open-source client for cloud gaming built on a Qt 6 \
Quick user interface with a native Rust streaming engine. It targets embedded \
and desktop Linux with a controller-first experience. The recipe builds the \
opennow-qt QML application and the Rust core/streamer binaries. The primary \
target is the STM32MP257F (aarch64) but the recipe is machine-agnostic: \
OPENNOW_RUST_TARGET selects the Rust triple and PACKAGECONFIG controls which \
hardware-decode backends are packaged and how the runtime reaches them."
HOMEPAGE = "https://github.com/OpenCloudGaming/OpenNOW"
BUGTRACKER = "https://github.com/OpenCloudGaming/OpenNOW/issues"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=efa1357149ea0c1589fb5abd29da41b4"

SRC_URI = "git://github.com/OpenCloudGaming/OpenNOW.git;protocol=https;branch=main"
SRCREV = "a7fe40838a56ac0899533dd373b49d85246ab638"

PV = "1.0.1+git${SRCPV}"
S = "${WORKDIR}/git"

# OpenNOW dependencies (all hard-required by the upstream CMake project):
#   - Qt 6.8+ : Quick, QuickControls2, Gui, Network, Multimedia, Test,
#               ShaderTools plus Qt6::GuiPrivate and Qt6::QuickPrivate
#   - SDL3    : gamepad/controller input (find_package(SDL3 REQUIRED CONFIG))
#   - Vulkan  : mandatory on Linux
#   - libva   : VA-API, also provides libva-drm (pkg_config REQUIRED)
#   - libdrm/libudev: V4L2 request API access on aarch64 (REQUIRED)
#   - Rust>=1.85 / cargo: edition 2024 build (provided by meta-rust-bin)
#   - wayland-native: wayland-scanner
DEPENDS = " \
    qtbase \
    qtdeclarative \
    qtmultimedia \
    qtshadertools \
    qtshadertools-native \
    libsdl3 \
    vulkan-loader \
    vulkan-headers \
    libva \
    wayland \
    wayland-protocols \
    wayland-native \
    libdrm \
    udev \
    cargo-native \
"

OPENNOW_RUST_TARGET ?= "aarch64-unknown-linux-gnu"

# Selectable runtime hardware-decode support. Upstream always enables the
# Linux media backends (VA-API, Vulkan, native V4L2) at build time, so these
# flags only control what is packaged and whether the `opennow` launcher
# helper exports the environment needed to reach a platform video decoder.
PACKAGECONFIG ??= "vulkan vaapi v4l2-request"
PACKAGECONFIG[vulkan] = ",,,vulkan-loader,"
PACKAGECONFIG[vaapi] = ",,,libva,"
PACKAGECONFIG[v4l2-request] = ",,,,"

# Driver and search path exported by the `opennow` launcher so the VA-API
# backend is pointed at a V4L2 stateless user-space driver (e.g. a
# libva-v4l2-request backend in front of the STM32MP2 hantro node
# /dev/video0). Override per machine or distribution.
OPENNOW_LIBVA_DRIVER ?= "v4l2_request"
OPENNOW_LIBVA_DRIVERS_PATH ?= "${libdir}/dri"

OECMAKE_SOURCEPATH = "${S}/opennow-qt"
OECMAKE_BUILD_TYPE = "Release"

EXTRA_OECMAKE:append = " -DOPENNOW_RUST_TARGET=${OPENNOW_RUST_TARGET}"

# The Rust builds run triggered from CMake. Give cargo a writable home inside
# the workdir (crates are fetched from crates.io during do_compile).
do_compile:prepend() {
    export CARGO_HOME="${WORKDIR}/cargo-home"
}

# Upstream install(TARGETS opennow-qt ...) plus the Rust artifacts copied next
# to the executable, so the default cmake do_install covers everything.
inherit cmake pkgconfig

RDEPENDS:${PN} += " \
    qtbase-plugins \
    qtdeclarative-qmlplugins \
    qtmultimedia \
    qtmultimedia-qmlplugins \
    qtmultimedia-plugins \
    libsdl3 \
    wayland \
"

# The `opennow` launcher exports the hardware-decode environment and then
# execs the QML application. It is only installed when a V4L2 stateless
# user-space media driver is part of the image.
do_install:append() {
    if ${@bb.utils.contains('PACKAGECONFIG', 'v4l2-request', 'true', 'false', d)}; then
        install -d ${D}${bindir}
        cat > ${D}${bindir}/opennow <<EOF
#!/bin/sh
export LIBVA_DRIVER_NAME="${OPENNOW_LIBVA_DRIVER}"
export LIBVA_DRIVERS_PATH="${OPENNOW_LIBVA_DRIVERS_PATH}"
exec ${bindir}/opennow-qt
EOF
        chmod 0755 ${D}${bindir}/opennow
    fi
}

FILES:${PN} += "${datadir}/doc/opennow ${datadir}/metainfo"