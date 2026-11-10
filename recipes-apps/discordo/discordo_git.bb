SUMMARY = "Discordo - a Discord client that runs in a terminal, written in Go"
DESCRIPTION = "Discordo is a terminal (TUI) client for Discord: it keeps its own \
account state, renders Discord-flavoured markdown, and shows images to terminals \
that speak the kitty graphics protocol. The UI is tcell/tview, the wire side is \
arikawa, the sign-in token is taken from the -token flag or the keyring. This \
recipe builds the cross-compiled Go binary and installs it next to the seat's \
weston-terminal, which is where the TUI runs."
HOMEPAGE = "https://github.com/ayn2op/discordo"
BUGTRACKER = "https://github.com/ayn2op/discordo/issues"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://src/${GO_IMPORT}/LICENSE;md5=5906994523cad205e26c6f83fd89f886"

# The pin is the newest tree of upstream whose go.mod line still fits this
# board's Go. oe-core ships go_1.22.12 only, and upstream raised its go.mod
# line to `go 1.23.0` at b35aaf42 (17 Aug 2024) and to `go 1.27.0` at
# e70162b2 (21 Aug 2026); from that first bump on, the language level this
# cross tool can compile is not enough and the build dies in do_compile.
# The newest commit still at `go 1.22.4` is the one right before the bump,
# 31905f3e790e63cd0f2366526afe41fb278c226e (2 Jul 2024). Its license file is
# the MIT one checksummed above; upstream's current HEAD has since been
# changed to GPL-3.0, which is why the pin, not HEAD, is what this recipe
# builds. See the README in this directory for the full record.
SRC_URI = "git://github.com/ayn2op/discordo.git;protocol=https;branch=main"
SRCREV = "31905f3e790e63cd0f2366526afe41fb278c226e"
PV = "0.1+git${SRCPV}"

# PKGR: pinned, like every other recipe in this layer, so the build counter
# does not reset and the deb version stays comparable across rebuilds.
PKGR = "r0.01"

# The 22 modules go.mod asks for at this pin are vendored into the recipe, as
# one tarball, and unpacked into the app's own vendor/ directory next to go's
# modules.txt manifest, so the build needs no network. Going without this is
# not an option: bitbake runs do_compile in a sandbox without network, and a
# plain go build that fetches its dependencies dies there with
# "go: github.com/...: network is unreachable"; the tarball below is the
# witness that the dependencies are in the recipe, not on the internet.
# It was made by running `go mod vendor` once at SRCREV with this tree's
# staged go 1.22.12, then tarred with fixed mode, owner and mtime so a
# rebuild of the recipe does not churn the layer. The README in this
# directory lists the 22 modules and the versions that ended up in the
# manifest.
SRC_URI += "file://discordo-vendor-31905f3e.tar.gz"

inherit go-mod

GO_IMPORT = "github.com/ayn2op/discordo"

# Only the app package itself is built. go.bbclass's default
# GO_INSTALL = "${GO_IMPORT}/..." would walk into the vendored modules too
# and drop 22 stray binaries into /usr/bin; they are needed as dependencies
# of the app, not as programs of their own.
GO_INSTALL = "${GO_IMPORT}"

# Provable no-network build: with vendor/ and its modules.txt in place, go
# never consults a proxy, so turning the proxy off makes any accidental
# dependency fetch fail loudly at do_compile rather than reach the internet.
export GOPROXY = "off"

# A file:// archive is unpacked by bitbake in ${WORKDIR} and ignores the
# destsuffix parameter (only the git/hg/npm fetchers honour it), so the
# vendored tree has to be pointed at from where go looks for it: the app's
# own source directory. A symlink is enough, and it is the same trick
# upstream's own go-vendor class uses.
addtask vendor before do_configure after do_unpack
do_vendor() {
    ln -snf ${WORKDIR}/vendor ${S}/src/${GO_IMPORT}/vendor
}

# go.bbclass's go_do_install copies the whole source tree of the built
# module into ${libdir}/go/src for the -dev package; the vendor symlink is
# dropped there so the dev package carries this project's own sources only
# and no dangling link.
do_install:append() {
    rm -f ${D}${libdir}/go/src/${GO_IMPORT}/vendor
}
