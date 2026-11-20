# Big Buck Bunny 1080p on this seat: the film VLC plays as the proof that the
# gstreamer decode route carries a real h264 picture to the panel. It is a
# payload like the games of the games layer, not a program: this recipe compiles
# nothing, it pins the fetch and installs one file.
#
# Blender's own download server is the carrier: it publishes the 1080p H.264
# master as a zip (https://download.blender.org/peach/bigbuckbunny_movies/big_buck_
# bunny_1080p_h264.mov.zip, 725,106,348 bytes) whose single member is the film
# itself, big_buck_bunny_1080p_h264.mov, 725,106,140 bytes stored uncompressed
# in the carrier, the member carrying its 2008-05-27 date. The zip's sha256 is
# the SRC_URI pin below; the member's own sha256 is measured and written in
# README.md beside this file, because that is the bytes VLC actually reads on
# the seat. This layer keeps no PROOF.md files: the applications' measurements
# live in the README next to the recipe that owns them, and the film follows
# that rule, while the games layer keeps its big numbers in PROOF.md.
#
# The film is (C) 2008 the Blender Foundation and published under CC BY 3.0
# (https://www.blender.org/support/about-this-film/). There is no source-tree
# license file inside a movie, so the license is pinned here as a LicenseRef,
# exactly the way the games layer pins a shareware/demo WAD.
SUMMARY = "Big Buck Bunny 1080p H.264, the Blender Foundation's open film"
HOMEPAGE = "https://www.blender.org/support/about-this-film/"
SECTION = "multimedia"

LICENSE = "LicenseRef-cc-by-3.0-bigbuckbunny"

# do_create_spdx wants the text of a LicenseRef rather than a name, and the
# games layer hands it one the same way its Unreal Tournament 99 and Unreal
# Gold payloads do (NO_GENERIC_LICENSE[<the name>] = <the file inside the
# source tree>): the note vendored below is that file, so the SPDX document
# carries the CC BY 3.0 conditions instead of giving up on the name.
NO_GENERIC_LICENSE[LicenseRef-cc-by-3.0-bigbuckbunny] = "LICENSE.txt"

# A movie carries no license file, and do_populate_lic wants one to checksum
# against even for a LicenseRef payload: the games layer met the same thing
# with the Hexen II payload, whose installer left no readable notice either,
# and it carried its own note as a file:// next to the recipe. Same here: the
# CC BY 3.0 conditions, with the pointer to Blender's own page, sit in the
# recipe's own files/ directory (files/LICENSE.txt, 657 bytes, md5
# 55b3693b96c288fb290a3ee38f12d018) and LIC_FILES_CHKSUM points at them. The
# fetch log names the path it chose - do_fetch answers
# "Using …/bigbuckbunny/files/LICENSE.txt for LICENSE.txt" and do_populate_lic
# then answers "md5 checksum matched for file://LICENSE.txt" - so the note the
# recipe checksums is this file, not a copy of it somewhere else.
LIC_FILES_CHKSUM = "file://LICENSE.txt;md5=55b3693b96c288fb290a3ee38f12d018"

SRC_URI = "https://download.blender.org/peach/bigbuckbunny_movies/big_buck_bunny_1080p_h264.mov.zip"
SRC_URI[sha256sum] = "402d6625f614631627d518b931865c9c4aefbbf3b75ed0494672e77ff510666d"
SRC_URI += "file://LICENSE.txt"

# The film has no release number of its own; the deb version is what the
# package manager sees, so it gets a plain one and the seat can compare a
# rebuild against what it already carries.
PV = "1.0.0"

# PKGR pinned as the rest of this layer does, so a rebuild keeps the deb
# version comparable with what a board already carries.
PKGR = "r0.01"

# BitBake's own unpacker reads the zip carrier (zip is one of its archive
# kinds), so after do_unpack WORKDIR holds the film member itself, not the
# carrier: 725,106,140 bytes with the 2008-05-27 date the zip entry carries.
# S is therefore WORKDIR itself and the member sits right in it.
S = "${WORKDIR}"

# /usr/share/media is the home of this layer's media payload, beside
# /usr/share/vlc: like the games layer's payloads, the seat moves the tree to
# its writable /gamedata mount after install, where a 725 MB file cannot sit
# on the rootfs anyway - and the chown line is the same reason the game
# payloads have: weston, not root, reads the payload.
do_install() {
    install -d ${D}/usr/share/media/bigbuckbunny
    install -m 0644 ${S}/big_buck_bunny_1080p_h264.mov \
        ${D}/usr/share/media/bigbuckbunny/big_buck_bunny_1080p_h264.mov
    chown -R root:root ${D}/usr/share/media/bigbuckbunny
}

# /usr/share/media is not one of the paths the base class's default FILES:${PN}
# already covers, so do_package fails without the line below and the film never
# reaches a deb. The games layer met the same thing and carries the same shape
# for the DOSBox-X payload tree (FILES:${PN} += "/usr/share/dosbox-x"), and the
# ioquake3 recipe is where the opposite is written down: a recipe whose payload
# is all in bin and lib has nothing to claim.
FILES:${PN} += "/usr/share/media/bigbuckbunny"
