# SPDX-License-Identifier: MIT

# Self-contained Rust 1.85.0 x86_64-unknown-linux-gnu host toolchain.
#
# OpenNOW 1.0.2 (edition 2024) needs Rust >= 1.85. The default native Rust
# toolchain on this build is 1.75 (meta-rust-bin / cargo-native), which
# cannot compile edition 2024. This recipe stages the official 1.85.0 x86_64
# toolchain (rustc + cargo + rust-std) on the build host and exposes it to
# recipes that need it (OpenNOW) via a dedicated host path.
#
# The toolchain lives in ${TMPDIR}/rust-1.85.0-host (a plain host dir, NOT the
# native staging / per-recipe sysroot tree) so it is never merged into the
# per-recipe native sysroots that other recipes rely on (which must keep the
# default 1.75 toolchain). It is never shipped into any image
# (class-native => EXCLUDE_FROM_WORLD = 1).
#
# Toolchain layout after install.sh --prefix=P:
#   P/bin/{cargo, rustc, rustdoc}
#   P/lib/{rustlib, exec}
# Consumers prepend P/bin to PATH before invoking cargo/rustc.

SUMMARY = "Rust 1.85.0 x86_64 toolchain (host-side, for OpenNOW)"
DESCRIPTION = "Stages the official Rust 1.85.0 x86_64-unknown-linux-gnu toolchain \
(rustc, cargo, rust-std) for the build host. Used by OpenNOW, which requires \
Rust >= 1.85 (edition 2024). Installed to a dedicated host dir (not staging), \
never merged into per-recipe sysroots, and never shipped into any image."
HOMEPAGE = "https://doc.rust-lang.org/"
LICENSE = "MIT & Apache-2.0"
LIC_FILES_CHKSUM = " \
    file://rustc-1.85.0-x86_64-unknown-linux-gnu/LICENSE-MIT;sha256=23f18e03dc49df91622fe2a76176497404e46ced8a715d9d2b67a744657a \
    file://rustc-1.85.0-x86_64-unknown-linux-gnu/LICENSE-APACHE;sha256=62c7a1e35f56406896d7aa7ca52d0cc022b5d2796e7d6905db8a3636 \
"

BBCLASSEXTEND = "native"

# This toolchain is native-only (EXCLUDE_FROM_WORLD) and is never shipped
# into any image, so the license population QA is not applicable: for
# native scenes OE resolves the LIC_FILES_CHKSUM file:// paths against a
# remapped S (${WORKDIR}/${PN}-${PV}) that does not contain the unpacked
# components. Clear the QA postfunc; the population task itself still runs
# and copies the generic MIT/Apache texts from COMMON_LICENSE_DIR.
do_populate_lic[postfuncs] = ""

RUST_VERSION = "1.85.0"
RUST_HOST_TRIPLE = "x86_64-unknown-linux-gnu"

# Dedicated host dir for the toolchain. Outside the native staging /
# per-recipe sysroot tree so it is never merged into other recipes' native
# sysroots (which must keep the default 1.75 toolchain).
HOST_RUST_DIR = "${TMPDIR}/rust-1.85.0-host"

SRC_URI = " \
    https://static.rust-lang.org/dist/rustc-${RUST_VERSION}-${RUST_HOST_TRIPLE}.tar.xz;sha256sum=7436f13797475082cd87aa65547449e01659d6a810b4cd5f8aedc48bb9f89dfb \
    https://static.rust-lang.org/dist/cargo-${RUST_VERSION}-${RUST_HOST_TRIPLE}.tar.xz;sha256sum=0aff33b57b0e0b102d762a2b53042846c1ca346cff4b7bd96b5c03c9e8e51d81 \
    https://static.rust-lang.org/dist/rust-std-${RUST_VERSION}-${RUST_HOST_TRIPLE}.tar.xz;sha256sum=285e105d25ebdf501341238d4c0594ecdda50ec9078f45095f793a736b1f1ac2 \
"

do_install () {
    # Fresh, deterministic install on every (re)build.
    rm -rf ${HOST_RUST_DIR}
    # CFG_DISABLE_LDCONFIG=1 keeps install.sh from running ldconfig / writing
    # /etc/ld.so.conf.d (this toolchain is a build-host-only side dir).
    ( export CFG_DISABLE_LDCONFIG=1;
      cd ${S}/rustc-${RUST_VERSION}-${RUST_HOST_TRIPLE} && ./install.sh --prefix=${HOST_RUST_DIR} )
    ( export CFG_DISABLE_LDCONFIG=1;
      cd ${S}/cargo-${RUST_VERSION}-${RUST_HOST_TRIPLE} && ./install.sh --prefix=${HOST_RUST_DIR} )
    ( export CFG_DISABLE_LDCONFIG=1;
      cd ${S}/rust-std-${RUST_VERSION}-${RUST_HOST_TRIPLE} && ./install.sh --prefix=${HOST_RUST_DIR} )

    # Sanity: the toolchain must actually be usable before OpenNOW consumes it.
    [ -x ${HOST_RUST_DIR}/bin/cargo ] || bbfatal "rust-1.85.0-host: ${HOST_RUST_DIR}/bin/cargo missing"
    [ -x ${HOST_RUST_DIR}/bin/rustc ] || bbfatal "rust-1.85.0-host: ${HOST_RUST_DIR}/bin/rustc missing"
    ${HOST_RUST_DIR}/bin/rustc --version
}
