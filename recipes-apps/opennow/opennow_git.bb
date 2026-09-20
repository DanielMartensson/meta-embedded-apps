SUMMARY = "OpenNOW - open source cloud gaming client (GeForce NOW style)"
DESCRIPTION = "OpenNOW is an open-source client for cloud gaming built on a Qt 6 \
Quick user interface with a native Rust streaming engine. It targets embedded \
and desktop Linux with a controller-first experience. The recipe builds the \
opennow-qt QML application and the Rust core/streamer binaries cross-compiled \
for the STM32MP257F (aarch64)."
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
    libva \
    wayland \
    wayland-protocols \
    wayland-native \
    libdrm \
    udev \
    cargo-native \
"

OPENNOW_RUST_TARGET ?= "aarch64-unknown-linux-gnu"

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
    vulkan-loader \
    libva \
"

FILES:${PN} += "${datadir}/doc/opennow ${datadir}/metainfo"