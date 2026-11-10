# OpenNOWLite - the client that plays

Detail file for `recipes-apps/opennowlite/opennowlite_git.bb`. Everything in it
was measured on the seat with the package this recipe builds. How to use the
client itself, switch by switch, lives in the standalone OpenNOWLite
repository's own README; this file is the build-side record.

This branch builds `opennowlite 1.0.4+release+git0+bec4a0b11f-r2.12` from the
release tag `v1.0.4` = `bec4a0b11fc77fb5bfe9584f012296333718e84b` on
`refs/heads/main`, the same pin as the OpenNOW recipe, and it is still the one
client that plays. The sections below record the older pins (`1.0.4+nightly.1515.1+git0+b3fe71026d`,
up to `r2.11`) as history; the current round is item 65 of the running log in
`../opennow/README.md`.

### OpenNOWLite

`recipes-apps/opennowlite` is the plan-B client: its own Wayland window, its own
GStreamer video and audio chain, and OpenNOW's Rust streamer used only to carry
input and the already-encoded media to Nvidia. It is a separate recipe, a
separate package and a separate source file from `opennow`: the two install side
by side - `/usr/bin/opennow` against `/usr/bin/opennowlite` (the launcher) and
`/usr/bin/opennow-lite` (the client), plus this recipe's own
`/usr/lib/opennowlite/libopennowlite_streamer_ffi.so` against OpenNOW's own
`/usr/lib/libopennow_streamer_ffi.so` - with no shared path, and nothing of the
opennow patch series is reused. OpenNOWLite pins OpenNOW
**v1.0.4-nightly.1515.1** (`SRCREV = "b3fe71026d8261d386c6f16fcdeb1596ebb85942"`)
in its own recipe with its own one-patch series: `0001-hand-encoded-media-to-the-host.patch`
adds `EncodedMediaKind`/`EncodedMediaPacket`/`EncodedMediaTap`, `MediaSink::with_tap()`,
`spawn_passthrough_linux()`, a `validate_backend()` bypass for a device-less engine,
and the `encoded_media_callback` plus `media_passthrough` fields of
`OpenNowStreamerConfig`. The library is dlopened by absolute path out of this
recipe's own directory and the app links `-ldl` only, so the process has no
`DT_NEEDED` entry for OpenNOW's SONAME and can never load, shadow or replace the
library the opennow recipe ships. The patched tree is also kept in a standalone
repository next to the layer, `/home/ubuntu/Dokument/Github/OpenNOWLite`, so the
same source can be read outside the build.

The design, in the order the pieces were measured:

* **The window is OpenNOWLite's own.** `opennow-lite.c` creates an xdg-shell
  toplevel (`app_id=opennowlite`, title `OpenNOWLite`, 1920x1080 by default) and
  the seat's `wl_keyboard` and `wl_pointer` listeners on the same `wl_display`.
  opennow-qt is not involved at all, and because the same connection owns the
  window, the seat's keyboard keeps focus on it: the opennow series' focus
  problem (open item 13) does not exist on this path.
* **waylandsink draws into that window** through `gst_wl_display_handle_context_new()`
  (`GstWlDisplayHandleContextType`, from `gstreamer-wayland-1.0`) for the `wl_display`
  and `GstVideoOverlay` for the `wl_surface`, the same hand-off this layer uses for
  YtGst, plus `gst_video_overlay_set_render_rectangle(0, 0, width, height)` before the
  first frame - without it the sink fails with `Window has no size set` and stops.
* **A view of that window only exists once the surface has content.** waylandsink
  renders into a *subsurface* (`gst_wl_window_new_in_surface`), and weston maps a view
  when the surface itself is painted, so nothing was visible until the app committed its
  own XRGB8888 shm frame (a dark slate, `0x00101820`) after the first configure. Stable
  xdg-shell has no `xdg_toplevel_set_size` - that call failed to link - so the size comes
  from `xdg_surface_set_window_geometry(0, 0, width, height)` plus the attached buffer.
