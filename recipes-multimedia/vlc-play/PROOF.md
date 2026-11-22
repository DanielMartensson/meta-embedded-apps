# vlc-play - the record behind the recipe

Detail file for `recipes-multimedia/vlc-play/vlc-play_1.0.0.bb`. The recipe's own
comments keep why the launcher exists and what each route is; this file keeps the
build, the deb's shape, the seat's install and every measured number, including
the gstreamer spells that do not work, which is what made the launcher's pipes
the shape they are.

## Why this is its own recipe and not an append of the player

`recipes-multimedia/vlc/vlc_3.0.20.bbappend` could have grown a
`do_install:append` that writes the launcher beside the player, and the round
started out that way in the shell on the seat. It is not what shipped, and the
reason is a documented number rather than a preference: the vlc round's whole
point is the player's deb shape, `363 entries` with every one of the 292 `.so`
files under `/usr/lib/vlc/plugins/`, and a second file installed by that package
moves the count the detail file quotes. A package of its own keeps the player's
deb byte-identical to that record and puts the launcher in a deb whose only file
is the launcher: `dpkg-deb -c` of `vlc-play_1.0.0-r0.01_arm64.deb` lists
`./usr/bin/vlc-play` and nothing else.

What the launcher is licensed as is the recipe's own claim: the script is
written in this layer, so there is no upstream tarball whose `LICENSE` could be
checksummed, and `LIC_FILES_CHKSUM` points at the layer's MIT text the
opennowlite recipe on the games layer checksums the same way.

## What the build produced

`bitbake -p` at the layer's own path parsed the new recipe into the tree -
`Parsing of 3262 .bb files complete (3258 cached, 4 parsed). 5290 targets, 622
skipped, 0 masked, 0 errors` - and `bitbake -s` answers
`vlc-play :1.0.0-r0`, so the pin is the recipe's own `PV = "1.0.0"` and its own
`PKGR = "r0.01"`. `bitbake vlc-play` answered
`Attempted 6099 tasks of which 6078 didn't need to be rerun and all succeeded`
in 30 s of wall time, with **no WARNING and no ERROR line at all** in the round's
log - no `do_package_qa` note either, so a shell script as a package of its own
packages clean.

| Deb | Bytes | md5 | What is in it |
| --- | --- | --- | --- |
| `vlc-play_1.0.0-r0.01_arm64.deb` | 4,004 | `5d612d213e0945d4467db72d1d8dae8b` | one regular file: `./usr/bin/vlc-play` at 9,958 B, mode `0755` |
| `vlc-play-dbg_1.0.0-r0.01_arm64.deb` | 1,108 | `54977104e218ee21bf47b1a6f8300519` | no regular file - there is no ELF to split symbols out of, so the `-dbg` package is the empty shape the base class hands out |
| `vlc-play-dev_1.0.0-r0.01_arm64.deb` | 1,192 | `7e24b4dc38ca18d07b3e43d4dd99839b` | no regular file; its control's `Recommends` are the `-dev` of every runtime piece plus `vlc-play (= 1.0.0-r0.01)` |

The base deb's control is the eight runtime pieces the routes call, and they
became a real `Depends` line rather than a wish:
`coreutils`, `ffmpeg`, `gstreamer1.0`, `gstreamer1.0-plugins-bad-v4l2codecs`,
`gstreamer1.0-plugins-bad-waylandsink`, `gstreamer1.0-plugins-base`,
`gstreamer1.0-plugins-good`, `vlc`. Those names are the seat's own packages read
with `dpkg -S`, which is also why the launcher asks for no new recipe under the
hood: `/usr/bin/ffprobe` answers `ffmpeg` at `6.1.3-r0.0`, `/usr/bin/gst-launch-1.0`
answers `gstreamer1.0` at `1.22.12-r0.0`, `/usr/bin/stat` is the symlink into
`stat.coreutils`, and the two `plugins-bad` subpackages are on the seat at
`1.22.12-r0.0` - the same two split packages the games layer's opennowlite
recipe names, because the two clients need the same two elements.

