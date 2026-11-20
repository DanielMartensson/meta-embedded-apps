# VLC on this seat, re-shaped from upstream rather than vendored.
#
# Upstream vlc_3.0.20 already lives in meta-multimedia, and that layer is on
# BASELAYERS, so its SRC_URI (https://get.videolan.org/vlc/3.0.20/vlc-3.0.20.tar.xz
# pinned at adc7285b4d2721cddf40eb5270cada2aaa10a334cb546fd55a06353447ba29b5),
# its ten patch files and its configure skeleton stay where they are: this
# bbappend only fixes what keeps it off this board. Upstream asks
# REQUIRED_DISTRO_FEATURES = "x11" and this seat has no X server at all - its
# desktop is Weston - so the whole recipe is skipped upstream-shape, and the
# player never lands.

# Wayland for the display half: REQUIRED_DISTRO_FEATURES becomes wayland,
# PACKAGECONFIG[wayland] is upstream's own entry (--enable-wayland plus the
# wayland/wayland-native pair it names), and --enable-gles2 opens the GL ES 2
# output path, the context this board's panel really carries: every engine on
# the seat runs vid_gl_context_es 1. Leaving the x11 key out of PACKAGECONFIG
# takes upstream's --without-x --disable-xcb branch, so no xcb enters the
# picture at all.
REQUIRED_DISTRO_FEATURES = "wayland"

# Decode route of this layer: gstreamer. PACKAGECONFIG[gstreamer] is upstream's
# own entry, --enable-gst-decode, and the three dependencies it names
# (gstreamer1.0, gstreamer1.0-plugins-base, gstreamer1.0-plugins-bad) are
# already on the seat, which is why they add nothing at install time: the film
# this layer carries plays through the stack the seat already has.
#
# Everything else upstream ships by default goes off here, and that prunes the
# feature set and the build in the same move: no Qt UI (this is a one-screen
# seat with weston-screenshooter for the stills), no live555/FireWire inputs,
# no DVD playback and its libdvdcss, no samba/upnp/VNC streaming inputs, no
# x264 encoder, no gtk+3 notify - and restating PACKAGECONFIG strong beats
# upstream's ?= default list, so none of those recipes is pulled. fontconfig
# and freetype stay because the menu and the subtitles draw text, png because
# the snapshot filter writes one.
PACKAGECONFIG = "gstreamer fontconfig freetype png wayland"

# DEPENDS restated to the same shape: the unconditional half upstream needs
# (the toolchain bits, dbus, the text shaper, gnutls, tremor for the
# EXTRA_OECONF --enable-tremor, ffmpeg for --enable-avcodec, the ALSA sink
# this layer has used for audio on every engine since the first round) keeps
# its place; the optional half switched off above - lua, libidn, avahi,
# libmodplug, libmtp/libusb1, orc, schroedinger, libsamplerate0, taglib, tiff,
# jpeg - is out of the list, so those recipes are not pulled in.
DEPENDS = "coreutils-native fribidi libtool libgcrypt libgcrypt-native bison-native \
    dbus libxml2 gnutls tremor ffmpeg flac alsa-lib xz"

# --disable-xvideo: upstream enables the Xvideo output module unconditionally,
# and with --without-x above it would look for an X that is not here.
# --disable-lua matches lua being out of DEPENDS. --enable-run-as-root is
# upstream's own line and stays: this seat runs every weston app as root with
# HOME=/home/weston, all the engines included.
EXTRA_OECONF:append = " --enable-gles2 --disable-xvideo --disable-lua"
