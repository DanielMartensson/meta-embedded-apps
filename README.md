# meta-embedded-apps

Yocto meta-layer for embedded applications. This repo
is organized as **one branch per Yocto release**; each branch contains the full
meta-layer for that release series. `main` is only the index.

## Branches

| Branch | Status | Contents |
| --- | --- | --- |
| `main` | Index only | This landing page |
| `scarthgap` | Active | Meta-layer for Yocto `scarthgap` |
| *(next release)* | — | New release branches are added here following the same naming |

## Applications

| Application | Description | Recipe | Branch |
| --- | --- | --- | --- |
| **Discordo** | Terminal Discord client, written in Go, with its 21 dependencies vendored | `recipes-apps/discordo` | `scarthgap` |
| **NanoBrowser** | Minimal QtWebEngine/QML browser | `recipes-apps/nanobrowser` | `scarthgap` |
| **YtGst** | YouTube client on GStreamer + yt-dlp (V4L2 hardware decode) | `recipes-apps/ytgst` | `scarthgap` |

Dependency recipes (GStreamer, Qt 6, yt-dlp etc.) live alongside the
applications in each branch. The games and the CloudMatch clients are not in
this layer at all: they live in the sibling layer
[meta-good-old-games](https://github.com/DanielMartensson/meta-good-old-games),
and that layer's index page is where the games and their screen dumps are
listed.

## Quick start example

Assume that you want to use the `scarthgap` branch.

```bash
git checkout scarthgap
```

Then follow the build instructions in that branch's `README.md`.
