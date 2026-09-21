# meta-embedded-apps

Yocto meta-layer for applications at embedded systems.

## Branch contents

The following applications are built by recipes in that branch:

| Application | Recipe | Branch |
| --- | --- | --- |
| **OpenNOW** – cloud gaming client (Qt 6 Quick UI + native Rust streamer, SDL3/Vulkan) | `recipes-apps/opennow` | `scarthgap` |
| **NanoBrowser** – minimal QtWebEngine/QML browser | `recipes-apps/nanobrowser` | `scarthgap` |
| **YtGst** – Qt 6 YouTube client on GStreamer + yt-dlp (V4L2 hardware decode) | `recipes-apps/ytgst` | `scarthgap` |

Additional recipes for required dependencies (SDL 3, yt-dlp, GStreamer Qt6 QML
sink) also live on `scarthgap`.

| Branch | Contents |
| --- | --- |
| `scarthgap` | The meta-layer: Yocto recipes for OpenNOW, NanoBrowser, YtGst + dependency recipes |
| `main` | This landing page only – no layer code |
