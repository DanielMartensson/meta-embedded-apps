SUMMARY = "OpenNOW - open source cloud gaming client (GeForce NOW style)"
DESCRIPTION = "OpenNOW is an open-source client for cloud gaming built on a Qt 6 \
Quick user interface with a native Rust streaming engine. It targets embedded \
and desktop Linux with a controller-first experience. The recipe builds the \
opennow-qt QML application and the Rust core/streamer binaries. Hardware \
decoding is selected at runtime by the client (nativeVideoBackend); \
PACKAGECONFIG and the OPENNOW_* variables control what is packaged and which \
decoder/GPU the launcher helper points at."
HOMEPAGE = "https://github.com/OpenCloudGaming/OpenNOW"
BUGTRACKER = "https://github.com/OpenCloudGaming/OpenNOW/issues"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=efa1357149ea0c1589fb5abd29da41b4"

SRC_URI = "git://github.com/OpenCloudGaming/OpenNOW.git;protocol=https;branch=main"
SRCREV = "a7fe40838a56ac0899533dd373b49d85246ab638"

PV = "1.0.1+git${SRCPV}"
S = "${WORKDIR}/git"

# This recipe is machine-agnostic. It builds the Qt application and the Rust
# streamer, and defaults to no optional decode backend, so it builds on any
# Linux machine. Which hardware decoder is usable is a property of the SoC, not
# of this layer: pick it in a *.bbappend next to your BSP, or in the
# distribution's conf. See OPENNOW_CARGO_FEATURES below.

# OpenNOW dependencies (all hard-required by the upstream CMake project):
#   - Qt 6.8+ : Quick, QuickControls2, Gui, Network, Multimedia, Test,
#               ShaderTools plus Qt6::GuiPrivate and Qt6::QuickPrivate
#   - SDL3    : gamepad/controller input (find_package(SDL3 REQUIRED CONFIG))
#   - Vulkan  : mandatory on Linux (find_package(Vulkan REQUIRED))
#   - libva   : build-time only, see the note below
#   - libdrm/udev: build-time only, see the note below
#   - Rust>=1.85 / cargo: edition 2024 build (provided by meta-rust-bin)
#   - wayland-native: wayland-scanner
#
# libva, libdrm and udev are build-time requirements, not runtime ones, unless
# OPENNOW_CARGO_FEATURES selects a backend that needs them. They stay in DEPENDS
# because opennow-qt/cmake/NativeRuntime.cmake demands them unconditionally on
# every Linux build, independent of the cargo feature list:
#   - pkg_check_modules(OPENNOW_VAAPI REQUIRED libva libva-drm) always runs.
#   - pkg_check_modules(OPENNOW_V4L2_REQUEST REQUIRED libdrm libudev) runs when
#     OPENNOW_RUST_TARGET is empty and the processor is aarch64.
# Only the default (empty) aarch64 path matters for the common case, but both
# are covered. RDEPENDS adds libva only when a VA-API backend is selected.
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

# Leave empty by default: Yocto builds with the cross toolchain as the native
# compiler, so cargo can link the host triple without an explicit --target.
# That avoids needing a rust-std for a foreign triple and avoids having to teach
# cargo about a linker. Set this only if you build outside the SDK, in which case
# do_compile:prepend derives the matching CARGO_TARGET_<TRIPLE>_LINKER.
OPENNOW_RUST_TARGET ?= ""

# Extra cargo features for the embedded streamer, comma-separated. Empty is the
# default and is the only value that is correct on every machine, because the
# right answer depends on the video hardware:
#
#   - The top-level opennow-streamer crate defines exactly three features,
#     linux-vaapi, linux-ffmpeg and linux-ffmpeg-bundled. There is no "vulkan"
#     and no "v4l2" feature, and passing either makes cargo abort with "none of
#     the selected packages contains these features". Vulkan comes in through
#     opennow-streamer-platform-linux's default = ["vulkan"], and its
#     src/video/mod.rs declares "mod v4l2;" with no #[cfg] at all, so the
#     stateful V4L2 M2M backend is always compiled and needs no feature.
#   - "linux-vaapi" is for machines that expose a stateless V4L2 codec node
#     (V4L2_CAP_VIDEO_CODEC_STATELESS). It pulls in cros-codecs and reaches the
#     decoder through DRM PRIME, so decoded frames avoid a CPU copy. Not every
#     V4L2 decoder qualifies: stateful memory-to-memory decoders, which are the
#     common case, cannot bind to it. Check the machine before selecting it.
#   - "linux-ffmpeg" enables the FFmpeg decode modes and needs an ffmpeg
#     provided by the distribution. "linux-ffmpeg-bundled" additionally
#     cross-compiles FFmpeg from source, and on aarch64 its build.rs selects
#     the pinned ffmpeg-rpi fork rather than upstream FFmpeg, so it is rejected
#     outright below.
#
# do_configure:prepend rewrites the list hardcoded in
# opennow-qt/cmake/NativeRuntime.cmake to this value.
OPENNOW_CARGO_FEATURES ?= ""

