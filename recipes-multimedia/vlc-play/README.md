# vlc-play - the film's route to the panel

## Load it

```bash
bitbake vlc-play
```

This is a recipe of this layer, not an append of the player's next door: the
launcher is a script this layer wrote rather than something fetched, so it gets
its own package and its own deb version, and the player's own deb keeps the shape
it was measured with.

```bash
dpkg -i vlc-play_1.0.0-r0.01_arm64.deb

/usr/bin/vlc-play      the launcher: a shell script that calls vlc, ffprobe and gst-launch-1.0
```

The deb's `Depends` names the packages its routes call - `vlc`, `ffmpeg`,
`coreutils` and the gstreamer stack, including the two `gstreamer1.0-plugins-bad`
split packages that carry the panel sink and this board's H.264 block - so on a
seat that already has the media stack the install adds one file and nothing else.

## Use it

```
vlc-play film.mp4             the smooth route
vlc-play --count film.mp4     the same run, with the window's frame tally
vlc-play --sw film.mp4        gstreamer's software decode instead
vlc-play --vlc film.mp4       vlc's own decode, chunked through a spool
```

The route is chosen from what `ffprobe` reads out of the file rather than from
what the file is called: an h264 stream in an mp4/MOV-family container goes to
this board's own H.264 block, and everything else goes to gstreamer's software
chain. `--sw` and `--vlc` override that choice. The launcher prints what it read
first - the container, the stream's codec, shape and frame rate, its sound, its
length - and then the route it took, so the two are never confused; `--count`
adds a branch that shrinks and counts every frame, which is what makes the
frame tally exact instead of "the decoder read that many".

The run needs the seat's Weston session, as every application on this seat does,
and the launcher sets it for a shell that does not already carry it, so
`vlc-play <clip>` from a bare ssh session draws on the panel. A film whose
frames do not fit the seat's `/tmp` in one piece is played in chunks by the
`--vlc` route. Every number - the rate each route runs at, what share of the
seat's two cores each one spends, and the gstreamer spells that do not work -
is in [PROOF.md](PROOF.md).

## Files

```
vlc-play_1.0.0.bb     this recipe: the script it vendors, the install, the runtime pieces its routes call
files/vlc-play        the launcher itself
README.md             this file: load and use
PROOF.md              the build, the deb's shape, the seat's install, every route's numbers, failure modes
```
