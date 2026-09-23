SUMMARY = "OpenNOW - open source cloud gaming client (GeForce NOW style)"
DESCRIPTION = "OpenNOW is an open-source client for cloud gaming built on a Qt 6 \
Quick user interface with a native Rust streaming engine. It targets embedded \
and desktop Linux with a controller-first experience. The recipe builds the \
opennow-qt QML application and the Rust core/streamer binaries. The recipe is \
machine-agnostic: PACKAGECONFIG and the OPENNOW_* variables select the \
hardware-decode backend (VA-API user-space driver) and the GPU/Vulkan driver \
used at runtime."
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
# backend is pointed at the platform video decoder (e.g. a libva-v4l2-request
# backend in front of the platform stateless decoder node /dev/video0).
# Override per machine or distribution.
OPENNOW_LIBVA_DRIVER ?= "v4l2_request"
OPENNOW_LIBVA_DRIVERS_PATH ?= "${libdir}/dri"

# GPU / Vulkan selection: which ICD, and which physical device, the launcher
# hands to the Vulkan loader. Empty values are not exported, letting the
# loader pick the default GPU on the machine. VK_DRIVER_FILES is honoured by
# recent Vulkan loaders; VK_ICD_FILENAMES is the older equivalent.
OPENNOW_VK_DRIVER_FILES ?= ""
OPENNOW_VK_ICD_FILENAMES ?= ""
OPENNOW_VK_DEVICE_INDEX ?= ""

# Qt rendering backend (QSG_RHI_BACKEND / QT_QUICK_BACKEND). Empty means Qt's
# default; set e.g. to "vulkan" to force Vulkan, or "opengl" for an
# OpenGL-based QRhi backend.
OPENNOW_QT_RHI_BACKEND ?= ""

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

# The `opennow` launcher exports the hardware-selection environment and then
# execs the QML application. It is installed when a hardware-decode user-space
# media driver (v4l2-request) is part of the image.
do_install:append() {
    if ${@bb.utils.contains('PACKAGECONFIG', 'v4l2-request', 'true', 'false', d)}; then
        install -d ${D}${bindir}
        cat > ${D}${bindir}/opennow <<EOF
#!/bin/sh
# Video decoder: point the VA-API backend at the platform video decoder.
export LIBVA_DRIVER_NAME="${OPENNOW_LIBVA_DRIVER}"
export LIBVA_DRIVERS_PATH="${OPENNOW_LIBVA_DRIVERS_PATH}"
# GPU / Vulkan selection (exported only when configured).
[ -n "${OPENNOW_VK_DRIVER_FILES}" ] && export VK_DRIVER_FILES="${OPENNOW_VK_DRIVER_FILES}"
[ -n "${OPENNOW_VK_ICD_FILENAMES}" ] && export VK_ICD_FILENAMES="${OPENNOW_VK_ICD_FILENAMES}"
[ -n "${OPENNOW_VK_DEVICE_INDEX}" ] && export VK_DEVICE_INDEX="${OPENNOW_VK_DEVICE_INDEX}"
# Qt rendering backend (exported only when configured).
[ -n "${OPENNOW_QT_RHI_BACKEND}" ] && export QT_QUICK_BACKEND="${OPENNOW_QT_RHI_BACKEND}"
exec ${bindir}/opennow-qt
EOF
        chmod 0755 ${D}${bindir}/opennow
    fi
}

FILES:${PN} += "${datadir}/doc/opennow ${datadir}/metainfo"