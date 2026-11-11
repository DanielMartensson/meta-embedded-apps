SUMMARY = "LibreQuake - the free Quake game data, mounted as the id1 directory"
DESCRIPTION = "The Quake protocol reads its game out of a directory of data \
named after the game, holding pak0.pak beside the maps, and an engine with no \
data of its own can only play what some other package put there. LibreQuake is \
a full free replacement of that data: at this release it ships id1 with pak0.pak \
and pak1.pak rebuilt from free assets, the deathmatch maps as loose .bsp/.lit, \
the music as track02..11.ogg, the progs.dat the multiplayer code runs and the \
config/doc set the engines read. It is data only: there is no engine code in \
here, this recipe's payload is the tree the fteqw launcher points its -basedir \
and -game at, and the README in the fteqw directory tells the round that played \
it. The licensing is the repo's own two documents inside id1: docs/COPYING is \
the BSD-3-Clause text the project puts on the models, textures and sounds, and \
docs/misc-docs/COPYING is the GPL v2 that covers the quakec progs (progs.dat, \
pop.lmp); docs/README-IMPORTANT-LICENCE-INFO inside id1 in the zip spells this \
split out, and there is no one-license file at the repository root, hence the \
combined license and the two checksums below."
HOMEPAGE = "https://github.com/lavenderdotpet/LibreQuake"

# Both texts sit in the unpacked zip under S (${WORKDIR}/full; the license
# check resolves these file:// names against S, the witness being
# license.bbclass's own srcdir = d.getVar('S') at line 132, and without
# LIC_FILES_CHKSUM the task stops with the QA error
#   do_populate_lic: QA Issue: Recipe file fetches files and does not have
#   license file information (LIC_FILES_CHKSUM)
# pinned here). The GPL md5 below is GNU's own v2 text, unchanged.
LICENSE = "BSD-3-Clause & GPL-2.0-only"
LIC_FILES_CHKSUM = "file://id1/docs/COPYING;md5=da773883bf8745d2637b3a191dd24fa5 file://id1/docs/misc-docs/COPYING;md5=94d55d512a9ba36caa9b7df079bae19f"

# Pin: the release assets are addressed by their tag, so pin the tag name and
# let the fetcher verify the asset's sha256 (taken from this release's own
# asset metadata on GitHub: full.zip of the v0.09-beta release, 115678161
# bytes). If the bytes under this tag ever change, do_fetch fails and the
# build refuses instead of shipping something else. The zip itself unpacks to
# ${WORKDIR}/full and holds the whole payload under full/id1/ (72 entries,
# 252557855 bytes unpacked), hence S below.
SRC_URI = "https://github.com/lavenderdotpet/LibreQuake/releases/download/v0.09-beta/full.zip;sha256sum=623e463b35811216244f9ba15e0c45abc1765288650b5e3d36a899b35e4bcf3d"
# The release tag is v0.09-beta, but the deb version must start with a digit:
# dpkg-deb refuses a control file whose Version is 'v0.09-beta-r0.01' with
#   error: parsing file ... 'Version' field value 'v0.09-beta-r0.01':
#   version number does not start with digit
# so the leading v of the tag is dropped here (the tag name itself stays in
# SRC_URI above, that is what the fetcher resolves).
PV = "0.09-beta"

# PKGR pinned like the rest of this layer, so the deb version stays
# comparable across rebuilds.
PKGR = "r0.01"

S = "${WORKDIR}/full"

# The engine is told where the tree is rather than guessing: the fteqw launcher
# this layer installs runs -basedir /usr/share/librequake -game id1, and FTEQW
# then reads id1/pak0.pak under that basedir. The name id1 is what the Quake
# protocol's own game entry asks for, so the tree is installed under its own
# name and no symlink is needed anywhere: an earlier round had /usr/share/lq1
# pointing at this tree and ran -game lq1 instead, which the launcher no
# longer does.
do_install() {
    install -d ${D}/usr/share/librequake
    cp -a ${S}/id1 ${D}/usr/share/librequake/id1
    # cp -a above keeps the zip's own uid (unzip extracted full/id1 as the
    # user running bitbake), which do_package_qa calls host contamination:
    #   /usr/share/librequake/id1/pak0.pak is owned by uid 1000, which is the
    #   same as the user running bitbake
    # the payload is data, so put the tree back under root like every other
    # recipe's install does.
    chown -R root:root ${D}/usr/share/librequake
}
