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

SRC_URI = "git://github.com/OpenCloudGaming/OpenNOW.git;protocol=https;branch=dev \
           file://0001-quiet-the-vulkan-and-locale-lines.patch \
           file://0002-anchor-the-launcher-badge-to-its-row.patch \
           file://0003-ask-only-for-the-decoders-and-surfaces-this-machine-has.patch \
           file://0004-drop-the-driver-s-orphaned-queue-report.patch \
           file://0005-select-the-renderer-at-run-time.patch \
           file://0006-let-sigterm-stop-the-application.patch \
           file://0007-leave-the-process-when-the-event-loop-ends.patch \
           file://0008-keep-the-sign-in-card-out-of-the-layer-path.patch \
           file://0009-do-not-let-one-slow-region-block-a-launch.patch \
           file://0010-print-the-native-error-message.patch \
           file://0011-copy-decoded-dmabuf-frames-to-the-cpu-when-the-gpu-ca.patch \
           file://0012-log-presented-frames-and-dump-the-first-copied-frame.patch \
           file://0013-import-linear-nv12-dmabuf-frames-as-vulkan-buffers.patch \
           file://0014-cache-imported-dmabuf-buffers-and-log-the-render-rate.patch \
           file://0015-count-the-frame-notifications-and-allow-a-forced-repaint.patch \
           file://0016-run-the-stream-window-opaque-and-sample-the-video-1-1.patch \
           file://0017-let-the-video-pass-be-switched-while-streaming.patch \
           file://0018-present-decoded-frames-through-a-wayland-surface.patch \
           file://0019-add-opt-in-frame-and-interface-capture-for-development.patch \
           file://0020-describe-the-decoder-buffer-at-its-visible-height.patch \
           file://0021-let-the-stream-window-be-windowed-or-closed-by-the-user.patch \
           file://0023-keep-the-keyboard-f11-and-the-frame-dump-working.patch \
           file://0024-keep-the-video-flowing-while-the-interface-rests.patch \
           file://0025-pull-frames-from-the-interface-thread.patch \
           file://0026-make-the-stream-window-the-default-presentation-path.patch \
           https://ffmpeg.org/releases/ffmpeg-9.0.tar.xz;name=ffmpeg;unpack=0 \
          "
SRCREV = "987ab3da4bd0e5c4e942c94d548c5c6b2cc79707"

# Source for the bundled FFmpeg build (OPENNOW_CARGO_FEATURES = linux-ffmpeg-bundled).
# The build script's default path clones github.com during configure, which a
# package build must not depend on; OPENNOW_FFMPEG_ARCHIVE makes it extract this
# tarball instead and apply the v4l2-request patches to it.
SRC_URI[ffmpeg.sha256sum] = "7f607a00dd0d28a729d5a4811205812eef01cf6ef6155025febb6f36a9062d52"

PV = "1.0.4+git${SRCPV}"
# The dev branch reports 1.0.4 (nightly); the newest stable release is 1.0.3.
# r0.12 leaves the process when the event loop ends instead of running Qt
# teardown, which deadlocks or aborts in the VeriSilicon Vulkan driver.
PKGR = "r0.33"
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
#   - nv-codec-headers: build-time headers for the bundled FFmpeg, see below
#   - Rust>=1.89 / cargo: edition 2024 build (provided by meta-rust-bin)
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
#
# nv-codec-headers is the same kind of build-time-only dependency, but it comes
# from the bundled FFmpeg build rather than from the CMake project. With
# "linux-ffmpeg-bundled" selected, ffmpeg-sys-next's build.rs turns on
# --enable-ffnvcodec/--enable-cuda/--enable-cuvid/--enable-nvdec for linux.
# FFmpeg's configure has "cuda_deps=ffnvcodec" and "enabled ffnvcodec && enable
# cuda", and the whole build runs with --disable-autodetect, so it does not
# fall back to skipping them: without the ffnvcodec headers configure dies with
#
#   ERROR: cuda requested, but not all dependencies are satisfied: ffnvcodec
#
# The headers are all it needs. NVENC/NVDEC are loaded from a driver at run
# time ("ffnvcodec_deps_any=\"libdl\""), so there is no CUDA toolkit, no
# libcuda link and no runtime dependency - the code is simply never reached on
# a board without an NVIDIA GPU. That keeps upstream's bundled feature set
# untouched instead of patching build-nvdec out of the vendored Cargo.toml.
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
    nv-codec-headers \
    rust-1.89.0-host \
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
#     cross-compiles FFmpeg 9.0 from source.
#
#     On aarch64 the bundled build is what carries the V4L2 Request hwaccel
#     patches (vendor/ffmpeg-sys-next/patches/v4l2-request). They teach FFmpeg
#     the stateless V4L2 decoder API, which is the same hardware path the
#     GStreamer element v4l2slh264dec uses, so this is the feature that makes
#     the STM32MP25 VPU usable: the stateful M2M path does not exist on this
#     board. A distribution FFmpeg (6.1 here) has no such hwaccel, so
#     "linux-ffmpeg" cannot replace it.
#
# do_configure:prepend rewrites the list hardcoded in
# opennow-qt/cmake/NativeRuntime.cmake to this value.
OPENNOW_CARGO_FEATURES ?= "linux-ffmpeg-bundled"

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