* **The session is the shipped core's.** `opennow-core` is asked over its RPC, which is
  *two one-way pipes*, not a socketpair: the handshake is `core.hello {"protocolVersion":6,...}`
  and after `session.create` the client **must** answer `{"type":"ack","id":"lite-create"}`
  or the core never continues. Replies carry no `type` member, so they are matched by `"ok"`
  plus the id. `hello`, `game.get`, `launch.inspect`, `create` and `prepare` wait 60 s
  (`OPENNOWLITE_RPC_TIMEOUT`, because the core's own credential/Electron startup and the vendor
  round trips run before the first request is read), while `session.poll` keeps 25 s and is
  re-asked ~2 s apart; `launch.inspect` answers with `scope`/`status`, and the vendor holds
  `preparing` (`status=1`) for 100-140 s before `status` is 2 or 3. Shutdown is closing the
  request end.
* **Media tap hands over assembled units**, so each is pushed straight into its `appsrc`:
  one video packet is one complete H.264 access unit (caps `video/x-h264,stream-format=byte-stream,alignment=au`,
  and the decoder on this board is `v4l2slh264dec`, the V4L2 stateless element, which outputs
  NV12 straight into waylandsink with no `videoconvert` anywhere in the chain), one audio packet
  is one complete Opus frame already unwrapped from RFC 2198 RED by `NvstAudioReceiver::depacketize`.
  The audio chain is `appsrc ! queue leaky=downstream max-size-buffers=200 ! opusdec ! autoaudiosink`
  with caps `audio/x-opus,channel-mapping-family=0` - the channel count is left to the packet,
  since pinning `channels=2` disagreed with a mono stream and made `opusdec` report a stream error
  on the first packet - and audio is fed at 200 frames a second (5 ms) against the 60 video units
  a second. `opusdec` needed the `gstreamer1.0-plugins-base` opus split package (`gstreamer1.0-plugins-base-opus`,
  which `layers/meta-watermelon-wine`'s bbappend turns that feature on for) and `libopus` in `RDEPENDS`.
* **An `appsrc` defaults to `GST_FORMAT_BYTES`, and that is what killed the audio.** The
  segment event it posts says "bytes", `GstAudioDecoder` answers `unsupported format;
  ignoring` and the appsrc itself posts `Internal data stream error` / `streaming stopped,
  reason error (-5)` - which reads as a video-bus error because it comes from the appsrc.
  The fix is `"format", GST_FORMAT_TIME` in `media_source_new`: the PTS on these buffers is
  already nanoseconds, so time is also the honest format. Verified offline on the seat against
  the real first packet (`/tmp/audiotest.py` plus `/tmp/opennowlite-audio1.bin`, 295 bytes):
  pushed with the default it errored, pushed with a time segment it decoded. `gst-launch`/`filesrc`
  tests are *not* a valid stand-in - they produce a BYTES segment anyway.
* **`block` on the appsrc stays FALSE**, and this is load-bearing: the push is made from the
  streamer's own media-callback thread, so a blocking push past `max-bytes` stops the feed for
  the rest of the session (measured on r0.3: the picture froze after 7 min, the thread named
  `opennow-media-c` sat in a `g_cond_wait` inside `libgstapp-1.0.so` under `gdb`, the tallies
  stayed at `video units=25200`/`audio packets=85500` for the next 40 min while the streamer's
  own `NVST rx-stats` kept counting 35808 frames per 600 s). A refused push is counted instead
  (`video refused=N`), and the appsrc's own queue depth (`current-level-bytes`, not
  `gst_app_src_get_size`, which answers `uint64 -1` here) is printed beside every tally, in
  bytes: at 60 units a second a chain that keeps up holds under one unit of picture (~30 KiB),
  so a growing `queued=` is what a falling-behind decoder reads as, rather than a frozen picture.
