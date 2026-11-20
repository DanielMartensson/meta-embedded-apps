# Big Buck Bunny 1080p - the film, and what it proves on this seat

Detail file for `recipes-multimedia/bigbuckbunny/bigbuckbunny.bb`. The
recipe's own comments keep the fetch, the install and the two QA answers;
this file keeps the numbers that make a relay honest and what a human on the
seat does with the film.

## Why the film is here

The player above is only an installation until something is put in front of
it. A 1080p H.264 master is what makes it a proof: it shows that
`--enable-gst-decode` carries a real picture to this board's panel through
the GStreamer stack the seat already has, at the seat's own 1920x1080.

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

The two hashes are what makes the two ends comparable: the carrier's sha256
is the pin BitBake checks the fetch against, the member's sha256 is what the
seat checks the file it was handed against, and the member is the file VLC
reads - the carrier is not on the seat at all. The 208-byte difference is the
zip's own record of the member.

The dates say why only the byte count and the hash are trustworthy: the member
carries 2008-05-27 inside the zip, the film's release date, while the deb
stamps the same file 2011-04-06, which is this build's `SOURCE_DATE_EPOCH`.
A deb's date says nothing about the film.

## The license, and why the recipe carries a note

The film is (C) 2008 the Blender Foundation and published under Creative
Commons Attribution 3.0, pinned in the recipe as
`LicenseRef-cc-by-3.0-bigbuckbunny` - the same shape the games layer uses for
every shareware and demo payload, a LicenseRef pinned to the rights holder's
own words. A movie has no source-tree `COPYING` for `LIC_FILES_CHKSUM` to
checksum against, so the recipe vendors its own note beside itself:
`files/LICENSE.txt`, 657 bytes, md5 `55b3693b96c288fb290a3ee38f12d018`, the
CC BY 3.0 conditions with the pointer to Blender's own page. The games layer
carried the same shape for the Hexen II payload, whose installer left no
readable notice either.

The build asked for exactly this twice, and both answers are in the recipe
rather than skipped:

- `do_package` failed with `Files/directories were installed but not shipped
in any package: /usr/share/media/bigbuckbunny/big_buck_bunny_1080p_h264.mov`
→ `FILES:${PN} += "/usr/share/media/bigbuckbunny"`. `${datadir}/media` is not
one of the paths the base class's default `FILES:${PN}` covers; the games
layer's DOSBox-X payload carries the same shape for its own tree.
- `do_create_spdx` failed with `Cannot find any text for license
LicenseRef-cc-by-3.0-bigbuckbunny` →
`NO_GENERIC_LICENSE[LicenseRef-cc-by-3.0-bigbuckbunny] = "LICENSE.txt"`, the
games layer's own answer for the two Epic `LicenseRef`s. Without it the
SPDX document gives up on the license name, and the package would ship
without the conditions a CC BY work has to carry.

## What the build produced

`bigbuckbunny_1.0.0-r0.01_arm64.deb`, 723,526,860 bytes, md5
`f54e5fea10df06119f1c24211de4121c`, Version `1.0.0-r0.01`, six entries - the
five directories down to `/usr/share/media/bigbuckbunny` and one file:

```
-rw-r--r-- root/root 725106140 … ./usr/share/media/bigbuckbunny/big_buck_bunny_1080p_h264.mov
```

One file, no code, no `Depends`: this is a payload recipe, and the deb is the
whole point of it. It is also the reason the seat does not get the film
through `dpkg -i` - unpacking 725,106,140 bytes into `/usr/share` needs space
the seat's rootfs does not have. At the last measurement the rootfs had
231,616 kB free against a 5,436,376 kB-free `/gamedata` mount
(`/dev/mmcblk0p11`, 8 GB), which is where every payload of this seat lives,
and is where this one goes: `/gamedata/media/bigbuckbunny/`.

## How to use it on the seat

The film is relayed as the file it already is - the build host cannot ssh to
the seat, so the route is build host → local machine → seat - and verified
against the member's sha256 above, the same check the recipe's pin made the
fetch against:

```sh
sha256sum /gamedata/media/bigbuckbunny/big_buck_bunny_1080p_h264.mov
# expect dc2146a2b1172def56730143ad80cd1825b7fad15f1fc9c23a4e7d01a741ac11
```

Then played through the player this layer carries, without an interface, in
the seat's weston session, and captured the way every other round on this
seat captures a frame:

```sh
HOME=/home/weston XDG_RUNTIME_DIR=/home/weston WAYLAND_DISPLAY=wayland-1 \
  cvlc /gamedata/media/bigbuckbunny/big_buck_bunny_1080p_h264.mov

XDG_PICTURES_DIR=/gamedata/installs/shots/gallery/bigbuckbunny \
  weston-screenshooter
```

The picture that has to be true is a 1080p one: the seat's desktop is
1920x1080 and the film's master is 1920x1080, so a frame that fills the
screen is the frame at its own scale, and the capture is one 1920x1080 PNG
with the seat's own 32 bottom rows of panel to crop out, exactly as the
game gallery's captures are cropped.

## What is on the seat

Nothing yet - the film and the player are measured on the build host only. The
seat was off the network across this round (`ping` answers
`Destination Host Unreachable` from the router node, `ssh` gives `No route to
host`), so the deploy, the sha256 check on the seat and the 1080p capture are
the steps that still have to run once the seat answers again.