# Selectable runtime hardware-decode support. These flags control what is
# packaged. The client probes Vulkan, Cuda, VaApi, V4l2 and Ffmpeg and reports
# what it found, and the user picks one in the UI, so nothing here selects a
# backend for the user.
PACKAGECONFIG ??= "vulkan"
PACKAGECONFIG[vulkan] = ",,,vulkan-loader,"

# GPU / Vulkan selection: which ICD, and which physical device, the launcher
# hands to the Vulkan loader. Empty values are not exported, letting the loader
# pick the default GPU on the machine. VK_DRIVER_FILES is honoured by recent
# Vulkan loaders; VK_ICD_FILENAMES is the older equivalent.
OPENNOW_VK_DRIVER_FILES ?= ""
OPENNOW_VK_ICD_FILENAMES ?= ""
OPENNOW_VK_DEVICE_INDEX ?= ""

# Qt rendering backend (QSG_RHI_BACKEND / QT_QUICK_BACKEND). Empty means Qt's
# default; set to "vulkan" to force Vulkan, or "opengl" for an OpenGL-based QRhi
# backend. Note that the opennow-qt target requires the Vulkan loader at build
# time regardless of this setting, but the RHI backend used at runtime is
# independent of it.
OPENNOW_QT_RHI_BACKEND ?= ""

OECMAKE_SOURCEPATH = "${S}/opennow-qt"
OECMAKE_BUILD_TYPE = "Release"

EXTRA_OECMAKE:append = " \
    -DOPENNOW_RUST_TARGET=${OPENNOW_RUST_TARGET} \
   "

# Upstream hardcodes the embedded streamer's cargo feature list in
# opennow-qt/cmake/NativeRuntime.cmake to "linux-ffmpeg-bundled,linux-vaapi"
# and appends "--features ${...}" to every cargo invocation. Two details make
# this a source edit rather than a -D override:
#
#   - The set() is marked CACHE STRING, so -D does reach it, but only a
#     non-empty value is useful. An empty value still leaves
#     list(APPEND ... --features "") in place, and cargo rejects that with
#     "none of the selected packages contains these features". The argument has
#     to be removed from the source, not just blanked.
#   - ffmpeg-sys-next's FFMPEG_DIR escape hatch sits behind a
#     CARGO_FEATURE_BUILD check that the bundled feature turns on, so there is
#     no supported way to keep bundled and redirect its source anyway.
#
# This runs at do_configure, not do_compile: "inherit cmake" configures in
# do_configure and builds in do_compile, and the cargo arguments are baked into
# the custom commands when they are generated during configure.
#
# The checks read the value out of the set() call rather than grepping the file.
# Grepping the file is wrong: the comment above the set() names "linux-vaapi" as
# an example of a reduced list, so a whole-file match finds that comment and
# fails the build on an untouched tree.
do_configure:prepend() {
    features_cmake="${S}/opennow-qt/cmake/NativeRuntime.cmake"
    streamer_features() {
        sed -n 's/^[[:space:]]*set(OPENNOW_STREAMER_CARGO_FEATURES[[:space:]]*"\([^"]*\)".*/\1/p' "$1"
    }
    current="$(streamer_features "${features_cmake}")"
    if [ "${current}" != "linux-ffmpeg-bundled,linux-vaapi" ]; then
        bbfatal "opennow: expected set(OPENNOW_STREAMER_CARGO_FEATURES \"linux-ffmpeg-bundled,linux-vaapi\") in ${features_cmake}, but found '${current}'. Upstream changed how the streamer cargo features are selected, so this recipe can no longer guarantee which features are used. Re-check do_configure:prepend and OPENNOW_CARGO_FEATURES before building."
    fi

    # Validate before touching the file, so a typo fails at configure with a
    # clear message instead of much later inside cargo. The top-level
    # opennow-streamer crate defines exactly three features, so anything else
    # would make cargo abort with "none of the selected packages contains these
    # features". linux-ffmpeg-bundled is rejected because no Yocto recipe should
    # cross-compile a third-party FFmpeg fork by default; the other two are
    # legitimate machine choices and are left to the caller.
    for feature in $(echo "${OPENNOW_CARGO_FEATURES}" | tr ',' ' '); do
        case "${feature}" in
            linux-ffmpeg-bundled)
                bbfatal "opennow: OPENNOW_CARGO_FEATURES requests 'linux-ffmpeg-bundled', which this recipe refuses to enable. It cross-compiles FFmpeg from source, and on aarch64 pulls the pinned ffmpeg-rpi fork rather than upstream FFmpeg. Use 'linux-ffmpeg' with a distribution-provided ffmpeg instead, or set OPENNOW_CARGO_FEATURES to empty."
                ;;
            linux-vaapi|linux-ffmpeg)
                ;;
            *)
                bbfatal "opennow: OPENNOW_CARGO_FEATURES requests unknown feature '${feature}'. The top-level opennow-streamer crate only defines linux-vaapi, linux-ffmpeg and linux-ffmpeg-bundled; cargo would reject anything else. The intended default is empty, which builds on any machine."
                ;;
        esac
    done

    # The cache value is rewritten in every case, so the default list leaves the
    # file even when the --features argument is about to be dropped as well.
    sed -i "s/linux-ffmpeg-bundled,linux-vaapi/${OPENNOW_CARGO_FEATURES}/g" "${features_cmake}"
    if [ -z "${OPENNOW_CARGO_FEATURES}" ]; then
        # Drop the --features argument. Leaving it in with an empty value makes
        # cargo abort, and list(APPEND var) with no items is valid CMake that
        # expands to nothing in the custom command.
        sed -i 's|^[[:space:]]*--features "${OPENNOW_STREAMER_CARGO_FEATURES}")$|    )|' "${features_cmake}"
    fi

    current="$(streamer_features "${features_cmake}")"
    if [ -n "${OPENNOW_CARGO_FEATURES}" ] && [ "${current}" != "${OPENNOW_CARGO_FEATURES}" ]; then
        bbfatal "opennow: rewriting the feature list in ${features_cmake} did not take effect; expected '${OPENNOW_CARGO_FEATURES}' but found '${current}'."
    fi

    # Verify on the result, not on the pattern we tried to match. The checks
    # below are deliberately not anchored to the exact upstream spelling: if
    # upstream reformats the --features line, the sed above silently matches
    # nothing and the build would otherwise sail through with the upstream
    # feature list still in effect. Comments are excluded, because the comment
    # above the set() names "linux-vaapi" as an example of a reduced list.
    code_lines() {
        grep -vE '^[[:space:]]*#' "$1"
    }
    if code_lines "${features_cmake}" | grep -q -- "linux-ffmpeg-bundled"; then
        bbfatal "opennow: 'linux-ffmpeg-bundled' is still present in executable CMake in ${features_cmake} after rewriting the feature list; refusing to build. It cross-compiles FFmpeg from source, and on aarch64 pulls the pinned ffmpeg-rpi fork rather than upstream FFmpeg. Upstream most likely reformatted the list(APPEND ...) block; re-check do_configure:prepend before building."
    fi
    if [ -z "${OPENNOW_CARGO_FEATURES}" ] \
        && code_lines "${features_cmake}" | grep -qiE -- '-{1,2}features.*OPENNOW_STREAMER_CARGO_FEATURES'; then
        bbfatal "opennow: ${features_cmake} still passes a cargo --features argument derived from OPENNOW_STREAMER_CARGO_FEATURES after OPENNOW_CARGO_FEATURES was emptied. Cargo rejects an empty feature list, so this would fail the build at do_compile. Upstream most likely reformatted the list(APPEND ...) block; re-check do_configure:prepend before building."
    fi
}

