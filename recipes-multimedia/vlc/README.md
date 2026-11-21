# VLC - the media player, and how to run it on the seat

## Load it

```bash
bitbake vlc
```

This is a **bbappend of upstream's own recipe**, not a recipe of this layer:
`meta-multimedia` already carries `vlc_3.0.20.bb`, and that layer is one of this
board's `BASELAYERS`, so the seat's VLC is a delta on top of upstream's pin rather
than a copy of it. The direction of the fold is unambiguous: upstream's layer is
priority 5, this layer is priority 10, so this layer's delta sits above it.
`meta-multimedia` therefore has to be on `BBLAYERS`.

What it installs, and the player's body beside it - `vlc`'s own `Depends` names
`libvlc`, so both debs go in together:

```bash
dpkg -i vlc_3.0.20-r0.0_arm64.deb libvlc_3.0.20-r0.0_arm64.deb

/usr/bin/vlc                       the front end, plus the cvlc / nvlc / rvlc wrappers
/usr/lib/vlc/plugins/              the whole plugin tree, in its own categories
```

## Use it

Run **`cvlc`**, not `vlc`: it asks for the `dummy` interface, so nothing tries to
open a UI on a seat whose desktop is a bare Weston session. Plain `vlc` answers
`Running vlc with the default interface. Use 'cvlc' to use vlc without interface.`

The run needs the seat's own Weston environment, as every engine on this seat does:

```sh
HOME=/home/weston XDG_RUNTIME_DIR=/home/weston WAYLAND_DISPLAY=wayland-1 \
  cvlc --stats --play-and-exit /gamedata/media/my-clip.mp4
```

That play is complete but **sound only**: vlc's own window-capable displays
(`gles2`, `egl_wl`, `wl_shell`, `xdg_shell`, `wl_shm`) all refuse on this seat,
so no pixels reach the panel and the player's statistics block is not printed
either (`--stats` answers silence, `--print-stats` answers `unknown option`).
The frame counts of this build come from the bytes its `yuv` video output
writes - one 1920x1080 planar frame is a fixed size, so bytes are frames.

To get the film **on the panel**, hand those bytes to the seat's compositor
through gstreamer's `waylandsink`, one chunk at a time:

```sh
HOME=/home/weston XDG_RUNTIME_DIR=/home/weston WAYLAND_DISPLAY=wayland-1 \
  cvlc --vout yuv --yuv-file=/tmp/spool.yuv --start-time=0 --stop-time=6 \
  --play-and-exit /gamedata/media/bbb-45s.mp4

HOME=/home/weston XDG_RUNTIME_DIR=/home/weston WAYLAND_DISPLAY=wayland-1 \
  gst-launch-1.0 -f filesrc location=/tmp/spool.yuv \
  ! rawvideoparse width=1920 height=1080 framerate=24/1 format=i420 \
  ! waylandsink
```

`rawvideoparse` reads raw bytes, so the decode in that pair is always vlc's.
The chunk is 6 s because the spool sits on the seat's `/tmp`, whose size sets
the chunk; the write leg and the window leg run one after the other, so a
chunk's picture and its sound are not in step - play the first command alone
when the sound has to be in step.

Put a clip on the seat's own writable `/gamedata/media/` rather than in `/usr/share`:
the seat's rootfs has no room for a 1080p master, which is why the film this layer
carries is relayed to `/gamedata` and is not a `dpkg -i` payload
([bigbuckbunny/README.md](bigbuckbunny/README.md)).

## What the switches buy, in one line each

The route this layer turns on is the **GStreamer decode** route: the player
decodes through the GStreamer stack the seat already has rather than through its
own GL or X display halves, which are switched off. `--enable-gles2` and the
`wayland` distro feature are what let the player put its picture on this board's
Weston compositor, and the X half is out because this board has no X server.

Every measured number - the debs, the plugin tree's counts, the `Depends` of the
main deb against the seat's own stack, and the measured 1080p play - is in
[PROOF.md](PROOF.md).

## Files

```
vlc_3.0.20.bbappend   the delta on upstream's recipe: distro features, PACKAGECONFIG, DEPENDS, configure switches
README.md             this file: load and use
PROOF.md              the pin's shape, the debs, the plugin tree, the measured play, failure modes
```
