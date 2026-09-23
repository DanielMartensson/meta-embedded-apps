# meta-embedded-apps

Yocto meta-layer for embedded applications. This repo
is organized as **one branch per Yocto release**; each branch contains the full
meta-layer for that release series. `main` is only the index.

## Branches

| Branch | Status | Contents |
| --- | --- | --- |
| `scarthgap` | Active | Meta-layer for Yocto `scarthgap` |
| `main` | Index only | This landing page |
| *(next release)* | — | New release branches are added here following the same naming |

## Applications

| Application | Description | Recipe | Branch |
| --- | --- | --- | --- |
| **OpenNOW** | Cloud-gaming client (Qt 6 UI + native Rust streamer, SDL3/Vulkan) | `recipes-apps/opennow` | `scarthgap` |
| **NanoBrowser** | Minimal QtWebEngine/QML browser | `recipes-apps/nanobrowser` | `scarthgap` |
| **YtGst** | YouTube client on GStreamer + yt-dlp (V4L2 hardware decode) | `recipes-apps/ytgst` | `scarthgap` |

Dependency recipes (SDL 3, yt-dlp, GStreamer, Qt6 QML sink etc.) live alongside the
applications in each branch.

## Quick start example

Assume that you want to use `scarthgap` branch.

```bash
git checkout scarthgap
```

Then follow the build instructions in the branch's `README.md`.

## Conventions

- One branch per Yocto release (`scarthgap`, `kirkstone`, …). New releases get a
  new branch based on `main`, never merged back.
- Each release branch carries its own updated layer recipes and dependency
  pins, so builds stay reproducible per release series.
- The latest supported release is listed first in **Branches**.
