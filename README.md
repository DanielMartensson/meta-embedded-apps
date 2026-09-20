# meta-embedded-apps

Yocto/OpenEmbedded **meta-layer** that builds the embedded application stack for
the **Watermelon-Wine STM32MP257F** board. The layer is developed on the
[`scarthgap`](https://github.com/DanielMartensson/meta-embedded-apps/tree/scarthgap)
branch and builds three applications directly from their upstream git
repositories — **without any source patches** — plus the dependency recipes
missing from the surrounding BSP layer stack.

## What this is

A BitBake layer (collection `embedded-apps`, release series `scarthgap`) that
wraps existing upstream applications into Yocto recipes for the OpenSTLinux
BSP. It only describes how to fetch, configure and package the apps; upstream
sources themselves are never modified.

## Contents

| Recipe | Purpose |
| --- | --- |
| `recipes-apps/opennow` | OpenNOW – open-source cloud gaming client (Qt 6 Quick UI + native Rust engine, SDL3/Vulkan/libva) |
| `recipes-apps/nanobrowser` | NanoBrowser – minimal QtWebEngine/QML browser |
| `recipes-apps/ytgst` | YtGst – Qt 6 YouTube client on GStreamer + yt-dlp (V4L2 hardware decode) |
| `recipes-multimedia/libsdl3` | SDL 3.x dependency (required by OpenNOW, absent from the stack) |
| `recipes-multimedia/gstreamer` | bbappend enabling the Qt6 QML video sink (`qml6glsink`) |
| `recipes-support/yt-dlp` | yt-dlp runtime tool (used by YtGst) |

The layer also ships a `conf/layer.conf` and full documentation in the
[`scarthgap`](https://github.com/DanielMartensson/meta-embedded-apps/tree/scarthgap)
branch.

## Branches

| Branch | Description |
| --- | --- |
| `scarthgap` | **Active development branch** – the meta-layer itself. Named after the Yocto release series, matching the upstream ST layers. Always base work on this branch. |
| `main` | Landing page only – this README. Kept for GitHub overview; no layer code lives here. |

## Target platforms

| Platform | Status |
| --- | --- |
| Watermelon-Wine board, **STM32MP257F** (STM32MP25 series) | Primary target |
| OpenSTLinux `openstlinux-6.6-yocto-scarthgap-mpu` BSP, Weston | Primary target |
| AArch64 (`cortexa35-ostl-linux`, glibc) | Primary target |
| `aarch64-unknown-linux-gnu` Rust target (OpenNOW) | Recipe-configurable |

Recipe and dependency completeness have been verified against the layer stack;
a full image build on the target BSP is the final verification step.