# toolchain.cmake forces CMAKE_PROGRAM_PATH "/" and CMAKE_FIND_ROOT_PATH_MODE_PROGRAM ONLY;
# find_program therefore ignores the shell PATH and only searches the
# CMAKE_FIND_ROOT_PATH entries. The Rust toolchain lives in a dedicated
# host dir, not in any of those paths, so preset the executables
# explicitly (verified: -D preset short-circuits find_program).
EXTRA_OECMAKE:append = " -DCARGO_EXECUTABLE=${TMPDIR}/rust-1.89.0-host/bin/cargo -DOPENNOW_LICENSE_RUSTC_EXECUTABLE=${TMPDIR}/rust-1.89.0-host/bin/rustc"

# Upstream hardcodes the embedded streamer cargo feature list in
# opennow-qt/cmake/NativeRuntime.cmake to "linux-ffmpeg-bundled,linux-vaapi"
# and appends it to every cargo invocation. It is a source edit rather than a
# -D override because:
#
#   - the list is written as a CMake list element across two lines, so the
#     plain substitution must also handle the empty case: leaving
#     "--features " (empty) in the cargo command makes cargo abort with
#     "none of the selected packages contains these features". The argument
#     has to be removed from the source, not just blanked.
#   - ffmpeg-sys-next's FFMPEG_DIR escape hatch sits behind a
#     CARGO_FEATURE_BUILD check that the bundled feature turns on, so there is
#     no supported way to keep bundled and redirect its source anyway.
#
# Upstream reformatted the feature list at some point: older revisions wrote
# set(OPENNOW_STREAMER_CARGO_FEATURES "linux-ffmpeg-bundled,linux-vaapi")
# (a CACHE STRING), v1.0.2 writes a literal list element inside
# list(APPEND OPENNOW_STREAMER_CARGO_FEATURE_ARGS). The detection below
# switches on which shape is present.
#
# This runs at do_configure, not do_compile: "inherit cmake" configures in
# do_configure and builds in do_compile, and the cargo arguments are baked
# into the custom commands when they are generated during configure.
#
# The checks re-verify the result after rewriting, so upstream reformating the
# list(APPEND ...) block fails at configure with a clear message instead of
# silently shipping the upstream feature list.
do_configure:prepend() {
    # OpenNOW needs Rust 1.89 (edition 2024); the default native toolchain
    # is 1.75. Use the dedicated 1.89 host toolchain. bitbake clears the
    # stale CMake build dir on every do_configure re-run
    # (do_configure[cleandirs] = ${B}), so no manual rm is needed here.
    export PATH="${TMPDIR}/rust-1.89.0-host/bin:${PATH}"
    features_cmake="${S}/opennow-qt/cmake/NativeRuntime.cmake"
    # Restore the pristine source first: a failed configure (a build
    # stopped mid-rewrite) leaves the --features line deleted, so without
    # this every re-run would trip the pre-rewrite check below. checkout
    # is a no-op on an untouched file.
    git -C "${S}" checkout -- opennow-qt/cmake/NativeRuntime.cmake
    streamer_features() {
        if grep -qE -- '^[[:space:]]*set\(OPENNOW_STREAMER_CARGO_FEATURES' "$1"; then
            # Legacy shape: set(OPENNOW_STREAMER_CARGO_FEATURES "<list>")
            sed -n 's/^[[:space:]]*set(OPENNOW_STREAMER_CARGO_FEATURES[[:space:]]*"\([^"]*\)".*/\1/p' "$1"
        else
            # Current shape: literal --features line inside list(APPEND ...)
            sed -n 's/^[[:space:]]*--[[:space:]]*features[[:space:]]*\(.*\)[[:space:]]*)$/\1/p' "$1"
        fi
    }
    current="$(streamer_features "${features_cmake}")"
    if [ "${current}" != "linux-ffmpeg-bundled,linux-vaapi" ]; then
        bbfatal "opennow: expected set(OPENNOW_STREAMER_CARGO_FEATURES \"linux-ffmpeg-bundled,linux-vaapi\") in ${features_cmake}, but found '${current}'. Upstream changed how the streamer cargo features are selected, so this recipe can no longer guarantee which features are used. Re-check do_configure:prepend and OPENNOW_CARGO_FEATURES before building."
    fi

    # Validate before touching the file, so a typo fails at configure with a
    # clear message instead of much later inside cargo. The top-level
    # opennow-streamer crate defines exactly three features, so anything else
    # would make cargo abort with "none of the selected packages contains these
    # features". All three are legitimate machine choices; the default above
    # picks the bundled build because that is the only one with the V4L2 Request
    # hwaccel on aarch64.
    for feature in $(echo "${OPENNOW_CARGO_FEATURES}" | tr ',' ' '); do
        case "${feature}" in
            linux-vaapi|linux-ffmpeg|linux-ffmpeg-bundled)
                ;;
            *)
                bbfatal "opennow: OPENNOW_CARGO_FEATURES requests unknown feature '${feature}'. The top-level opennow-streamer crate only defines linux-vaapi, linux-ffmpeg and linux-ffmpeg-bundled; cargo would reject anything else."
                ;;
        esac
    done

    # Rewrite the feature list in place. For an empty value the --features
    # argument is dropped, but the enclosing list(APPEND ...) call stays valid:
    # in the current shape the closing ")" sits on the same line as the
    # argument, so only the argument text is removed, never the whole line.
    sed -i "s/linux-ffmpeg-bundled,linux-vaapi/${OPENNOW_CARGO_FEATURES}/g" "${features_cmake}"
    if [ -z "${OPENNOW_CARGO_FEATURES}" ]; then
        # Current shape: strip "--features " from the line that also carries the
        # closing ")" of the list(APPEND ...) call.
        sed -i 's|^\([[:space:]]*\)--features[[:space:]]*|\1|' "${features_cmake}"
        # Legacy shape: strip the --features "${var}" prefix and leave
        # list(APPEND ... ) with no items.
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
    if [ -z "${OPENNOW_CARGO_FEATURES}" ] \
        && code_lines "${features_cmake}" | grep -qiE -- '-{1,2}features.*OPENNOW_STREAMER_CARGO_FEATURES'; then
        bbfatal "opennow: ${features_cmake} still passes a cargo --features argument derived from OPENNOW_STREAMER_CARGO_FEATURES after OPENNOW_CARGO_FEATURES was emptied. Cargo rejects an empty feature list, so this would fail the build at do_compile. Upstream most likely reformatted the list(APPEND ...) block; re-check do_configure:prepend before building."
    fi

    # ---------------------------------------------------------------------------
    # Upstream 1.0.2 bug: the nvst-peer-probe rule passes a Cargo feature as
    # a bare word (positionally) instead of --features.
    #
    # add_custom_command(...) for the nvst-peer-probe binary expands to
    #
    #     --package opennow-streamer-transport
    #     sony-peer-probe
    #     --bin nvst-peer-probe
    #
    # cargo rejects this ("unexpected argument 'sony-peer-probe' found").
    # `sony-peer-probe' is a feature of opennow-streamer-transport
    # ([features] sony-peer-probe = []) that the binary requires
    # ([[bin]] required-features = ["sony-peer-probe"]).
    #
    # The git checkout at the top of this function resets NativeRuntime.cmake
    # on every run, so this rewrite is idempotent and convergent. The two
    # checks fail at configure (not deep inside cargo) if the rule was
    # reformatted differently than this recipe assumes.
    if code_lines "${features_cmake}" | grep -qE -- '^[[:space:]]+sony-peer-probe[[:space:]]*$'; then
        sed -i 's|^\([[:space:]]\+\)sony-peer-probe[[:space:]]*$|\1--features sony-peer-probe|' "${features_cmake}"
    fi
    if code_lines "${features_cmake}" | grep -qE -- '^[[:space:]]+sony-peer-probe[[:space:]]*$'; then
        bbfatal "opennow: the bare 'sony-peer-probe' argument is still present in the nvst-peer-probe cargo rule of ${features_cmake} after the rewrite; the sed did not match. Upstream most likely reformatted this rule; re-check do_configure:prepend before building."
    fi
    if ! code_lines "${features_cmake}" | grep -qE -- '[[:space:]]--features[[:space:]]+sony-peer-probe'; then
        bbfatal "opennow: the nvst-peer-probe cargo rule in ${features_cmake} does not pass '--features sony-peer-probe' after the rewrite; the binary requires the sony-peer-probe feature and the bare-feature-word form would abort cargo. Upstream most likely reformatted this rule differently than the patterns above expect; re-check do_configure:prepend before building."
    fi
}
# The Rust builds run triggered from CMake. Give cargo a writable home inside
# the workdir (crates are fetched from crates.io during do_compile, so do_compile
# needs network access unless you vendor them; see the README).
do_compile:prepend() {
    # Host-side cargo build scripts link with a host C compiler (cc),
    # which lives in the build machine /usr/bin. This distro task
    # base PATH never contains /usr/bin (HOSTPATH is not exported for
    # tasks and the uninative sysroot has no compiler), so add it to
    # the single PATH export here: rust-1.89.0-host/bin first, then
    # the base PATH (native sysroot, recipe-sysroot, hosttools), then
    # /usr/bin last for the host tools missing elsewhere
    # (cc, gcc, g++, ld, ar, as).
    # The task run file stores each export expanded and the last one
    # wins, so this must remain ONE export: a second, later line
    # export PATH="${PATH}:/usr/bin" would clobber the rust prefix
    # and make cargo fail with rustc not found.
    export PATH="${TMPDIR}/rust-1.89.0-host/bin:${PATH}:/usr/bin"
    export CARGO_HOME="${HOME}/.cargo"
    # The LAN DNS is intermittently flaky; give cargo a generous retry/timeout
    # window so transient "could not resolve host" failures recover instead of
    # aborting the whole task. Harmless when the cache is already primed.
    export CARGO_NET_RETRY=8
    export CARGO_NET_TIMEOUT=30
    # FFmpeg's configure, run by the vendored ffmpeg-sys-next build script,
    # applies the cross prefix to the pkg-config name too:
    #   configure:5051  pkg_config_default="${cross_prefix}${pkg_config_default}"
    # With --cross-prefix=aarch64-ostl-linux- it therefore looks for
    # "aarch64-ostl-linux-pkg-config". This distro does not create that name:
    # the pkgconfig class exports PKG_CONFIG_LIBDIR / PKG_CONFIG_PATH /
    # PKG_CONFIG_SYSROOT_DIR at the task sysroot (confirmed in
    # temp/run.do_compile) and expects plain "pkg-config", so
    # aarch64-ostl-linux-pkg-config does not exist anywhere in PATH. FFmpeg then
    # takes the configure:5093 branch
    #   if ! $pkg_config --version; then warn "... not found, library
    #   detection may fail."; pkg_config=false
    # and sets pkg_config to the command `false`. Every check_pkg_config from
    # then on fails without a message, so with --disable-autodetect every
    # pkg-config-detected component stays disabled and the explicit
    # --enable-ffnvcodec/--enable-cuda request dies late with the confusing
    #
    #   ERROR: cuda requested, but not all dependencies are satisfied: ffnvcodec
    #
    # (a *sample* failure: vulkan/vaapi/libdrm were being dropped the same way).
    # The wrapper is a plain passthrough - the real pkg-config already honours
    # the exported PKG_CONFIG_* - and it lives in recipe-sysroot-native/usr/bin
    # so the base PATH finds it, next to the toolchain wrappers above.
    pkgcw="${WORKDIR}/recipe-sysroot-native/usr/bin/aarch64-ostl-linux-pkg-config"
    printf '#!/bin/sh\n# opennow-pkgconfig-wrap\nexec pkg-config "$@"\n' > "${pkgcw}"
    chmod +x "${pkgcw}"
    # Bundled FFmpeg: take the release tarball from SRC_URI instead of letting
    # build.rs clone github.com, and hand it the target sysroot so its configure
    # does not fall back to the host one.
    export OPENNOW_FFMPEG_ARCHIVE="${WORKDIR}/ffmpeg-9.0.tar.xz"
    export SYSROOT="${RECIPE_SYSROOT}"
    # FFmpeg's configure takes its install destinations from the environment,
    # and oe-core exports them to every task (see temp/run.do_compile:
    # libdir=/usr/lib, datadir=/usr/share, includedir, bindir, ...). Because the
    # environment wins over configure's own defaults, the bundled FFmpeg wrote
    #
    #   prefix=<...>/out/dist-<patchset>
    #   LIBDIR=$(DESTDIR)/usr/lib
    #   DATADIR=$(DESTDIR)/usr/share
    #
    # into ffbuild/config.mak - the --prefix build.rs passes does not reach the
    # other directories - and then `make install` tried to install into the
    # *host*, as an unprivileged user, after compiling everything:
    #
    #   mkdir: cannot create directory '/usr/share/examples': Permission denied
    #   make: *** [doc/examples/Makefile:48: install-examples] Error 1
    #   install: cannot create regular file '/usr/lib/libavformat.a': Permission denied
    #   make: *** [ffbuild/library.mak:152: install-libavformat-static] Error 1
    #   ... thread 'main' panicked ... "make install failed"
    #
    # Dropping them for this task lets configure derive every destination from
    # that --prefix, which is where ffmpeg-sys-next then looks for the static
    # libraries and headers. prefix itself is left alone: it reaches configure
    # only through build.rs's explicit --prefix=..., and CMake (which also runs
    # in this task) must not see the install prefix move.
    unset libdir shlibdir datadir includedir bindir sbindir libexecdir \
        mandir docdir infodir sysconfdir localstatedir sharedstatedir \
        exec_prefix
    # The vendored ffmpeg-sys-next generates its Rust bindings from the FFmpeg
    # headers it just built, with bindgen, which drives libclang. libclang does
    # not know the cross toolchain. build.rs does pass --sysroot=..., which
    # finds glibc's headers, but glibc's <limits.h> is a #include_next shim that
    # expects the *compiler's* builtin header next on the search path, and GCC's
    # own include directory is not inside the sysroot. libclang stops there with
    #
    #   .../recipe-sysroot/usr/include/limits.h:124:16: fatal error:
    #   'limits.h' file not found
    #   ... panicked at vendor/ffmpeg-sys-next/build.rs:2069:10:
    #   Unable to generate bindings
    #
    # BINDGEN_EXTRA_CLANG_ARGS is bindgen's documented hook for clang arguments
    # that are appended regardless of what the crate's build script passes, and
    # nothing in this distro sets it (its cargo.bbclass does not, and this
    # recipe builds Rust through CMake rather than that class). Ask the cross
    # compiler where its headers are rather than pinning the gcc version, and
    # keep the sysroot at the task's own. The driver here is the real one: this
    # runs before the wrappers further down replace it.
    gccinc="$("${WORKDIR}/recipe-sysroot-native/usr/bin/aarch64-ostl-linux/aarch64-ostl-linux-gcc" -print-file-name=include)"
    export BINDGEN_EXTRA_CLANG_ARGS="--sysroot=${RECIPE_SYSROOT} -I${gccinc}"
    # cc-rs (used by host-side Rust build scripts, e.g. the `ring` crate for
    # the opennow-license-report notices generator) resolves its compiler by
    # checking, in order: CC_<triple> / CC_<triple_underscore> /
    # {TARGET|HOST}_CC / CC.  Here CC is the aarch64 TARGET compiler (correct
    # for the `--target aarch64` build).  But the same task also builds a
    # native x86_64 HOST tool; that build would otherwise fall through to CC
    # (the aarch64 compiler, which rejects x86-only flags such as -m64).
    # Steer host builds at the host compiler via HOST_CC (checked before CC)
    # without touching CC (the target build still uses it).
    export HOST_CC="gcc"
    # Only needed when building for a foreign triple. Derived from
    # OPENNOW_RUST_TARGET so it works for any architecture rather than being
    # hard-coded to one. With the default empty target, cargo builds for the
    # host triple, which the cross toolchain already matches.
    # aarch64 target-toolchain sysroot wrapper (applied unconditionally):
    # the cross toolchain's default sysroot is dead (/not/exist), so aarch64
    # links fail (ld: cannot find crt1.o/crti.o/crtbegin.o/-lgcc/-lgcc_s/-lc)
    # even when OPENNOW_RUST_TARGET is empty, in which case cargo builds for
    # its own "host" triple and C-compiling deps (cc/clang-sys/bindgen)
    # invoke the aarch64 driver without --sysroot. The wrapper is idempotent:
    # it skips drivers that are already wrapped (by this block or by the
    # conditional block below) and re-invokes the original driver with
    # -mcpu=cortex-a35+crc -mbranch-protection=standard --sysroot.
    # The aarch64 cross toolchain is staged under recipe-sysroot-native
    # with prefix aarch64-ostl-linux (its default sysroot is dead,
    # /not/exist), so every bare driver invocation needs --sysroot.
    # NOTE: TARGET_PREFIX is not defined in the do_compile environment,
    # so a ${TARGET_PREFIX%?}-derived path silently emptied out and the
    # wrapper no-oped; use the literal toolchain prefix.
    tcdir="${WORKDIR}/recipe-sysroot-native/usr/bin/aarch64-ostl-linux"
    for n in gcc g++ c++; do
        pw="${tcdir}/aarch64-ostl-linux-${n}"
        [ -f "${pw}" ] || continue
        [ "$(head -c 9 "${pw}" 2>/dev/null)" = "#!/bin/sh" ] && continue
        mv "${pw}" "${pw}.real"
        printf '#!/bin/sh\n# opennow-sysroot-wrap-uncond\nexec %s/aarch64-ostl-linux-%s.real -mcpu=cortex-a35+crc -mbranch-protection=standard --sysroot="%s/recipe-sysroot" "$@"\n' \
            "${tcdir}" "${n}" "${WORKDIR}" > "${pw}"
        chmod +x "${pw}"
    done

    if [ -n "${OPENNOW_RUST_TARGET}" ]; then
        triple="$(echo "${OPENNOW_RUST_TARGET}" | tr '[:lower:]-' '[:upper:]_')"
        export "CARGO_TARGET_${triple}_LINKER=${TARGET_PREFIX}gcc"

        # Target toolchain sysroot fix ------------------------------------------
        # The aarch64 cross toolchain staged in ${WORKDIR}/recipe-sysroot-native
        # has a dead default sysroot (/not/exist), so any bare
        # `aarch64-ostl-linux-gcc`/`g++` invocation cannot find the target
        # libc/crt that live in ${WORKDIR}/recipe-sysroot. Two consumers
        # hit this and neither passes --sysroot on its own:
        #   - rustc's target link: CARGO_TARGET_<TRIPLE>_LINKER (a bare name
        #     resolved via PATH);
        #   - CMake-driven sys crates (e.g. sdl2-sys): CMAKE_C/CXX_COMPILER,
        #     resolved via PATH and cached as a full path in each
        #     CMakeCache.txt with the --sysroot flag dropped.
        # Replace the unversioned C/C++ drivers in place with a wrapper that
        # re-executes the real driver, adding the machine flags and an
        # absolute --sysroot=${WORKDIR}/recipe-sysroot (absolute, so it
        # resolves from any cwd). In place (not a PATH shadow) so every
        # consumer reaches the fix no matter how it names the compiler: the
        # bare name via PATH and the cached full path both resolve to this
        # same file, which is now the wrapper. The unversioned names are
        # target-only, so the host (x86_64) toolchain is never touched.
        # Idempotent: a wrapper's first bytes are the sh shebang; a restored
        # real ELF driver is not, so a re-run or re-stage converges.
        # The aarch64 cross toolchain is staged under recipe-sysroot-native
        # with prefix aarch64-ostl-linux (its default sysroot is dead,
        # /not/exist), so every bare driver invocation needs --sysroot.
        # NOTE: TARGET_PREFIX is not defined in the do_compile environment,
        # so a ${TARGET_PREFIX%?}-derived path silently emptied out and the
        # wrapper no-oped; use the literal toolchain prefix.
        tcdir="${WORKDIR}/recipe-sysroot-native/usr/bin/aarch64-ostl-linux"
        for n in gcc g++ c++; do
            p="${tcdir}/aarch64-ostl-linux-${n}"
            [ -f "${p}" ] || continue
            [ "$(head -c 9 "${p}" 2>/dev/null)" = "#!/bin/sh" ] && continue
            mv "${p}" "${p}.real"
            printf '#!/bin/sh\n# opennow-sysroot-wrap\nexec %s/aarch64-ostl-linux-%s.real -mcpu=cortex-a35+crc -mbranch-protection=standard --sysroot="%s/recipe-sysroot" "$@"\n' \
                "${tcdir}" "${n}" "${WORKDIR}" > "${p}"
            chmod +x "${p}"
        done
    fi
}

