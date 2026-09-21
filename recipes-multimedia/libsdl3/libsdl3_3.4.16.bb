SUMMARY = "Simple DirectMedia Layer (3.x)"
DESCRIPTION = "Simple DirectMedia Layer is a cross-platform multimedia library \
designed to provide low level access to audio, keyboard, mouse, joystick, 3D \
hardware via OpenGL/OpenGL ES/Vulkan, and 2D video framebuffer."
HOMEPAGE = "https://www.libsdl.org/"
BUGTRACKER = "https://github.com/libsdl-org/SDL/issues/"

SECTION = "libs"

LICENSE = "Zlib & BSD-2-Clause"
LIC_FILES_CHKSUM = " \
    file://LICENSE.txt;md5=036a54229112040a743509a86b30c80c \
    file://src/hidapi/LICENSE.txt;md5=7c3949a631240cb6c31c50f3eb696077 \
    file://src/hidapi/LICENSE-bsd.txt;md5=b5fa085ce0926bb50d0621620a82361f \
"

SRC_URI = "git://github.com/libsdl-org/SDL.git;protocol=https;branch=main"
SRCREV = "67bf94eed520569fa2eb615922547e8f419f49f3"
PV = "3.4.16"

S = "${WORKDIR}/git"

inherit cmake lib_package pkgconfig

# OpenNOW requires the SDL3 CMake package:
#     find_package(SDL3 REQUIRED CONFIG)
# SDL 3.x installs SDL3Config.cmake and SDL3Targets.cmake.
PROVIDES = "virtual/libsdl3"

# Feed the auto-detection in SDL's CMake so the Wayland video driver, Vulkan,
# KMSDRM input/EVDev and audio backends are found in the target sysroot.
DEPENDS = " \
    wayland \
    wayland-native \
    wayland-protocols \
    libxkbcommon \
    libdrm \
    vulkan-loader \
    udev \
"
DEPENDS:append = " ${@bb.utils.contains('DISTRO_FEATURES', 'opengl', 'virtual/libgles2 virtual/egl', '', d)}"
DEPENDS:append = " ${@bb.utils.contains('DISTRO_FEATURES', 'alsa', 'alsa-lib', '', d)}"

EXTRA_OECMAKE = " \
    -DSDL_TEST_LIBRARY=OFF \
    -DSDL_TESTS=OFF \
    -DSDL_INSTALL_TESTS=OFF \
"