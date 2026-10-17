# YtGst needs the GStreamer Qt6 QML video sink from gst-plugins-good:
#   - element  : qml6glsink
#   - QML type : org.freedesktop.gstreamer.Qt6GLVideoItem
# Enabling the upstream qt6 meson feature (no source patches involved).
PACKAGECONFIG:append = " qt6"

# Upstream 1.22.12 only has a qt5 entry, so this adds the qt6 one.
# ${QT5WAYLANDDEPENDS} resolves to qtwayland when "wayland" is in DISTRO_FEATURES
# and is exactly what upstream puts on the qt5 line. It matters: meson's
# "Wayland windowing" branch (qt-wayland defaults to auto) ends in
#   dependency('qt6', modules : ['WaylandClient'])
# and only defines -DHAVE_QT_WAYLAND when that is found. Without qtwayland in the
# sysroot meson silently falls back to the EGLFS branch, libgstqml6.so is built
# with wayland=0/eglfs=1 and the sink cannot present a frame under Weston.
PACKAGECONFIG[qt6] = "-Dqt6=enabled,-Dqt6=disabled,qtbase qtdeclarative qtbase-native qtdeclarative-native qttools-native ${QT5WAYLANDDEPENDS}"
# extends PATH so meson sees native Qt6 moc uic rcc in the native sysroot usr/libexec
PATH:append = ":${STAGING_LIBEXECDIR_NATIVE}"

FILES:${PN}-qt6 += "${datadir}/qt6/qml/org/freedesktop/gstreamer/Qt6GLVideoItem \
                    ${libdir}/qt6/qml/org/freedesktop/gstreamer/Qt6GLVideoItem"