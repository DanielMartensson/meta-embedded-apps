SUMMARY = "YtGst - Qt6 YouTube client powered by GStreamer and yt-dlp"
DESCRIPTION = "YtGst is a Qt 6 Quick YouTube client. It queries YouTube with \
yt-dlp, streams the video through GStreamer (qml6glsink + v4l2h264dec hardware \
decode on STM32MP2) and supports downloads, subtitles and playback speed."
HOMEPAGE = "https://github.com/DanielMartensson/YtGst"
BUGTRACKER = "https://github.com/DanielMartensson/YtGst/issues"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=19b5c44dd600b05d43b5bb78ad0bd491"

SRC_URI = "git://github.com/DanielMartensson/YtGst.git;protocol=https;branch=main"
SRCREV = "6cbd5957961d9682665801026ec9366c85e2776d"

PV = "0.1+git${SRCPV}"
S = "${WORKDIR}/git"

# Qt 6.8 (Quick/Network/Widgets) and GStreamer core + GL library (gstgl).
DEPENDS = " \
    qtbase \
    qtdeclarative \
    gstreamer1.0 \
    gstreamer1.0-plugins-base \
"

# STM32MP2 has no VA-API driver; H.264 decode comes from the V4L2 stateless
# codec plugin (v4l2h264dec) in the ST gst-plugins-bad fork.
YTGST_VIDEO_DECODER ?= "v4l2h264dec"

EXTRA_OECMAKE:append = " -DYTGST_VIDEO_DECODER=${YTGST_VIDEO_DECODER}"

inherit cmake pkgconfig

# Upstream install(TARGETS ytgst BUNDLE DESTINATION .) would put the binary at
# the install prefix root. Install it cleanly into ${bindir}.
do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/ytgst ${D}${bindir}/ytgst
}

RDEPENDS:${PN} += " \
    qtbase-plugins \
    qtdeclarative-qmlplugins \
    gstreamer1.0 \
    gstreamer1.0-plugins-base \
    gstreamer1.0-plugins-good \
    gstreamer1.0-plugins-good-qt6 \
    gstreamer1.0-plugins-bad \
    gstreamer1.0-gl \
    yt-dlp \
    pulseaudio \
"