# Upstream install(TARGETS opennow-qt ...) plus the Rust artifacts copied next
# to the executable, so the default cmake do_install covers everything.
inherit qt6-cmake pkgconfig mime-xdg
DEPENDS:append = " qtdeclarative-native"

# Reorder PACKAGES so the base package is processed first in the normal
# (50-priority) group. OpenSTLinux's default (meta/conf/bitbake.conf) puts the
# base LAST, so opennow-dev would claim the bare libopennow_streamer_ffi.so
# (soname == itself, no version) via /usr/lib/lib*.so before base runs.
# Base's default FILES pattern is versioned (lib*.so.*), so base also needs
# the explicit claim below to own the bare FFI lib at runtime.
PACKAGES = "opennow opennow-staticdev opennow-dev opennow-doc opennow-locale opennow-src opennow-dbg"
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

# The `opennow` launcher normalises the runtime environment: this image's
# weston runs with XDG_RUNTIME_DIR=/home/weston, which is where its Wayland
# socket lives, so that value has to be preserved rather than replaced, and
# weston's session has LANG=C, which Qt warns about. It exports GPU / Vulkan
# selection (only when configured), and execs the QML application.
# The video backend is not forced here on purpose: the client probes what
# the machine offers and the user picks one in the UI.
do_install:append() {
    install -d ${D}${bindir}
    cat > ${D}${bindir}/opennow <<EOF
#!/bin/sh
# The escapes below are deliberate: this file is generated by a shell heredoc in
# the recipe, so an unescaped \$VARIABLE would be expanded by the *build* shell
# and frozen into the script.
# This image's weston runs with XDG_RUNTIME_DIR=/home/weston and its socket is
# /home/weston/wayland-1 (see /proc/\$(pgrep -x weston)/environ). The systemd
# user runtime dir /run/user/1000 has no wayland-1 socket, so a launcher that
# exported it unconditionally - as this one used to - made the Qt wayland-egl
# plugin abort before the app ever started:
#   Failed to create wl_display (No such file or directory)
#   This application failed to start because no Qt platform plugin could be
#   initialized.
# Keep whatever runtime dir the session provides (a shell started from weston
# has the right one) and only fall back to the user runtime dir when unset.
if [ -z "\${XDG_RUNTIME_DIR}" ] && [ -d /run/user/1000 ]; then
    export XDG_RUNTIME_DIR=/run/user/1000
fi
# Qt's wayland-egl plugin otherwise defaults to wayland-0, but this image's
# weston listens on wayland-1 (wayland-0 in the runtime dir is a stale socket
# from something else). Take the first socket that is actually there rather than
# hard-coding one, so the same launcher works on an image where wayland-0 is
# the real one.
if [ -z "\${WAYLAND_DISPLAY}" ]; then
    for _socket in wayland-1 wayland-0; do
        if [ -S "\${XDG_RUNTIME_DIR}/\${_socket}" ]; then
            export WAYLAND_DISPLAY="\${_socket}"
            break
        fi
    done
fi
# The embedded streamer opens its audio output through PipeWire, and this
# image's ALSA "default" is the PipeWire plugin as well. Both honour
# PIPEWIRE_RUNTIME_DIR when it is set, but the session keeps
# XDG_RUNTIME_DIR=/home/weston for Wayland while the user's PipeWire socket
# lives in /run/user/<uid>. Without this the first stream dies with
#   media-output-unavailable: the PipeWire socket was not found under
#   XDG_RUNTIME_DIR; Alsa is unavailable: Host is down
# Move only the audio clients; the Wayland socket above keeps the session's
# directory.
if [ -z "\${PIPEWIRE_RUNTIME_DIR}" ] && [ -S "/run/user/\$(id -u)/pipewire-0" ]; then
    export PIPEWIRE_RUNTIME_DIR="/run/user/\$(id -u)"
fi
# weston's session starts with LANG unset, and a non-UTF-8 character type makes
# Qt print
#   Detected locale "C" with character encoding "ANSI_X3.4-1968", which is not UTF-8.
#   Qt depends on a UTF-8 locale, but has failed to switch to one.
# C.UTF-8 is not in this image's locale-archive at all (the `locale` tool is not
# even installed), en_US.UTF-8 is, so that is what a UTF-8 session gets here.
case "\${LC_ALL:-\${LANG:-}}" in
    *UTF-8*|*utf8*|*UTF8*) ;;
    *) export LANG=en_US.UTF-8 ;;
