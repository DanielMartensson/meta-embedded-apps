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
applications in each branch. The games and the CloudMatch clients that used to
sit in this layer now live in its sibling layer,
[meta-good-old-games](https://github.com/DanielMartensson/meta-good-old-games):
this layer keeps only the applications.

## Screen dumps of the games

The games of the sibling layer measured on this seat, one dump per game; the
dumps live on that layer's `scarthgap` branch, and every game's own recipe
README carries its full proof round:

| Game | Screen dump |
| --- | --- |
| id's DOOM shareware 1.9 | ![DOOM shareware 1.9](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/doom1-demo.png) |
| Freedoom Phase 2, the free DOOM II episode | ![Freedoom Phase 2](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/doom2-demo.png) |
| id's Heretic shareware episode | ![Heretic episode 1](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/heretic1-demo.png) |
| id/Raven's Hexen demo | ![Hexen demo](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/hexen-demo.png) |
| id/Raven's Hexen II shareware | ![Hexen II shareware](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/hexen2-demo.png) |
| id's Quake 1 shareware | ![Quake 1 shareware](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/quake1-demo.png) |
| id's Quake II demo (3.14) | ![Quake II demo](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/quake2-demo.png) |
| id's Quake III Arena demo (1.11) | ![Quake III Arena demo](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/quake3-demo.png) |
| Duke Nukem 3D Episode One | ![Duke Nukem 3D Episode One](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/duke3d-demo.png) |
| Jazz Jackrabbit 1, Holiday Hare '94 | ![Jazz Jackrabbit 1](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/jazz1-demo.png) |
| LibreQuake, the free Quake data | ![LibreQuake](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/librequake.png) |
| OpenArena 0.8.8 | ![OpenArena](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/openarena.png) |
| id's Unreal Tournament 99 | ![Unreal Tournament 99](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/ut99.png) |
| id's Unreal / Unreal Gold | ![Unreal Gold](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/unreal.png) |
| Dark and Darker (streamed, not downloaded) | ![Dark and Darker](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/dark-darker.png) |
| Destiny 2 (streamed, not downloaded) | ![Destiny 2](https://raw.githubusercontent.com/DanielMartensson/meta-good-old-games/scarthgap/shots/destiny2.png) |

The two streamed games play through the OpenNOWLite CloudMatch client whose
servers are the host's; nothing of either game is downloaded, so their dumps
are captures of the seat watching the stream.

## Quick start example

Assume that you want to use the `scarthgap` branch.

```bash
git checkout scarthgap
```

Then follow the build instructions in that branch's `README.md`.
