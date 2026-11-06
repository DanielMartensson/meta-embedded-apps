SUMMARY = "OpenNOWLite - a separate Wayland streaming window for GeForce NOW"
DESCRIPTION = "OpenNOWLite is the plan-B client for this board: it opens its \
own Wayland window for the stream instead of hosting the picture in \
opennow-qt, draws it with GStreamer (waylandsink and the SoC's stateless V4L2 \
H.264 decoder) and keeps the seat's keyboard and pointer as a separate input \
pipeline. Stage 1 of this recipe builds that window and that input path and \
proves them with a test pattern; the media path is fed from the native \
streamer of upstream OpenNOW v1.0.4-nightly.1515.1, pinned below, in stage 2."
HOMEPAGE = "https://github.com/OpenCloudGaming/OpenNOW"
BUGTRACKER = "https://github.com/OpenCloudGaming/OpenNOW/issues"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# The upstream pin is the point of this recipe: v1.0.4-nightly.1515.1 is
# b3fe71026d8261d386c6f16fcdeb1596ebb85942. Nothing of the opennow recipe is
# reused: OpenNOWLite has its own source file, its own window, its own patch
# series and its own package, so the two clients install side by side and
# never share a binary or a log line.
SRC_URI = "git://github.com/OpenCloudGaming/OpenNOW.git;protocol=https;branch=dev \
           file://opennow-lite.c \
           file://opennowlite-launch.sh"
SRCREV = "b3fe71026d8261d386c6f16fcdeb1596ebb85942"
PV = "1.0.4+nightly.1515.1+git${SRCPV}"
S = "${WORKDIR}/git"

# The PR-server counter resets to r0.0 whenever the recipe changes; pin it so
# the deb stays >= whatever is installed on the boards.
PKGR = "r0.2"

DEPENDS = "gstreamer1.0 gstreamer1.0-plugins-base gstreamer1.0-plugins-bad wayland wayland-protocols wayland-native"

inherit pkgconfig

# waylandsink draws into the application's own xdg-shell toplevel: the display
# goes in through GstWlDisplayHandleContextType (gstreamer-wayland-1.0) and the
# surface through the GstVideoOverlay window handle, so the picture fills the
# window this program owns and no Qt and no compositor module is involved.
# xdg-shell is generated here from the sysroot's wayland-protocols.
do_compile() {
    wayland-scanner client-header \
        ${STAGING_DATADIR}/wayland-protocols/stable/xdg-shell/xdg-shell.xml \
        ${WORKDIR}/xdg-shell-client-protocol.h
    wayland-scanner private-code \
        ${STAGING_DATADIR}/wayland-protocols/stable/xdg-shell/xdg-shell.xml \
        ${WORKDIR}/xdg-shell-protocol.c
    ${CC} ${CFLAGS} ${CPPFLAGS} -I${WORKDIR} -o opennow-lite \
        ${WORKDIR}/opennow-lite.c ${WORKDIR}/xdg-shell-protocol.c \
        ${LDFLAGS} $(pkg-config --cflags --libs gstreamer-1.0 gstreamer-video-1.0 gstreamer-wayland-1.0 wayland-client)
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 opennow-lite ${D}${bindir}/opennow-lite
    install -m 0755 ${WORKDIR}/opennowlite-launch.sh ${D}${bindir}/opennowlite
}

FILES:${PN} += "${bindir}/opennow-lite ${bindir}/opennowlite"

# Runtime elements: waylandsink lives in the bad feature package that also
# carries libgstwayland-1.0.so.0, videotestsrc/videoconvert in base, and the
# SoC's v4l2slh264dec is already in the image - it is what stage 2 feeds.
RDEPENDS:${PN} += " \
    gstreamer1.0 \
    gstreamer1.0-plugins-base \
    gstreamer1.0-plugins-good \
    gstreamer1.0-plugins-bad-waylandsink \
"