esac
# SDL is only here for gamepads, but even SDL_INIT_GAMEPAD opens SDL's video
# driver, which connects to Wayland on its own and then destroys that connection
# while wl_compositor is still attached - libwayland reports it, twice, at every
# start. A dummy video driver changes nothing for a gamepad and keeps the console
# quiet. Both spellings: SDL3 renamed the variable, SDL2's name still works.
export SDL_VIDEODRIVER=dummy
export SDL_VIDEO_DRIVER=dummy
# The UI's click sounds go through QtMultimedia and PulseAudio. Without a
# PULSE_SERVER the client looks in \$XDG_RUNTIME_DIR/pulse/native, but this
# image's pipewire-pulse listens in /run/user/1000, so the first sound effect
# logs
#   warning: PulseAudioService: pa_context_connect() failed
#   critical qt.multimedia.soundeffect: Failed to update audio output.
# Point the client at the socket that is actually there.
if [ -z "\${PULSE_SERVER}" ]; then
    for _pulse in "\${XDG_RUNTIME_DIR}/pulse/native" "/run/user/\$(id -u)/pulse/native"; do
        if [ -S "\${_pulse}" ]; then
            export PULSE_SERVER="unix:\${_pulse}"
            break
        fi
    done
fi
# Qt's default wayland plugin cannot create a Vulkan instance, and OpenNOW
# forces the Vulkan RHI backend on Linux; the wayland-egl plugin can, and it is
# the same one NanoBrowser uses on this board.
export QT_QPA_PLATFORM=wayland-egl
# GPU / Vulkan selection (exported only when configured).
[ -n "${OPENNOW_VK_DRIVER_FILES}" ] && export VK_DRIVER_FILES="${OPENNOW_VK_DRIVER_FILES}"
[ -n "${OPENNOW_VK_ICD_FILENAMES}" ] && export VK_ICD_FILENAMES="${OPENNOW_VK_ICD_FILENAMES}"
[ -n "${OPENNOW_VK_DEVICE_INDEX}" ] && export VK_DEVICE_INDEX="${OPENNOW_VK_DEVICE_INDEX}"
# Qt rendering backend (exported only when configured).
[ -n "${OPENNOW_QT_RHI_BACKEND}" ] && export QT_QUICK_BACKEND="${OPENNOW_QT_RHI_BACKEND}"
exec ${bindir}/opennow-qt "\$@"
EOF
    chmod 0755 ${D}${bindir}/opennow

    # Upstream CMake installs io.github.opencloudgaming.OpenNOW.desktop
    # with Exec=opennow-qt %U, which bypasses the launcher and so loses the
    # LANG / QT_QPA_PLATFORM / GPU selection it sets up. Overwrite it with an
    # entry that goes through the launcher.
    install -d ${D}${datadir}/applications
    cat > ${D}${datadir}/applications/io.github.opencloudgaming.OpenNOW.desktop <<EOF