The file the recipe vendors is `files/vlc-play`, 9,958 B, md5
`0af60ee482a611138aae7f20a5b8b854`.

## What is on the seat

The deb went build host → local machine → seat and then `dpkg -i`, with the
hand-copied script the earlier rounds had put there removed first, so the bytes
on the seat are the package's rather than a copy the package overwrote:

```
Selecting previously unselected package vlc-play.
Unpacking vlc-play (1.0.0-r0.01) ...
Setting up vlc-play (1.0.0-r0.01) ...
rc=0

dpkg -s vlc-play     Status: install ok installed    Version: 1.0.0-r0.01
dpkg -L vlc-play     /usr/bin/vlc-play
md5sum /usr/bin/vlc-play   0af60ee482a611138aae7f20a5b8b854   - the vendored file's own md5
```

So the launcher on the panel is the recipe's file, and the round below ran the
package's script rather than a hand copy.

## The round the seat ran from that package

All counts are the tee branch: every frame is shrunk to 32x32 GRAY8, 1,024 B, and
counted, and `max-lateness=-1` means the sink is never told to drop, so "the
window was handed" is what reached the panel rather than what the decoder read.
The seat's `/tmp` is an 885M tmpfs, and one 1920x1080 planar I420 frame costs
3,110,400 B.

