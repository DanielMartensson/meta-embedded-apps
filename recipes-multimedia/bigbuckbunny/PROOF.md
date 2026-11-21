# Big Buck Bunny 1080p - the record behind the payload recipe

Detail file for `recipes-multimedia/bigbuckbunny/bigbuckbunny.bb`: the fetch, the
carrier's and the member's numbers, the license and the two build answers the
payload needs, and the seat's space. What the film is for and how the route is
proved lives in [README.md](README.md).

## What the carrier holds

Blender's own download server is the carrier, and the film's page
([blender.org/support/about-this-film](https://www.blender.org/support/about-this-film/))
names it. What that server publishes today is the zip, not the bare movie:
`big_buck_bunny_1080p_h264.mov.zip` answers, the same name without the `.zip`
404s, which is why the recipe's `SRC_URI` is the zip and
`S = "${WORKDIR}"` - BitBake's own unpacker reads the carrier and leaves the
member in `${WORKDIR}`, so `do_install` never sees a zip to unzip.

| | Bytes | sha256 |
| --- | --- | --- |
| the carrier zip on the server | 725,106,348 | `402d6625f614631627d518b931865c9c4aefbbf3b75ed0494672e77ff510666d` |
| the member `big_buck_bunny_1080p_h264.mov`, stored uncompressed inside it | 725,106,140 | `dc2146a2b1172def56730143ad80cd1825b7fad15f1fc9c23a4e7d01a741ac11` |

The two hashes are what makes the two ends comparable: the carrier's sha256 is
the pin BitBake checks the fetch against, the member's sha256 is what the seat
checks the file it was handed against, and the member is the file VLC reads -
the carrier is not on the seat at all. The 208-byte difference is the zip's own
record of the member.

The dates say why only the byte count and the hash are trustworthy: the member
carries 2008-05-27 inside the zip, the film's release date, while the deb stamps
the same file 2011-04-06, which is this build's `SOURCE_DATE_EPOCH`. A deb's date
says nothing about the film.

## The license, and why the recipe carries a note

The film is (C) 2008 the Blender Foundation and published under Creative Commons
Attribution 3.0, pinned in the recipe as `LicenseRef-cc-by-3.0-bigbuckbunny` - the
same shape the games layer uses for every shareware and demo payload, a LicenseRef
pinned to the rights holder's own words. A movie has no source-tree `COPYING` for
`LIC_FILES_CHKSUM` to checksum against, so the recipe vendors its own note beside
itself: `files/LICENSE.txt`, 657 bytes, md5 `55b3693b96c288fb290a3ee38f12d018`, the
CC BY 3.0 conditions with the pointer to Blender's own page. The games layer
carried the same shape for the Hexen II payload, whose installer left no readable
notice either.

The build asked for exactly this twice, and both answers are in the recipe rather
than skipped:

- `do_package` failed with `Files/directories were installed but not shipped in
  any package: /usr/share/media/bigbuckbunny/big_buck_bunny_1080p_h264.mov`
  → `FILES:${PN} += "/usr/share/media/bigbuckbunny"`. `${datadir}/media` is not
  one of the paths the base class's default `FILES:${PN}` covers; the games
  layer's DOSBox-X payload carries the same shape for its own tree.
- `do_create_spdx` failed with `Cannot find any text for license
  LicenseRef-cc-by-3.0-bigbuckbunny` →
  `NO_GENERIC_LICENSE[LicenseRef-cc-by-3.0-bigbuckbunny] = "LICENSE.txt"`, the
  games layer's own answer for the two Epic `LicenseRef`s. Without it the SPDX
  document gives up on the license name, and the package would ship without the
  conditions a CC BY work has to carry.

## What the build produced

`bigbuckbunny_1.0.0-r0.01_arm64.deb`, 723,526,860 bytes, md5
`f54e5fea10df06119f1c24211de4121c`, Version `1.0.0-r0.01`, six entries - the five
directories down to `/usr/share/media/bigbuckbunny` and one file:

```
-rw-r--r-- root/root 725106140 … ./usr/share/media/bigbuckbunny/big_buck_bunny_1080p_h264.mov
```

One file, no code, no `Depends`: this is a payload recipe, and the deb is the whole
point of it. It is also the reason the seat does not get the film through `dpkg -i`
- unpacking 725,106,140 bytes into `/usr/share` needs space the seat's rootfs does
not have. At the last measurement the rootfs had 231,616 kB free against a 5,436,376
kB-free `/gamedata` mount (`/dev/mmcblk0p11`, 8 GB), which is where every payload
of this seat lives, and is where this one goes: `/gamedata/media/`.

## What the seat's own proof used

The film itself was never staged on the seat: proving the decode route needs the
route, not the film's length, so the round used a slice cut locally and relayed
byte-exact to the seat, then deleted:

| | Bytes | sha256 |
| --- | --- | --- |
| `/home/mint/scratch/bbb-nov20/bbb-45s.mp4`, the local slice | 82,194,359 | `81efa11145a08237eff5bc30da1fa15af9d40f257e09f37df36349d92eaf097d` |
| the same file on the seat, `/gamedata/media/bbb-45s.mp4`, after the relay | 82,194,359 | `81efa11145a08237eff5bc30da1fa15af9d40f257e09f37df36349d92eaf097d` |

The slice is h264 1920x1080 at 24 fps, 1,081 frames over 45.041667 s, with an aac
track; the two hashes matching is what says the relay was honest. A 12 s probe clip
of the same shape was relayed earlier to answer the same question at a smaller size:
`/gamedata/media/vlc-probe-1080p.mp4`, 2,636,572 B, sha256 `00bbbb05eb…a38121`,
h264 1920x1080 at 30 fps, 360 frames over 12 s.

What the slice bought is a route that can be counted rather than described, and
the slice is long enough to be the whole route: vlc plays it, and the panel
shows it. The frame size is what makes the counting exact - one frame of
1920×1080 planar I420 from vlc's `yuv` video output costs 3,110,400 B, so a
byte count is a frame count.

## vlc's own leg

| the slice, `cvlc --vout yuv --yuv-file=/dev/null --stats --play-and-exit` | rc 0, 46 s of wall time against the slice's 45.041667 s, 7 log lines of which the only entries are the dbus and interface complaints and `main playlist: end of playlist, exiting` |
| the 12 s probe clip written through a fifo on the seat's `/tmp`, drained by `dd … bs=3110400 iflag=fullblock` | `358+1 records in` = 358 whole frames in 12 s against the clip's 360 frames at 30 fps, rc 0 - the whole clip through vlc's decoder and its own output at the clip's rate, with no compositor and no mmcfs in the way |
| the same sink pointed at a file on the mmcfs `/gamedata` | 385,690,375 B = 124 whole frames against the clip's 360, 127 on a second run: the mmcfs write, not the decode, caps this one |
| the seat's statistics block | not printed: `--stats` answers silence, `--print-stats` answers `vlc: unknown option or missing mandatory argument` - which is why the tallies here are byte counts and dd's records |

## the same film on the panel

vlc's frames go through `filesrc … ! rawvideoparse … ! waylandsink`, in 6 s
chunks because the spool sits on a 885M tmpfs and one frame costs 3,110,400 B.
A tee branch shrinks every frame to 32×32 GRAY8 (1,024 B) and counts it, so
the frames the window was handed are exact, and `max-lateness = -1` means the
sink is never told to drop.

| chunk of the film | vlc wrote | frames | window open | frames to the window |
| --- | --- | --- | --- | --- |
| 0–6 s | 7 s | 147 | 6 s | 147 |
| 6–12 s | 7 s | 151 | 7 s | 151 |
| 12–18 s | 7 s | 114 | 7 s | 114 |
| 18–24 s | 7 s | 152 | 7 s | 152 |
| 24–30 s | 8 s | 100 | 6 s | 100 |
| 30–36 s | 8 s | 117 | 6 s | 117 |
| 36–42 s | 7 s | 151 | 7 s | 151 |
| 42–48 s | 4 s | 74 | 6 s | 74 |
| the whole film | 66 s | 1,006 | 52 s | **1,006** |

The film is 1,081 frames over 45.041667 s, so 1,006 of its frames reached the
window, 93 % of them, over 52 s of window time: about 19 fps presented against
the film's own 24 fps, and 16 to 24 fps per chunk. The whole film costs about
118 s of wall time, because the write leg and the window leg run one after the
other.

The window is read out of the seat's own scene graph, 2 s into a chunk, and its
PID is the `gst-launch` of that chunk:

```
View 0 (role xdg_toplevel, PID 109136, surface ID 3, top-level window)
View 1 (role wl_subsurface, PID 109136, surface ID 10, sub-surface)
```

The gallery record is the capture taken inside every chunk: `chunk0.png` …
`chunk7.png` carry the film, 497,624 B to 3,519,568 B, eight distinct md5s,
against `start.png` at 157,537 B of unchanged panel.

## why the chunks, and what the fifo does instead

Wiring vlc and gstreamer to the same fifo makes the two halves wait for each
other, and the loss is measurable: over the 12 s probe clip the tally branch
counted **8 frames of the clip's 360** in 16 s of wall time, both sides rc 0,
and over the 45 s film the window simply stops - the captures at +10 s and
+20 s carry the film (2,525,855 B and 3,587,978 B), the captures at +30 s and
+40 s are the unchanged panel (157,653 B each), and the sink logged 27 ×
"A lot of buffers are being dropped". vlc's own log across such a run carries
no complaint at all, so the player silently stops delivering when its write
end is starved, and the chunking is what lets the writer finish before the
window reads.

| what vlc's default `gles2` display does instead, over the slice | 14,757 log lines, 2,408 refused video outputs (`parent window not available` → `video output creation failed` → `failed to create video output` → `decode_slice_header error`), 301 × `no frame!` |

So the film answers the question it was relayed for, in the two parts it takes
on this seat: vlc decodes and presents the film at the frame rate the media
carries (its own `yuv` output, the film's whole length inside its own wall
time, audio included), and the panel shows 93 % of the film's frames at about
19 fps in 6 s chunks through the gstreamer sink. What vlc cannot do is put its
pixels on the panel itself - `cvlc`'s interface is `dummy`, so the output chain
never gets a parent window, and this seat has no `/dev/fb*` device and no
`/usr/share/terminfo` to answer that with. The panel leg of a 1080p film is
therefore the seat's compositor and the sink, not the player, and making the
player itself own a window belongs to `layers/meta-watermelon-wine`.
