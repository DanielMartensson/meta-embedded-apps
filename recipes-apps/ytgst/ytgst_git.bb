SUMMARY = "YtGst - Qt6 YouTube client powered by GStreamer and yt-dlp"
DESCRIPTION = "YtGst is a Qt 6 Quick YouTube client. It queries YouTube with \
yt-dlp, streams the video through GStreamer (waylandsink + v4l2slh264dec hardware \
decode offload) and supports downloads, subtitles and playback speed."
HOMEPAGE = "https://github.com/DanielMartensson/YtGst"
BUGTRACKER = "https://github.com/DanielMartensson/YtGst/issues"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=19b5c44dd600b05d43b5bb78ad0bd491"

SRC_URI = "git://github.com/DanielMartensson/YtGst.git;protocol=https;branch=main"
SRCREV = "a5343791f5ef3517c4ad3e6f11efb4402f049ba2"

PV = "0.1+git${SRCPV}"
S = "${WORKDIR}/git"
# PKGR: the PR-server counter (PRAUTO) resets to r0.0 whenever the recipe
# changes; pin it so the deb stays >= the version already installed on the
# boards. r0.47 builds from upstream main alone: every change that used to
# arrive as 0001-0004 is part of the YtGst repository now, so the recipe has no
# patches and no local source. r0.46 fits the control panel and the picture
# window to the output they land on (the panel scale is clamped to the screen
# width, so XL is 2.5x on a 1920 pixel screen and smaller elsewhere) and rewrites
# the README for this design. r0.45 ends the progress bar at the panel's edge and
# makes it 2 px thicker. r0.44 scales the panel with the scene graph alone: the
# supersampling layer multiplied every panel pixel into a 1800x350 texture, the
# GPU ran out of memory and the compositor died. r0.43 shows the strip again
# (the chained read-only binding left it invisible). r0.42 fetches and decodes
# the sound with an explicit chain instead of a playbin: the playbin's buffering
# message held the shared clock and a steady 30 fps dropped to 0 fps while the
# audio caught up. r0.41 gives the panel small, medium, large and extra large
# sizes. r0.40 downloads the resolution the panel shows, as one plain mp4 with
# H.264 and AAC, which needs ffmpeg on the board. r0.39 gives the picture a window of its own, with a frame the application
# draws (title bar with the title, fullscreen and close buttons, drag and resize
# areas). Weston 13's desktop-shell draws no window frames at all - it has no
# xdg-decoration support and no frame code - so every window on this board is
# bare and the sink's own toplevel could neither be moved nor resized. The sink
# now draws into a Wayland sub-surface of the application's window instead: the
# window's wl_surface and the connection behind it go to the sink through a
# GstContext (GstWlDisplayHandleContextType) and the GstVideoOverlay window
# handle, before the first frame, and the picture fills exactly the area inside
# the frame (render-rectangle). The volume slider is horizontal and runs
# leftwards from the icon instead of upwards. The scene graph is back on Vulkan:
# the earlier freeze belonged to qml6glsink/GstGL and is gone with it -
# waylandsink never enters the scene graph. YTGST_RENDERER=opengl still forces
# the old backend. The sound was verified rather than changed: pointed at a file
# with YTGST_AUDIO_SINK the audio branch decodes 44100 Hz stereo at -23 dB mean,
# its sink-input is live and uncorked at 100% in PipeWire, and the chain is the
# one youtube-play.sh uses (audioconvert ! audioresample ! 48 kHz S16LE !
# pulsesink).
# boards. r0.28 holds 10 s of video in the queue between the demuxer and the
# parser: at the default size of about a second an HLS segment hiccup emptied it,
# the sink starved for about 2 s and the stream jumped forward when the segment
# finally arrived (measured: a window of 480 frames arriving with 11.65 s of
# content, where 480 frames at 60 fps is 8 s). With 10 s the content position
# advances by exactly one second a second, in every round. r0.27 puts the sound
# back into the video pipeline. r0.24-r0.26 had
# moved it into a pipeline of its own, to stop it starving the picture - but that
# was a measurement artifact: with an audio branch in the pipeline the pipeline
# can never reach EOS (the audio stream does not end), so every "same-pipeline"
# run hit its timeout and looked stalled, while runs with the audio in a separate
# process ended when the video did. Measured with progressreport, which prints
# how far into the content the picture has got, video alone, video + audio in one
# pipeline (fetched with souphttpsrc or curlhttpsrc) and audio in its own
# pipeline all reach 43 s of content in 45 s of wall clock; libcurl stalls
# exactly like libsoup, so the shared libsoup session was not the mechanism
# either. Two pipelines did cost real sync: the two sinks' positions showed the
# sound running 660 ms behind the picture, because the audio pipeline's own
# PAUSED->PLAYING change (its pulse stream being set up) is not instant. One
# pipeline keeps sound, picture, seeks and base time in step by construction.
# YTGST_NO_AUDIO=1 plays the picture alone, to prove it again. r0.23 stops a
# pipeline state change from blocking the GUI thread (the
# position query is only issued once the pipeline has settled in PLAYING, and
# asks the video sink directly), reports the sink's rate from the streaming
# thread, and defaults the control panel to the OpenGL RHI: the VeriSilicon
# Vulkan driver freezes a Qt Quick Wayland window's GUI thread (and has crashed)
# as soon as the window is exposed or restacked, while OpenGL holds ~58-60 fps
# and the video is drawn by waylandsink either way. r0.22 gives the frameless
# overlay panel its own close button. r0.21 fixes the missing h264parse ->
# decoder link (playback stopped with "reason not-linked" and zero frames), lets
# the play button restart playback, and adds the real waylandsink fullscreen
# toggle plus the fading transparent overlay panel. r0.20 picks the RHI backend
# after the QApplication exists, which is what fixes the Vulkan probe
# (QVulkanInstance::create() crashed before the application object was created).
# r0.19 installs the QML module so engine.loadFromModule("Ytgst", ...) stops
# aborting with "module Ytgst is not installed". r0.18 carries the two-window
# waylandsink design (standalone video window + linked control-panel window) and
# the Vulkan RHI adaptation. r0.17 carries the fix that starts the pipeline on a
# worker thread so the player-window compositor keeps running. r0.11 carries the
# source fixes that used to be applied as a patch.
PKGR = "r0.47"