| the run | what the launcher said | measured |
| --- | --- | --- |
| `/gamedata/media/vlc-probe-1080p.mp4` with `--count` | `mov,mp4,m4a,3gp,3g2,mj2, h264 1920 x 1080 @ 30/1, audio none, 12.000000 s`, then `the seat's own H.264 block, one pass over the file` | **360 frames of the clip's 360** handed to the window, 13.05 s of wall time, 56 % of one core, 65,536 kB of rss |
| `/gamedata/media/bbb-45s.mp4` with `--count`, the film's sound on | `mov,mp4,m4a,3gp,3g2,mj2, h264 1920 x 1080 @ 24/1, audio aac, 45.041667 s`, same route line | **1,081 frames of the film's 1,081** handed, 46.08 s against the film's 45.041667 s, 28 % of one core, 124,064 kB of rss |
| the same film with `--sw --count` | `gstreamer's own software decode, its sound on the seat's sink` | **1,081 of 1,081** handed, 46.12 s, 86 % of the seat's two cores, 202,064 kB |
| the same film with `--sw` alone | same route line, no tally branch | 46.21 s, 85 %, 199,392 kB |
| the probe clip with `--vlc --count` | `vlc's own decode, the film's frames spooled and presented`, then a line per chunk | chunks 165 and 175 frames over two 6 s windows, **340 of the clip's 360** handed in 29 s |
| the whole 45 s film with `--vlc --count` | eight chunk lines, 146/152/121/152/99/105/151/74 | **1,000 of the film's 1,081** frames handed over 99.63 s at 104 % - the route that runs vlc's own decoder, kept because it is the slower one |
| the probe clip's own streams in an AVI container (`ffmpeg -c copy`, so the file is h264 in an AVI at 2,654,982 B) | `avi, h264 1920 x 1080 @ 30/1, audio none, 12.000000 s`, then `gstreamer's own software decode` | 360 of 360 handed, 13.07 s, 100 % - the container test is what sent it to the software route, so the choice reads the file rather than its name |

The hardware route is what makes the play smooth: 28 % of one core against the
software route's 86 % of both cores, both carrying the film's whole frame count
inside the film's own wall time. The seat's panel shows it: `/gamedata/installs/shots/vlc-play/pkg-at-10s.png`
2,348,080 B, taken 10 s inside the package's own hardware round, is the film's
1080p frame across the whole panel with the seat's `weston-desktop-shell` bar
under it, and the two captures the earlier round took inside the same route at
+10 s and +30 s (`film-at-10s.png` 2,320,798 B, `film-at-30s.png` 3,340,549 B)
are the hillside and the burrow. They are that directory's, and the launcher owns
them: no game and no game picture sits in this layer.

The launcher's refusals answer rc 1 and say which of the two things went wrong
before they touch a pipeline: `--help` prints `usage: vlc-play [--count] [--sw] [--vlc] film`,
no file prints `no such file: none` plus the usage, an unknown switch prints
`not a switch of this file: --bogus`, and a file whose first video stream `ffprobe`
cannot read (`/etc/hostname`) prints its own empty reading and
`ffprobe does not read a video stream out of this file`. A relative path works,
because the launcher resolves it before it hands the file to the pipelines.

## What the choice is read from, and why the hardware route names its own demuxer

`ffprobe -show_entries format=format_name` answers `mov,mp4,m4a,3gp,3g2,mj2` for
an mp4 - the demuxer family's own name, not the word `mp4` - so the container
test is the globs `*mp4*|*mov*|*mj2*|*3gp*|*3g2*` against that string, and the
codec test is the literal `h264` out of the first video stream. `r_frame_rate`
feeds the `rawvideoparse` framerate of the `--vlc` route and the spool's cost is
`w*h*3/2` bytes per frame, read out of the same probe.

The hardware leg therefore runs `filesrc` and a named `qtdemux`, whose pads are
`video_0` and `audio_0`, rather than a single source element: reaching the SoC's
block through `uridecodebin` does not work on this build.

| the spell that does not work | what the seat answers | |
| --- | --- | --- |
| `uridecodebin name=dec uri=file://… ! video/x-h264 ! h264parse ! v4l2slh264dec …`, one pad, on the mp4 clip | rc 1 in 0.40 s, tally 0: `ERROR: from element …/GstURIDecodeBin:dec/GstDecodeBin:decodebin0/GstQTDemux:qtdemux0: Internal data stream error.` and `ERROR: pipeline doesn't want to preroll.` | the cap asked of the element is one the element's own demuxer will not hand over |
| the same spell on the AVI copy | the same two lines, named against `GstAviDemux:avidemux0` | not one container's fault |
| the same spell with **two** pads, the audio pad to `alsasink`, on the mkv copy of the film and on the mp4 film | rc 124 at a 70 s timeout, 0.0 % cpu, tally 0 | the pipeline waits for a pad it never gets, and nothing is being decoded |
| `playbin uri=file://… video-sink="waylandsink max-lateness=-1"` | rc 0 and the film does play, at 46.04-46.49 s of wall time and 86-92 % - but the tally branch counts **0** | a source element's sink is a property rather than a pipe, so there is no branch point to tee - which is why the counted software leg is an `uridecodebin` and the uncounted one is either |
| `uridecodebin` with its two pads, the video pad capped `video/x-raw` instead, on the mp4 film | rc 0, 46.13 s, 86 %, tally 1,081 | the two-pad form is fine when the video pad asks for raw: it is only the h264 cap that is dead, which is what the fallback leg's shape is |
| asking the seat's ffmpeg for the file the fallback leg needs as a raw-codec AVI: `-c:v raw` | `Unknown encoder 'raw'`, `Error selecting an encoder`, no output file | this seat's ffmpeg carries no `raw` muxer-side encoder and no libvpx; its VP8 encoder is the SoC's `vp8_v4l2m2m`, and a run of it wrote 0 bytes and answered rc 234 with `Nothing was written into output file, because at least one of its streams received no packets.` |
| the file the fallback leg is tested against instead: `ffmpeg -c copy` of the probe clip into an AVI | 2,654,982 B, `ffprobe` answers `avi` with an `h264` stream | and the launcher sends it to the software route, which is the point of the row |

The gstreamer numbers of the hardware leg itself - the block reading the probe
clip in 9.51 s against `avdec_h264`'s 9.58 s at 178 %, and the same chain to the
panel in 12.27 s at 4 % - are in the player's own [../vlc/PROOF.md](../vlc/PROOF.md),
which is where the decode route's plugin counts and the fifo failure live. What
this file adds is the launcher's own shape: the counted and uncounted forms of
both routes, the container test that reads a file rather than its name, and the
`--vlc` route as the spool's chunk lines.
