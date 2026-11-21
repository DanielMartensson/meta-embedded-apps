# Big Buck Bunny 1080p - the film that makes the player's decode route a proof

## What it is for

This is **not a game and not part of the shelf**. The player above is only an
installation until something is put in front of it, and a 1080p H.264 master is
what makes it a proof: it shows that the `--enable-gst-decode` route carries a
real picture to this board's panel at the seat's own 1920x1080. The film is the
example of that route; nothing about it is a payload this seat has to carry.

## Load it

```bash
bitbake vlc bigbuckbunny
```

It is a payload recipe: one file, no code, no `Depends`. **Do not `dpkg -i` it** -
unpacking it into `/usr/share` needs space the seat's rootfs does not have. The
member is relayed as the file it already is, to the seat's own writable mount, and
verified there against the member's sha256 in [PROOF.md](PROOF.md).

## Use it

To prove the route, relay a **slice** of the master rather than the whole film -
it is the route that has to be true, not the film's length:

```sh
HOME=/home/weston XDG_RUNTIME_DIR=/home/weston WAYLAND_DISPLAY=wayland-1 \
  cvlc --stats --play-and-exit /gamedata/media/bbb-45s.mp4
```

That play carries the film and its sound, and vlc's decode rate is proved by the
wall time against the slice's own length: the 45 s slice runs inside its own 45 s.
This build prints no statistics block, so the seat's video frame rate is counted
from the bytes vlc's `yuv` output writes - one 1920x1080 planar frame is a fixed
size, so bytes are frames - and the film's frames reach the panel in chunks:

```sh
HOME=/home/weston XDG_RUNTIME_DIR=/home/weston WAYLAND_DISPLAY=wayland-1 \
  gst-launch-1.0 -f filesrc location=/tmp/spool.yuv \
  ! rawvideoparse width=1920 height=1080 framerate=24/1 format=i420 \
  ! waylandsink
```

The spool is what the `cvlc --vout yuv` above wrote, so the decode stays vlc's:
`rawvideoparse` only reads raw bytes.

A still of what is on the panel is taken the way every other round on this seat
takes one:

```sh
XDG_PICTURES_DIR=/gamedata/installs/shots/gallery/vlc weston-screenshooter
```

The picture that has to be true is a 1080p one: the seat's desktop is 1920x1080 and
the film's master is 1920x1080, so a frame that fills the screen is the frame at its
own scale, and the capture is one 1920x1080 PNG with the seat's own 32 bottom rows
of panel to crop out - exactly as the game gallery's captures are cropped.

## Files

```
bigbuckbunny.bb   the payload: the carrier's fetch, the install, the two QA answers
README.md         this file: what the film is for, and how the route is proved
PROOF.md          the carrier's and the member's sizes and hashes, the deb, the seat's space
```
