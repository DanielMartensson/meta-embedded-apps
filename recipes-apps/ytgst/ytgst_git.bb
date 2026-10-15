SUMMARY = "YtGst - Qt6 YouTube client powered by GStreamer and yt-dlp"
DESCRIPTION = "YtGst is a Qt 6 Quick YouTube client. It queries YouTube with \
yt-dlp, streams the video through GStreamer (qml6glsink + v4l2slh264dec hardware \
decode offload) and supports downloads, subtitles and playback speed."
HOMEPAGE = "https://github.com/DanielMartensson/YtGst"
BUGTRACKER = "https://github.com/DanielMartensson/YtGst/issues"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=19b5c44dd600b05d43b5bb78ad0bd491"

SRC_URI = "git://github.com/DanielMartensson/YtGst.git;protocol=https;branch=main"
SRCREV = "6cbd5957961d9682665801026ec9366c85e2776d"

PV = "0.1+git${SRCPV}"
S = "${WORKDIR}/git"

# Real alsasink audio fallback, --watch <URL> CLI, DASH-first format selection.
PATCHES = "patches/ytgst-audio-watch.patch"

# Qt 6.8 (Quick/Network/Widgets) and GStreamer core + GL library (gstgl).
DEPENDS = " \
    qtbase \
    qtdeclarative \
    gstreamer1.0 \
    gstreamer1.0-plugins-base \
"

# Optional V4L2 stateless hardware decode via v4l2slh264dec when the platform
# ships a codec plugin for it; override per machine otherwise.
YTGST_VIDEO_DECODER ?= "v4l2slh264dec"

EXTRA_OECMAKE:append = " -DYTGST_VIDEO_DECODER=${YTGST_VIDEO_DECODER}"

inherit qt6-cmake pkgconfig
DEPENDS:append = " qtdeclarative-native"

# Upstream install(TARGETS ytgst BUNDLE DESTINATION .) would put the binary at
# the install prefix root. Install it cleanly into ${bindir}.
do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/ytgst ${D}${bindir}/ytgst
}

# GStreamer element usage (from YtGst source):
#   qml6glsink  -> good, qml6 feature -> gstreamer1.0-plugins-good-qml6
#   glupload / glcolorconvert / capsfilter / playbin / alsasink
#                -> base main plugin lib -> gstreamer1.0-plugins-base
# (glupload/glcolorconvert were moved from -bad to -base in GStreamer 1.15+;
#  -bad has no gl subpackage in 1.22.12)
RDEPENDS:${PN} += " \
    qtbase-plugins \
    qtdeclarative-qmlplugins \
    gstreamer1.0 \
    gstreamer1.0-plugins-base \
    gstreamer1.0-plugins-good \
    gstreamer1.0-plugins-good-qml6 \
    yt-dlp \
    pulseaudio \
"