[Desktop Entry]
Type=Application
Name=OpenNOW
Comment=Controller-first GeForce NOW client
Exec=opennow %U
Icon=io.github.opencloudgaming.OpenNOW
Terminal=false
Categories=Game;Network;
Keywords=GeForce;cloud;gaming;controller;
StartupNotify=true
StartupWMClass=OpenNOW
MimeType=x-scheme-handler/opennow;
EOF
}

FILES:${PN} += "${datadir}/doc/opennow ${datadir}/metainfo"

# The OpenNOW release build ships its binaries and the FFI shared library
# pre-stripped (no debug sections in either the raw cargo output or the
# packaged files). There is no debug info left to strip or to move to a -dbg
# package, so OE's "already-stripped" QA check is expected and harmless;
# skip it (same idiom used for other pre-stripped recipes in this tree).
INSANE_SKIP:${PN} += "already-stripped"

# The "ldflags" QA path test (package_qa_hash_style) is active because LDFLAGS
# carries --hash-style=gnu, and it flags any ELF with a symbol table that lacks
# a GNU_HASH table. The C++ part (opennow-qt) is linked by qmake with
# -Wl,--hash-style=gnu and passes; the Rust parts are linked by rustc's linker
# (lld) which does not emit the GNU hash table that test looks for, so they
# fail it even though the build is correct. Skip exactly this one check for
# the base package (all fatal GNU_HASH messages land in ${PN}).
INSANE_SKIP:${PN} += "ldflags"

# The Rust binaries keep their build directory in the debug info: rustc has no
# -ffile-prefix-map equivalent to the one the C++ part gets from CFLAGS, so
# opennow-core (and the debug file of the acceptance binary) still name TMPDIR.
# The paths are build trivia, not something a debugger needs on the target.
INSANE_SKIP:${PN} += "buildpaths"
INSANE_SKIP:${PN}-dbg += "buildpaths"

# Upstream cmake also installs the OpenNOW desktop icons under
# ${datadir}/icons (hicolor PNGs + OpenNOW.svg). They are runtime data and
# are not claimed by any -dev/-dbg/-src split, so claim them in the base
# package to avoid the [installed-vs-shipped] QA error.
FILES:${PN} += "${datadir}/icons"
# The FFI shared library is a runtime dependency of the base package
# binaries. Upstream installs it into ${libdir}; the OE default split would
# place it in the -dev package, but a runtime .so must not live in -dev
# (QA: dev-elf/ldflags/dev-deps). Claim it explicitly for the base package.
FILES:${PN} += "${libdir}/libopennow_streamer_ffi.so"