# Qt 6.8 (Quick/Network/Widgets/WaylandCompositor) and GStreamer core + GL
# library (gstgl); the compositor module is what shows the waylandsink picture
# inside the player window.
#
# gstreamer1.0-plugins-bad carries waylandsink's helper library
# (libgstwayland-1.0, gst/wayland/*.h); wayland carries wayland-client, which
# its headers include. Both are for embedding the picture into the application's
# own window (see src/player.cpp).
DEPENDS = " \
    qtbase \
    qtdeclarative \
    qtwayland \
    gstreamer1.0 \
    gstreamer1.0-plugins-base \
    gstreamer1.0-plugins-bad \
    wayland \
"

# Optional V4L2 stateless hardware decode via v4l2slh264dec when the platform
# ships a codec plugin for it; override per machine otherwise.
YTGST_VIDEO_DECODER ?= "v4l2slh264dec"

EXTRA_OECMAKE:append = " -DYTGST_VIDEO_DECODER=${YTGST_VIDEO_DECODER}"

inherit qt6-cmake pkgconfig
# qtwayland-native: Qt6WaylandScannerTools, which the Qt6WaylandCompositor
# config file requires at configure time.
DEPENDS:append = " qtdeclarative-native qtwayland-native"

# Upstream install(TARGETS ytgst BUNDLE DESTINATION .) would put the binary at
# the install prefix root. Install it cleanly into ${bindir}.
#
# The QML module has to be installed too: main.cpp calls
# engine.loadFromModule("Ytgst", "Main"/"PlayerWindow"), which resolves the URI
# against the QML import path (${libdir}/qml on this distro). qt_add_qml_module
# builds the module under ${B}/Ytgst (qmldir, *.qmltypes, qml/*.qml); install it
# verbatim so the module is found at runtime instead of aborting with
# "module Ytgst is not installed".
do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/ytgst ${D}${bindir}/ytgst

    install -d ${D}${libdir}/qml/Ytgst
    cp -a ${B}/Ytgst/. ${D}${libdir}/qml/Ytgst/
    # cp -a keeps the build user's ownership; hand the module to root so the
    # packages do not carry the host uid (QA host-user-contaminated).
    chown -R root:root ${D}${libdir}/qml/Ytgst
}

# Claim the installed QML module; otherwise do_package fails with
# "installed-vs-shipped" for qmldir, the *.qmltypes and the qml/*.qml files.
FILES:${PN} += "${libdir}/qml/Ytgst"

# The generated ytgst_qml_module_dir_map.qrc embeds the build directory path
# (Qt writes it as the qresource alias target). It is only a build-time map of
# the module directory; the installed files are what the QML engine reads, so
# the leaked path is harmless and the check is skipped.
INSANE_SKIP:${PN} += "buildpaths"

# GStreamer element usage (from YtGst source):
#   waylandsink -> bad, waylandsink feature -> gstreamer1.0-plugins-bad-waylandsink
#                  (the picture, drawn into a Wayland sub-surface of the
#                   application's own window -> no compositor module needed)
#   v4l2slh264dec -> the SoC's stateless H.264 decoder (kernel/VPU, in the image)
#   souphttpsrc / hlsdemux / tsdemux / h264parse / qtdemux / queues
#                -> base + good binary/network plugins
#   audioconvert / audioresample / pulsesink
#                -> base main plugin lib -> gstreamer1.0-plugins-base
# The sink's helper library (libgstwayland-1.0.so.0) comes with the waylandsink
# feature package.
RDEPENDS:${PN} += " \
    qtbase-plugins \
    qtdeclarative-qmlplugins \
    qtwayland-qmlplugins \
    gstreamer1.0 \
    gstreamer1.0-plugins-base \
    gstreamer1.0-plugins-good \
    gstreamer1.0-plugins-bad-waylandsink \
    yt-dlp \
    ffmpeg \
    pulseaudio \
"
