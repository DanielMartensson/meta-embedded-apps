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

What the slice bought is a decode route that can be timed rather than described:

| the slice, `cvlc --vout yuv --yuv-file=/dev/null --stats --play-and-exit` | rc 0, 46 s of wall time against the slice's 45.041667 s, 7 log lines of which the only entries are the dbus and interface complaints and `main playlist: end of playlist, exiting` - vlc's h264 decoder and its own `yuv` video output ran the whole 1080p film at the film's rate |
| the same sink pointed at a file, on the probe clip | 385,690,375 B written = 124 whole 1920×1080 YUV frames at 3,110,400 B a frame, against the clip's 360 frames, and a second run wrote 127: this sink is mmcfs-write bound, not decode bound, and it is the only way this build counts frames, since it prints no statistics block |
| the same file pointed at the seat's default `gles2` display, the slice | 14,757 lines, 2,408 refused video outputs (`parent window not available` → `video output creation failed` → `failed to create video output` → `decode_slice_header error`), 301 × `no frame!` |

So the film answers the question it was relayed for - vlc plays 1080p at the frame
rate the media carries, audio and all - and it leaves exactly one thing open: the
panel. `cvlc`'s interface is `dummy`, so the video output chain never gets a
parent window, and the seat has no `/dev/fb*` device and no `/usr/share/terminfo`
to answer that with; the window-capable legs of the film are the gstreamer and
GL routes instead.