* **Audio failures must not end the session.** `drain_audio_bus` logs every message with
  `GST_MESSAGE_SRC_NAME` (the first error read is usually the appsrc's "internal data stream
  error", which only says something downstream failed), and `media_audio_teardown` sets
  `audio_ready = false` and puts the pipeline in `GST_STATE_NULL` while keeping the objects
  alive until exit, because the streamer's media thread may still be inside
  `gst_app_src_push_buffer`.
* **The seat's input numbering is evdev plus a vendor mapping.** A `wl_keyboard.key` event
  carries the Wayland code, which is the evdev code plus 8; the authoritative table is upstream's
  `PhysicalKeyMapData.h` `Layout.virtualKeys[128]` indexed by **evdev**, and the specials come from
  `StreamVideoItemInput.cpp` (Return `0x0d`, Escape `0x1b`, Backspace `0x08`, Tab `0x09`, the
  modifiers `0xa0`-`0xa5`, Super `0x5b`/`0x5c`, arrows, F-keys, keypad). The ad-hoc `evdev + 8`
  table this client had before had **no Return at all** - Enter was silently dropped - and mapped
  Escape as Tab, so the exit shortcut could never fire. Wire modifiers come from upstream's
  `inputModifiers`: `0x01` shift, `0x02` control, `0x04` alt, `0x08` meta (xkb bits 0, 2, 3, 6),
  which is why the quit shortcut mask is now `0x0d` (the old `0x7` asked for shift, caps-lock and
  control and could never fire).
* **Buttons are the seat's evdev codes, not 1/2/3.** A `wl_pointer.button` event carries BTN_LEFT
  `0x110` (272), BTN_RIGHT 273 and BTN_MIDDLE 274; the FFI validates `1..=5` and upstream's
  `windows_raw_input.rs` `BUTTON_FLAGS` maps left to 1, middle to 2 and right to 3, so this client
  maps 272 to 1, 274 to 2 and 273 to 3. Sending 0 for the left button was measured as `button
  submit refused (2)` (`INVALID_CONFIG`), and comparing the event against 1 meant the click never
  reached the game at all.
* **The pointer is absolute, not relative.** This was the measured bug: a client that sent relative
  deltas had 100 pointer buttons and 28 keys reach the FFI with no refusal and the seat's own arrow
  move, while nothing in the stream ever did. The remote sends a cursor notification
  (`NVST cursor capture tx: command=0x0308 ... reason=cursor-notification`), OpenNOW's own default is
  absolute (`relativeMouse` is an opt-in QML setting), and the FFI publishes it with
  `submit_mouse_absolute(handle, x, y, width, height)` in video pixel coordinates: upstream clamps
  the point inside the aspect-fit viewport and sends the viewport's extent in the same packet, and the
  FFI rejects `width == 0 || height == 0`. weston reports the cursor inside *the surface it hovers*, so
  the coordinates only mean something while that surface is this window (`a->pointer_surface == a->surface`),
  and the window keeps the video's aspect ratio, so window pixels need no rescaling. Upstream publishes
  a position on hover, on every button transition (press *and* release, position first, because no move
  event is guaranteed before a click) and on wheel, so the vendor's hit-testing and its clicks agree; a
  wheel step is 120 units of a turn on weston, on Windows (`WHEEL_DELTA`) and in GFN's packet, so the raw
  value goes through unchanged - dividing by 120 made the scroll invisible.
* **The client drives the cloud game's own notices** (`--auto-pilot`), and the measured shape of that
  chain is one event per notice, ~135 s apart: extra presses of one clock close the host's own windows
  and leave a bare desktop. The armed defaults are the measured offsets, `--pilot-at=63:r,212:r`: one
  Return at +63 s answers the consent notice and one Return at +212 s answers the splash's PRESS ENTER
  TO PLAY. **Every event after the notice list is a pointer jiggle, not a key** - `--pilot-gap=60`,
  `--pilot-times=64`, so the first is at +272 s and the last at +4052 s, past a free-tier hour - one
  absolute submit at the window's middle plus or minus 40 px, the side alternating with the event number,
  logged as `opennowlite-pilot: jiggle N x= y= at +Ns due=+Ns` and counted in the exit tally as
  `jiggle=%lu`. Keys after the list are measured to fail, and the failure is the game's own: on the r1.3
  round of Nov 7 the Escape armed at +270 s reached the corner hint that character screen carries,
  `Esc Exit to Desktop`, which opened Destiny 2's own `ARE YOU SURE? ... quit your current session and
  exit to desktop` over the guardian (the witness reads it at +280 s and +290 s, the screen's build line
  `9.7.0.6` still in the corner), and the *next* Enter of the same clock, the one armed at +330 s,
  answered that confirm - the session went back to the Steam desktop (the store page at +299 s) and the
  vendor then sent no media at all, `NVST rx-stats elapsed=320.1s ... frames=18298` being that round's
  last line, while `session.poll` kept answering `ok` in the core's own diagnostics and the client kept
  taking input. A window holding one frame for the next ten minutes is what reads on the TV as an
  "extremely slow" picture rather than as a stopped one, which is why a pointer move - activity the
  host's idle timer counts, though the round measured below shows that once a minute of it is not
  enough to hold the hour unattended - replaced the keep-keys in `r1.4`.
* **The clock's own gap was a bug of its own** (found and fixed between r1.2 and r1.3): the keep events
  after the list were armed on a sliding `last + gap` that the main loop re-based, so they fired at
  +282 s, +330 s, +390 s, +450 s, ... instead of exactly 60 s apart. r1.3 arms each event at its own
  `due` and the round killed from pids 267195/267200/267245 shows the presses at +630 s, +690 s, +750 s
  ... +1050 s, exactly 60 s apart (`stopping ... submits=34 refused=0 skipped=0 pilot=16`).
* **The window presents the game at 59.33 fps, so neither GStreamer nor the window is what makes the
  picture look slow.** `fpswin.sh` (seat copy `/tmp/fpswin.sh`) feeds `/tmp/clip-h264.h264`, a slice of
  the real 60 fps stream cut out of a witness: 1801 frames, md5 `1f3f5dbdc13f320f42132835c0b2f403`.
  Through **the client's own window** with the chain
  `filesrc location=CLIP ! h264parse ! v4l2slh264dec ! waylandsink name=sink` and `sync=true` (the sink
  waits until the compositor has taken the frame, so the wallclock is the rate the window showed):
  **1801 frames in 30355 ms = 59.331 fps**. The same chain through `gst-launch-1.0`'s own top-level
  waylandsink: 30244 ms = 59.549 fps. So the client's hand-off costs 0.2 fps of its own window. With
  `sync=false` the same client window is fed at 123 fps (14615 ms), so the decode is not the limit
  either. The other numbers on the table are benches of other shapes, not of the game: decode into
  `fakesink` (`fpsbench.sh`, `fpscmp.sh`: 47.33 s for 1801 frames = 38.05 fps, and 47.35 s on the
  repeat) is a chain with no element holding the decoder's output, so the stateless decoder recycles
  its own buffers and that rate is the bench's shape - the same chain with a `queue` in front of the
  sink ran 17.28 s = 104 fps - and `avdec_h264` into `fakesink` costs about the same (48066 ms = 37.47
  fps), so the two decoders are interchangeable here; and `--test-pattern --pipeline` demos read under
  6 fps because `videotestsrc`'s own 1080p drawing is what is in that chain (`fpsbench2.sh`: 1800 frames
  of `pattern=clock` did not finish in 600 s), not the game. What is slow on this seat is a *stopped*
  window, and the pilot of `r1.4` is the fix for it.
* **The picture is recorded so a screen nobody is looking at can still be read**
  (`--record=FILE --record-seconds=S`). The tap's units are whole access units with their start codes,
  so writing them out in order gives a raw H.264 stream: `ffprobe` reads it as 1920x1080 yuv420p at
  60 fps (a 480 s record is 269660160 bytes) and `ffmpeg -i FILE -vf "select='eq(n\,16200)'" -vsync
  vfr out.png` gives a frame that can be looked at. `-ss` does not work on that file - the raw stream
  has no index, so ffmpeg answers `could not seek to position 55.000` - which is why the witness is
  selected by frame number in one decode pass. This is the witness for the seat, because
  `weston-screenshooter` answers `Output capture error: unauthorized` while this client holds the
  window.

Measured on the board, the current release being `opennowlite 1.0.4+nightly.1515.1+git0+b3fe71026d-r1.4`
(deb md5 `6135e38dea5a8b70a838ab2f95e1a159`, client `6b0af66da05cef5ed45527fbd48183ee`, launcher
`d0102c5535a4eb16dd28e2402796007a` and FFI library `0c79eeec9a9a2a1e33fbe444dfe297f3` unchanged since
r1.0, verified host to board by md5 at both hops; the PKGR progression is r1.0 (absolute pointer)
`e2279a6249be103e6c26f32fe0291903`/`e6db9ac0196792e2b686934f498749a0`, r1.1 (plus `--record`)
`eb2d0e0437fb338375869714a4590e32`/`12742f9b5211735965118f40ce4764e5`, r1.2 (list-based pilot clock)
`69cdaa592c50262b6acce3e8fa20f366`/`f92d3f251a7786c036428cb91b4a0b42`, r1.3 (per-press key letters,
keep-gap fix, deduped `--help`) `140666db4506a3cb13dbdeb5fe33bdf2`/`c9d2cf1ebc0e7219c60c5d3c9c81fc71`,
r1.4 (pointer-jiggle keep events) `6135e38dea5a8b70a838ab2f95e1a159`/`6b0af66da05cef5ed45527fbd48183ee`):

`/home/weston/lite-run.sh` is the reproduction that takes a seat session up and keeps the vendor queue
visible, and `/home/weston/lite-pilot.sh` (md5 `38182802c0c96fa3cc1729a59354b582`) is the unattended
version of the same thing: `ROUND_TIMEOUT`, `MAX_ROUNDS`, `MAX_PLAY` and `REC_SECONDS` are overridable
from the environment, and each round writes its own `/home/weston/.runs/lite-pilot-<TAG>-<round>.log`
plus its `.h264` witness, where `<TAG>` is the seat's own start time, so a relaunch never truncates the
evidence of a previous one. It moves the stale `unresolved-allocation.json` aside to
`/home/weston/backup/unresolved-allocation.json.pilot-<TAG>` before every relaunch (the core refuses
every new session while that record exists - the Nov 7 launches left one behind at 12:06, 12:24, 12:49,
14:09, 14:35, 15:12, 16:17 and 18:24) and only ever kills the PIDs it recorded for its own client and
core, never a pattern. The marker lines the log carries are the ones listed in the source's own header:
`window ready`, `rpc <method> line=<n>`, `reply for <id> in <n> ms`, `session ready for <id>`, `engine
started for <id>`, `encoded video unit 1` / `encoded audio packet 1` (with `bytes=290 channels=2
rate=48000`), `video units=N queued=N` / `audio packets=N queued=N`, `recording N s of picture into
FILE`, `pilot armed with N events, first at +Ns`, `pilot: press N vk=0x.. at +Ns due=+Ns`,
`pilot: jiggle N x= y= at +Ns due=+Ns`, `pointer outside the window ignored`, `<kind> submit refused
(<n>)` and the exit tally `stopping keys= motions= submits= refused= skipped= pilot= jiggle=`.

The free hour was held end to end by an unattended r1.2 round: script pid 193561, client pid 193566,
core pid 193612, round 1 started 15:12:34 seat time, 3907 s of wall, and the streamer's own counters
ended at `NVST rx-stats elapsed=3600.6s frames=213596` - 59.4 frames a second reaching the client for a
full hour - with the exit tally `opennowlite: stopping keys=12 motions=1734 submits=2111 refused=0
skipped=0 pilot=13`, `opennowlite: record closed bytes=358425830`, `opennowlite: stream reply lite-stop
ok` and `opennowlite: engine stopped`. The witness came back to this repository's own tree byte-identical
(`/tmp/opencode/work/witness-r12.h264`, md5 verified against the seat's copy), and the seat's keyboard
reached the client during it (`keys=12`). A human at the seat also confirmed the picture's colours read
brighter than the opennow series' own window, which is consistent with open item 6: this client's NV12
goes from `v4l2slh264dec` to `waylandsink` with no `videoconvert` and no tone map on the board, so the
difference is the TV's own picture processing on the SDR signal it is handed.

The r1.4 build's own round of Nov 7 (script pid 309057, client pid 309062, core pid 309108, round 1
started 20:11:19 seat time, `/home/weston/.runs/lite-pilot-1107-2011-1.log` plus its witness) is that
pilot fix measured: the log armed `pilot armed with 66 presses, first at +63s`, answered the consent
notice at +63 s and the splash at +212 s, and then logged jiggles at +272 s, +332 s, ... +1532 s. At
media +1550 s the tallies read `video units=91800 queued=0` (a third of one unit of picture held in
the appsrc, so the decoder kept up) against `NVST rx-stats elapsed=1550.2s frames=92151` - 59.4 frames
a second arriving for 25 1/2 min, where the r1.3 round of the same day had already stopped arriving at
+320 s. That round's witness (269660160 bytes for the first 480 s of picture, md5 `383b3f3d6758e29bcdd78a03f89ca908`
verified seat to local) reads: Steam's store carousel at +90 s, Destiny 2's own dark network loading art
at +220 s - the PLAY answer at +212 s is what opened it - and the character screen at +270 s, +330 s,
+420 s and +470 s, the guardian standing with its `Hunter`/`ELO` class card and the corner's only key
hint reading `Esc Exit to Desktop`, with no quit confirmation anywhere in those frames and the guardian's
idle pose different between the +270 s and the +420 s one, so the window was advancing rather than
holding a frame. The round ran to its full 3900 s cut-off and answered `SIGTERM` with
`opennowlite: stopping keys=0 motions=88 submits=164 refused=0 skipped=0 pilot=2 jiggle=58`,
`opennowlite: record closed bytes=269661582`, `opennowlite: stream reply lite-stop ok` and
`opennowlite: engine stopped`: the pilot pressed exactly twice, moved the cursor 58 times, and 88 pointer
positions rode the wire with 0 refused. The media, though, stopped on its own at 1720 s of picture -
101810 frames, 59.2 a second up to that line, `qosUtilPercent=3` on the `control-stats` line after it and
no further `rx-stats` line at all for the next 32 min, while the jiggles kept logging to +3692 s and the
core kept answering `session.poll: outcome=ok` - so the unattended r1.4 round held 28 1/2 min of picture
against the r1.2 hour round's 3600.6 s, and the two rounds differ on one thing: 88 pointer positions here
against 1734 there, which is what a human's mouse makes and a jiggle clock does not.

Open items for OpenNOWLite, recorded so they are not mistaken for done:

1. **An unattended seat held 28 1/2 min of picture on r1.4, not the hour, and two things are owed.** The
   media stops on its own at 1720 s with the session still alive and the jiggles still logging, while the
   attended r1.2 round above held 3600.6 s of picture - the difference the logs can see is the pointer:
   88 positions sent here against 1734 there. So the first thing owed is a keep clock that reads like a
   hand (a cursor that moves every second or two rather than once a minute, which is what the 60 s jiggle
   clock is), and the second is the character screen, which waits for the player's own choice: `--pilot-click`
   with `--pilot-point` on the class card the witness can name is this client's only lever for that step,
   and whether such a click leaves the game's own menus is unmeasured. A key is not a substitute for either
   - keys after the notice list are what opened the exit shortcut above.
2. **The input has no in-log proof of crossing the wire.** The FFI answers `OPENNOW_STREAMER_OK` for an
   event it dropped (the gate refuses while its owner is inactive or the NVST feedback `input_available`
   is false), and the opennow series' `NVST embedded input tally:`/`native input drain:` lines belong to
   *its* patch series, not this one, so a lite run's log cannot say how many presses rode the wire. What
   it can say is which calls were refused and that the input handshake happened
   (`NVST cursor capture tx: command=0x0308 enabled=true reason=input-activation`,
   `NVST remote cursor tracking tx: command=0x030d enabled=true`). An input tally in this recipe's own
   patch series is the next thing to land.
3. **`weston-screenshooter` is unauthorized while this client holds the window**, so the picture is
   witnessed by the recorded H.264 file, not by a screenshot, and reading a frame costs a full decode
   pass over the file.
4. **The seat's `autoaudiosink` warns `Failed to connect: Connection refused`** (no PulseAudio on the
   board) and still plays through ALSA; whether the TV's own speaker is the one that carries the game's
   audio is a matter for a human ear.
5. **The seat's free-tier hour and its free disk pull against each other.** A 480 s witness is ~270 MB
   and the board's root filesystem has ~1.2 G free, so a fetched witness is deleted on the seat after its
   md5 has been verified against the local copy; that is what keeps a second round's witness inside the
   space.

The numbered items that carry the OpenNOWLite rounds live in the running log in
`../opennow/README.md`: items 1..65 are one shared record, and splitting it
would break the item numbers the rounds quote each other by.
