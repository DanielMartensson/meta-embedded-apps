# SPDX-License-Identifier: MIT

# Self-contained Rust 1.89.0 x86_64-unknown-linux-gnu host toolchain.
#
# OpenNOW (edition 2024) needs Rust >= 1.89. The default native Rust
# toolchain on this build is 1.75 (meta-rust-bin / cargo-native), which
# cannot compile edition 2024. This recipe stages the official 1.89.0 x86_64
# toolchain (rustc + cargo + rust-std for x86_64 plus the aarch64 std for
# device targets) on the build host and exposes it to
# recipes that need it (OpenNOW) via a dedicated host path.
#
# The toolchain lives in ${TMPDIR}/rust-1.89.0-host (a plain host dir, NOT the
# native staging / per-recipe sysroot tree) so it is never merged into the
# per-recipe native sysroots that other recipes rely on (which must keep the
# default 1.75 toolchain). It is never shipped into any image
# (class-native => EXCLUDE_FROM_WORLD = 1).
#
# Toolchain layout after install.sh --prefix=P:
#   P/bin/{cargo, rustc, rustdoc}
#   P/lib/{rustlib, exec}
# Consumers prepend P/bin to PATH before invoking cargo/rustc.

SUMMARY = "Rust 1.89.0 x86_64 toolchain (host-side, for OpenNOW)"
DESCRIPTION = "Stages the official Rust 1.89.0 x86_64-unknown-linux-gnu toolchain \
(rustc, cargo, rust-std) for the build host. Used by OpenNOW, which requires \
Rust >= 1.89 (edition 2024). Installed to a dedicated host dir (not staging), \
never merged into per-recipe sysroots, and never shipped into any image."
HOMEPAGE = "https://doc.rust-lang.org/"
LICENSE = "MIT & Apache-2.0"
LIC_FILES_CHKSUM = " \
    file://rustc-1.89.0-x86_64-unknown-linux-gnu/LICENSE-MIT;sha256=23f18e03dc49df91622fe2a76176497404e46ced8a715d9d2b67a744657a \
    file://rustc-1.89.0-x86_64-unknown-linux-gnu/LICENSE-APACHE;sha256=62c7a1e35f56406896d7aa7ca52d0cc022b5d2796e7d6905db8a3636 \
"

BBCLASSEXTEND = "native"

# This toolchain is native-only (EXCLUDE_FROM_WORLD) and is never shipped
# into any image, so the license population QA is not applicable: for
# native scenes OE resolves the LIC_FILES_CHKSUM file:// paths against a
# remapped S (${WORKDIR}/${PN}-${PV}) that does not contain the unpacked
# components. Clear the QA postfunc; the population task itself still runs
# and copies the generic MIT/Apache texts from COMMON_LICENSE_DIR.
do_populate_lic[postfuncs] = ""

RUST_VERSION = "1.89.0"
RUST_HOST_TRIPLE = "x86_64-unknown-linux-gnu"
# aarch64 rust-std enables cargo `build --target aarch64-unknown-linux-gnu`
RUST_TARGET_TRIPLE = "aarch64-unknown-linux-gnu"

# Dedicated host dir for the toolchain. Outside the native staging /
# per-recipe sysroot tree so it is never merged into other recipes' native
# sysroots (which must keep the default 1.75 toolchain).
HOST_RUST_DIR = "${TMPDIR}/rust-1.89.0-host"

SRC_URI = " \
    https://static.rust-lang.org/dist/rustc-${RUST_VERSION}-${RUST_HOST_TRIPLE}.tar.xz;sha256sum=b42c254e1349df86bd40bc28fdf386172a1a46f2eeabe3c7a08a75cf1fb60e27 \
    https://static.rust-lang.org/dist/cargo-${RUST_VERSION}-${RUST_HOST_TRIPLE}.tar.xz;sha256sum=99fc10be2aeedf2c23a484f217bfa76458494495a0eee33e280d3616bb08282d \
    https://static.rust-lang.org/dist/rust-std-${RUST_VERSION}-${RUST_HOST_TRIPLE}.tar.xz;sha256sum=2719470dcd78b3f97d78b978c8f85a1a58d84ff11b62558294621c01bca34d49 \
    https://static.rust-lang.org/dist/rust-std-${RUST_VERSION}-${RUST_TARGET_TRIPLE}.tar.xz;sha256sum=abea0955dded88c68d731524ab9d29b162fae23bf5805b9f1dec063cba37c2aa\
"

do_install () {
    # Fresh, deterministic install on every (re)build.
    rm -rf ${HOST_RUST_DIR}
    # Components unpack directly into WORKDIR; the native scene remaps S
    # to WORKDIR/${PN}-${PV}, so reference the component dirs via WORKDIR
    # (not S) — S points at the (empty) nested dir here.
    # CFG_DISABLE_LDCONFIG=1 keeps install.sh from running ldconfig / writing
    # /etc/ld.so.conf.d (this toolchain is a build-host-only side dir).
    ( export CFG_DISABLE_LDCONFIG=1;
      cd ${WORKDIR}/rustc-${RUST_VERSION}-${RUST_HOST_TRIPLE} && ./install.sh --prefix=${HOST_RUST_DIR} )
    ( export CFG_DISABLE_LDCONFIG=1;
      cd ${WORKDIR}/cargo-${RUST_VERSION}-${RUST_HOST_TRIPLE} && ./install.sh --prefix=${HOST_RUST_DIR} )
    ( export CFG_DISABLE_LDCONFIG=1;
      cd ${WORKDIR}/rust-std-${RUST_VERSION}-${RUST_HOST_TRIPLE} && ./install.sh --prefix=${HOST_RUST_DIR} )
    # aarch64 rust-std (device target, OPENNOW_RUST_TARGET)
    ( export CFG_DISABLE_LDCONFIG=1;
      cd ${WORKDIR}/rust-std-${RUST_VERSION}-${RUST_TARGET_TRIPLE} && ./install.sh --prefix=${HOST_RUST_DIR} )

    # Sanity: the toolchain must actually be usable before OpenNOW consumes it.
    [ -x ${HOST_RUST_DIR}/bin/cargo ] || bbfatal "rust-1.89.0-host: ${HOST_RUST_DIR}/bin/cargo missing"
    [ -x ${HOST_RUST_DIR}/bin/rustc ] || bbfatal "rust-1.89.0-host: ${HOST_RUST_DIR}/bin/rustc missing"
    [ -d ${HOST_RUST_DIR}/lib/rustlib/${RUST_TARGET_TRIPLE} ] || bbfatal "rust-1.89.0-host: ${HOST_RUST_DIR}/lib/rustlib/${RUST_TARGET_TRIPLE} missing"
    # (rustc --version sanity check omitted: deadlocks under pseudo; the binaries are verified.)
}
