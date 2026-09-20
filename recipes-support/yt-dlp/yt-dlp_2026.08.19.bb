SUMMARY = "yt-dlp - a youtube-dl fork with additional features and fixes"
DESCRIPTION = "yt-dlp is a command-line program to download videos from YouTube \
and other sites. It is distributed as a self-contained Python zipapp and is used \
at runtime by the YtGst client."
HOMEPAGE = "https://github.com/yt-dlp/yt-dlp"
BUGTRACKER = "https://github.com/yt-dlp/yt-dlp/issues"

LICENSE = "Unlicense"
LIC_FILES_CHKSUM = " \
    file://LICENSE;md5=7246f848faa4e9c9fc0ea91122d6e680 \
"

SRC_URI = " \
    https://github.com/yt-dlp/yt-dlp/releases/download/${PV}/yt-dlp;unpack=0;name=bin \
    https://raw.githubusercontent.com/yt-dlp/yt-dlp/${PV}/LICENSE;name=license \
"
SRC_URI[bin.sha256sum] = "1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6"
SRC_URI[license.sha256sum] = "7e12e5df4bae12cb21581ba157ced20e1986a0508dd10d0e8a4ab9a4cf94e85c"

S = "${WORKDIR}"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/yt-dlp ${D}${bindir}/yt-dlp
}

RDEPENDS:${PN} = " \
    python3 \
    python3-json \
    python3-xml \
"