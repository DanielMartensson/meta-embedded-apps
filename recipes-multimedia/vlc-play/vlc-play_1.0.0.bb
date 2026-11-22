# vlc-play - the launcher that puts a film on this seat's panel, at the film's
# own rate, from the media the seat already carries.
#
# Why a launcher at all: VLC 3.0.20 is on the seat and its decode works, but
# none of its window-capable video outputs (egl_wl, gl, gles2, wl_shell, xdg_shell,
# wl_shm, fb, vmem) reaches this board's Weston compositor. Every one of them
# answers "main video output error: video output creation failed" once per
# attempted frame and then "no frame!", because the display half asks weston for
# a parent window and the interface the player runs under - cvlc's "-I dummy" -
# has none to give, and this seat has no /dev/fb* to fall back to. A window that
# vlc owns is a change to layers/meta-watermelon-wine, not to this recipe, so
# this layer's own launcher is what carries the film's pixels to the panel: the
# compositor gets them through gstreamer's waylandsink, and vlc is used only
# where vlc being the decoder is the point, which is the --vlc route.
#
# The decode is chosen from what the file really is rather than from what it is
# called: ffprobe reads the first video stream and the container, so a film whose
# h264 stream sits in an AVI container is not sent to the hardware route.
#
#   h264 in an mp4/MOV-family container - gstreamer's v4l2slh264dec, which is
#   the SoC's own stateless H.264 block (st,stm32mp25-vdec-dec, /dev/video5),
#   presented by waylandsink and heard on the seat's own ALSA sink. One pass
#   over the file, and the 45 s 1080p slice reaches the window with all of its
#   frames in the film's own wall time at 28 % of one core.
#
#   anything else, or --sw - gstreamer's own software chain through one
#   uridecodebin, whose two pads are the film's frames and its sound. Same
#   rate, same frame count, nearly both of the seat's two cores spent on the
#   decode.
#
#   --vlc - cvlc --vout yuv writes the film's frames to a spool on the seat's
#   /tmp and the window presents that spool chunk by chunk, because one
#   1920x1080 planar frame costs w*h*3/2 bytes and /tmp is 885M. This is the
#   route where vlc's own decoder does the work, and it is the slower one.
#
# The seat's H.264 block cannot be reached through uridecodebin: asking that
# element for an h264 stream answers "Internal data stream error" and
# "pipeline doesn't want to preroll", and the two-pad form with that cap never
# ends. That is why the hardware route names its own demuxer, whose pads are
# really called video_0 and audio_0. The counted runs carry a tee branch that
# shrinks every frame to 32x32 GRAY8 and counts it, so the frames the window
# was handed are exact. Every number of every route lives in PROOF.md beside
# this file, together with the failure modes the choice was made against.
#
# The launcher is this layer's own script rather than a fetch, which is why
# there is no upstream pin here to name: the recipe vendors the file and the
# MIT claim is the recipe's, since there is no upstream source tree beside it
# whose license file could be checksummed.
SUMMARY = "vlc-play - play a film on the seat's panel at the film's own rate"
DESCRIPTION = "vlc-play is a shell launcher that puts a film on the seat's \
Weston panel. VLC 3.0.20 on this board hands nothing to the compositor, so \
the picture goes through GStreamer's waylandsink and the decoder is picked \
from what ffprobe reads out of the file: h264 in an mp4/MOV-family container \
goes to the SoC's own stateless H.264 block (v4l2slh264dec), anything else to \
gstreamer's software chain, and --vlc runs vlc's own decoder against a spool. \
The sound goes to the seat's ALSA sink."
SECTION = "multimedia"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI += "file://vlc-play"

# The launcher has no upstream release number of its own; the deb version is
# what the seat's package manager sees, so it gets a plain one and a rebuild
# stays comparable against what a board already carries, the way the film
# payload next door does it.
PV = "1.0.0"

# PKGR pinned as the rest of this layer does, so a rebuild keeps the deb
# version comparable with what a board already carries.
PKGR = "r0.01"

# BitBake's own unpacker puts a file:// source right into WORKDIR, so S is
# WORKDIR itself and the script sits in it, the same shape the yt-dlp recipe
# uses for its fetched single-file program.
S = "${WORKDIR}"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/vlc-play ${D}${bindir}/vlc-play
}

# The path the recipe installs, named rather than left to a default: this is
# how the film payload recipe next door claims its nonstandard /usr/share tree
# (FILES:${PN} += "/usr/share/media/bigbuckbunny"), and the launcher's answer
# is the same habit for a path inside the standard bin.
FILES:${PN} += "${bindir}/vlc-play"

# What the script calls, package by package, which is also what makes the
# launcher add nothing to a seat that already has the media stack: ffprobe is
# ffmpeg's own (dpkg -S /usr/bin/ffprobe answers ffmpeg 6.1.3-r0.0), cvlc is
# vlc's, gst-launch-1.0 is gstreamer1.0's, stat/date/readlink are coreutils'
# (the seat's /usr/bin/stat is the symlink into stat.coreutils, and the tally
# reads GNU's -c form), and the two elements the smooth route needs are the
# two bad-feature split packages of gstreamer1.0-plugins-bad at 1.22.12-r0.0,
# the same two the opennowlite recipe on the games layer names for the same
# reason. plugins-base and plugins-good carry the caps, the conversions, the
# scales, the tee and the demuxers both routes ask for.
RDEPENDS:${PN} += " \
    coreutils \
    vlc \
    ffmpeg \
    gstreamer1.0 \
    gstreamer1.0-plugins-base \
    gstreamer1.0-plugins-good \
    gstreamer1.0-plugins-bad-waylandsink \
    gstreamer1.0-plugins-bad-v4l2codecs \
"