# The Rust builds run triggered from CMake. Give cargo a writable home inside
# the workdir (crates are fetched from crates.io during do_compile, so do_compile
# needs network access unless you vendor them; see the README).
do_compile:prepend() {
    export CARGO_HOME="${WORKDIR}/cargo-home"
    # Only needed when building for a foreign triple. Derived from
    # OPENNOW_RUST_TARGET so it works for any architecture rather than being
    # hard-coded to one. With the default empty target, cargo builds for the
    # host triple, which the cross toolchain already matches.
    if [ -n "${OPENNOW_RUST_TARGET}" ]; then
        triple="$(echo "${OPENNOW_RUST_TARGET}" | tr '[:lower:]-' '[:upper:]_')"
        export "CARGO_TARGET_${triple}_LINKER=${TARGET_PREFIX}gcc"
    fi
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

# A VA-API backend links against libva at runtime, unlike the rest of the
# dependencies above.
RDEPENDS:${PN}:append = " ${@bb.utils.contains('OPENNOW_CARGO_FEATURES', 'linux-vaapi', 'libva', '', d)}"

# The `opennow` launcher only exports display-selection environment and then
# execs the QML application. The video backend is not forced here on purpose:
# the client probes what the machine offers and the user picks one in the UI.
do_install:append() {
    install -d ${D}${bindir}
    cat > ${D}${bindir}/opennow <<EOF
#!/bin/sh
# GPU / Vulkan selection (exported only when configured).
[ -n "${OPENNOW_VK_DRIVER_FILES}" ] && export VK_DRIVER_FILES="${OPENNOW_VK_DRIVER_FILES}"
[ -n "${OPENNOW_VK_ICD_FILENAMES}" ] && export VK_ICD_FILENAMES="${OPENNOW_VK_ICD_FILENAMES}"
[ -n "${OPENNOW_VK_DEVICE_INDEX}" ] && export VK_DEVICE_INDEX="${OPENNOW_VK_DEVICE_INDEX}"
# Qt rendering backend (exported only when configured).
[ -n "${OPENNOW_QT_RHI_BACKEND}" ] && export QT_QUICK_BACKEND="${OPENNOW_QT_RHI_BACKEND}"
exec ${bindir}/opennow-qt
EOF
    chmod 0755 ${D}${bindir}/opennow
}

FILES:${PN} += "${datadir}/doc/opennow ${datadir}/metainfo"
