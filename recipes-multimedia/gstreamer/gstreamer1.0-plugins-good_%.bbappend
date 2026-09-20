# YtGst needs the GStreamer Qt6 QML video sink from gst-plugins-good:
#   - element  : qml6glsink
#   - QML type : org.freedesktop.gstreamer.Qt6GLVideoItem
# Enabling the upstream qt6 meson feature (no source patches involved).
PACKAGECONFIG:append = " qt6"

PACKAGECONFIG[qt6] = "-Dqt6=enabled,-Dqt6=disabled,qtbase qtdeclarative"

FILES:${PN}-qt6 += "${datadir}/qt6/qml/org/freedesktop/gstreamer/Qt6GLVideoItem \
                    ${libdir}/qt6/qml/org/freedesktop/gstreamer/Qt6GLVideoItem"