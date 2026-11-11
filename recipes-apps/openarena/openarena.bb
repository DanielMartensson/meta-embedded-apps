SUMMARY = "OpenArena 0.8.8 - the free arena-shooter data, installed as baseoa"
DESCRIPTION = "The Quake III protocol reads its game out of a directory of \
pk3 archives named after the game, and an engine that speaks that protocol \
still needs someone else to have put those archives there. OpenArena is the \
free stand-alone release of that data: the whole thing is one zip, and the two \
directories inside it are the payload, baseoa with nine pk3s of maps, players, \
weapons, shaders and sounds, and missionpack as one further pk3 of the maps the \
0.8.8 release adds. It is data only: the engine is the fteqw this layer builds, \
whose makefile links the Quake III gamecode into the client it produces rather \
than loading it as a plugin, so nothing here has to carry any code. The rest of \
the zip is what the same release ships for other platforms (the macOS .app \
bundles, the Windows openarena.exe, the readmes and the credits), and this \
recipe installs none of it."
HOMEPAGE = "https://openarena.ws"

# The license is the one the release itself states. readme_088.txt inside the
# zip has a LICENSE section that reads "The program and data are licensed under
# the GNU GPL v2. See the file 'COPYING' for details.", and COPYING is the
# 17992-byte GNU v2 text that sits at the top of the same zip, so its md5 is
# GNU's own: 94d55d512a9ba36caa9b7df079bae19f. This is the whole claim, program
# and data both, and the reason one free download is enough: the assets are
# free-to-use under the same v2 the engine is under, not only free to fetch.
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://COPYING;md5=94d55d512a9ba36caa9b7df079bae19f"

# Pin. openarena.ws hands out this release through a download form whose link
# now 302-redirects through the Internet Archive, so the form is not a stable
# address; the archive's own target is download.tuxfamily.org/openarena/rel/088/
# openarena-0.8.8.zip, which is still served today and answers with the
# release's own metadata (Content-Length 425189255, Last-Modified Feb 2012),
# and the site's file page publishes 395.51 MB with md5
# 9f353d96d7889c377349d692c3905e5b for this very file. The sha256 below was
# taken by fetching that URL and hashing what came down, so a changed asset
# fails do_fetch rather than shipping something else.
SRC_URI = "https://download.tuxfamily.org/openarena/rel/088/openarena-0.8.8.zip;sha256sum=5a8faf7f5b51f351b0a1618c06b6b98a5f1a6758f1d39818de2c87df2a0bac4a"

# The deb version has to start with a digit for dpkg, and the release is named
# 0.8.8, which does, so the version is the release as it is called upstream.
PV = "0.8.8"

# PKGR pinned as the rest of this layer does, so a rebuild keeps the deb
# version comparable with what a board already carries.
PKGR = "r0.01"

# The zip unpacks to a directory named after the release, and the payload
# inside it is two sibling directories, hence S here and the two names below.
S = "${WORKDIR}/openarena-0.8.8"

# The engine is told where the tree is: the launcher this recipe installs runs
# -basedir /usr/share/openarena -game baseoa. baseoa is the directory the
# release itself calls its payload, and unlike the Quake tree there is no
# engine-side game entry whose name has to be matched, because OpenArena is
# not in FTEQW's own list of games: the -quake3 on the same line is what says
# which protocol the tree speaks. missionpack is read by the same client as a
# second game directory, so it is installed beside baseoa rather than merged
# into it, and a map inside it stays addressable by its own name.
#
# The modes are spelled out because the payload arrives as a cp of the
# extracted zip: unzip leaves the directories at 700 and the files at 644
# owned by whoever runs bitbake, a 700 directory is not world-readable and
# do_package_qa calls the uid host contamination, both of which the seat then
# answers with "Failed to open dir /usr/share/openarena/baseoa/".
do_install() {
    install -d ${D}/usr/share/openarena
    cp -a ${S}/baseoa ${D}/usr/share/openarena/baseoa
    cp -a ${S}/missionpack ${D}/usr/share/openarena/missionpack
    chmod 0755 ${D}/usr/share/openarena \
        ${D}/usr/share/openarena/baseoa ${D}/usr/share/openarena/missionpack
    chmod 0644 ${D}/usr/share/openarena/baseoa/* \
        ${D}/usr/share/openarena/missionpack/*
    chown -R root:root ${D}/usr/share/openarena
}

# The launcher. The client it runs is the one the fteqw recipe builds, and the
# two sets here are what this board needs: vid_gl_context_es 1 asks the Vivante
# driver for a GL ES 3.1 context rather than the ES-CM 1.1 one it hands back
# when left alone, and vid_fullscreen 2 keeps the window at the seat's desktop
# size. No pr_ssqc_memsize here: that arena belongs to the Quake-C progs the
# Quake payload ships, and the Quake III gamecode has none.
do_install:append() {
    install -d ${D}${bindir}
    printf '%s\n' \
        '#!/bin/sh' \
        '# Launcher for the OpenArena data this layer installs, on the FTEQW' \
        '# gles client the fteqw recipe builds. OpenArena is not in FTEQW' \
        '# own list of games, so -quake3 is stated by hand and -game names' \
        '# the tree this recipe installed: -basedir /usr/share/openarena' \
        '# -game baseoa. Override with OPENARENA_BASEDIR / OPENARENA_GAME /' \
        '# OPENARENA_ARGS, or pass your own -basedir, -game and -set' \
        '# arguments on the command line, which win.' \
        'basedir=${OPENARENA_BASEDIR:-/usr/share/openarena}' \
        'game=${OPENARENA_GAME:-baseoa}' \
        'fteargs=${OPENARENA_ARGS:--set vid_gl_context_es 1 -set vid_fullscreen 2}' \
        'exec /usr/bin/fteqw-gles -basedir "$basedir" -game "$game" -quake3 -updatesrc "" $fteargs "$@"' \
        > ${D}${bindir}/openarena
    chmod 0755 ${D}${bindir}/openarena
}

# The launcher runs the client the fteqw recipe installs, so that package has
# to be on the board for this one to do anything.
RDEPENDS:${PN} += "fteqw"
