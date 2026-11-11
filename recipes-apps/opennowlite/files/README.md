OpenNOWLite
===========

**A player.** It takes the stream that the shipped CloudMatch core allocates
and draws it on its own picture and audio pipeline instead of letting the
core's own front end present it: Wayland window + KMS scan-out + Vulkan/VAAPI
decode + NVST input + Opus audio → the kernel's ALSA sink.

It exists because the Qt front end that comes with the core had a playback
path that produced black frames. OpenNOWLite asks the same core for the same
allocation, and plays it itself. The core still does the account work:
sign-in, catalog, consent. The client draws and plays.

Two sentences about who builds it, because this is the question that keeps
coming back: **this repository is source only.** The binary you run on the
seat is produced by **Yocto / BitBake** from the recipe
`layers/meta-embedded-apps/recipes-apps/opennowlite/opennowlite_git.bb` in the
Watermelon Wine OS tree, which packages the result as an **arm64 `.deb`** that
the seat installs with `dpkg -i`. Nothing on the seat is compiled or patched by
hand.

This repo mirrors exactly what that recipe ships: the same upstream pin, the
same one patch, the same `opennow-lite.c`, and a `native/` copy of upstream's
reference tree after that patch, so the deltas can be read next to the code
they came from.


What is installed and what was proven
-------------------------------------

The package on the seat is `1.0.4+release+git0+bec4a0b11f-r2.12` — the same
upstream commit the OpenNOW recipe in `meta-embedded-apps` builds, on purpose:

```
upstream   OpenCloudGaming/OpenNow  tag v1.0.4  =  bec4a0b11fc77fb5bfe9584f012296333718e84b
branch     refs/heads/main          (the tag is not reachable from dev)
FFI ABI    18  (OPENNOW_STREAMER_FFI_ABI_VERSION at this pin; was 16 at the nightly)
core RPC   6   (unchanged at this pin; the client still speaks it)
```

```
opennowlite_1.0.4+release+git0+bec4a0b11f-r2.12_arm64.deb   md5 c38b83e2eed1fa37c4b14c2654d48fa9   3.6 MB
/usr/bin/opennow-lite                                       md5 5e1c2c5defe86053cb7ee4f6ab424f8d   375832 B
/usr/lib/libopennowlite_streamer_ffi.so                     md5 4abd0fc739c4cf28e87dbbf0eb88cd63   509616 B
```

A proof round ran end to end with that deb, on Destiny 2
(`--launch-app-id=100362311`). It played: picture, audio, keyboard, mouse,
at 60 fps.

```
session create reply=2067 named=1 phase=preparing
session ready for 100362311
video decoded=301 in 5016ms (60.0 fps) units=13514 at +233s
```

* 35 fps probes, 57.2–60.9 fps, 21 of them exactly `60.0`
* 28 key events and 164 relative mouse motions delivered to the game
* 69000 audio packets in, 960 queued, 720000 samples decoded
* 86 NVST NACKs, all answered — that is what the r2.11 ACK/RTP fix pays for:
  the sender waits for a real `rtxfr/ack.rtp` instead of assuming an instant
  positive NALU/RTP, and the reader treats an RTP with no matching receiver
  ACK as expired and re-sends


Requirements
------------

One `arm64` Linux host with:

* a Linux kernel with **FFSMAF** (`--enable-audio` rejects a kernel without it;
  ALSA is used, not PulseAudio or PipeWire)
* **Vulkan** and a **VAAPI** driver for the GPU you decode on
* **KMS** for the scan-out device
* the **shipped OpenNOW CloudMatch core** next to it (`/usr/bin/opennow-core`),
  already signed in

`aarch64` only — the vendored NVST sender is `aarch64` code.


Install
-------

The install has exactly two parts: **build the deb on the build host**, and
**`dpkg -i` it on the seat**. There is no configure/make on the seat and no
`apt`.

1. Source the OS build environment on the build host.

   ```bash
   cd ~/Dokument/Github/Watermelon-Wine/watermelon-wine-os
   export BUILDDIR=build-openstlinuxweston-stm32mp25-mx
   . layers/openembedded-core/oe-init-build-env "$BUILDDIR"
   ```

2. Build OpenNOWLite. The recipe is in `meta-embedded-apps`, and that layer is
   the only layer involved.

   ```bash
   bitbake opennowlite
   ```

   If the package was already deployed into the image, BitBake's database says
   it is up to date and nothing is rebuilt. Evict it first:

   ```bash
   bitbake -cc none opennowlite        # or drop the `opennowlite` line from /conf/local.d/stt
   bitbake opennowlite
   ```

   Never leave `opennowlite` or `opennow` in `/conf/local.d/stt`: the `notstt`
   override wins before BitBake even parses the layer, and the build looks
   "successful" when nothing was rebuilt. Every layer is already in
   `conf/minitstt`, so `stt` only ever adds the glibc and u-boot ones.

3. The deb lands in the deploy directory, with the package version in its name:

   ```bash
   ls "$BUILDDIR"/tmp-glibc/deploy/deb/cortexa35/
   opennowlite_1.0.4+release+git0+bec4a0b11f-r2.12_arm64.deb      3.6 MB
   opennowlite-dbg_1.0.4+release+git0+bec4a0b11f-r2.12_arm64.deb    62 KB
   opennowlite-dev_1.0.4+release+git0+bec4a0b11f-r2.12_arm64.deb   1.5 KB
   ```

   Find it without typing the version:

   ```bash
   find "$BUILDDIR/tmp-glibc/deploy/deb/cortexa35" -name 'opennowlite*_arm64.deb'
   ```

4. Copy it to the seat and install it. On this board the build host cannot ssh
   to the seat, so the deb is relayed through the local machine; either way the
   last two commands are the whole installation:

   ```bash
   scp opennowlite_..._arm64.deb weston@192.168.1.33:/tmp/    # or via the local machine
   ssh root@192.168.1.33 'dpkg -i /tmp/opennowlite.deb'
   ```

   `dpkg -i` is the entire install procedure. OpenEmbedded builds an
   arch-specific deb for the target machine, and the seat *is* that machine, so
   nothing is rebuilt there. Do not rebuild on the seat and do not run `apt`
   there.

5. Check that the new package is the one that got installed:

   ```console
   # dpkg -s opennowlite
   Package: opennowlite
   Version: 1.0.4+release+git0+bec4a0b11f-r2.12
   ```

6. Delete the deb from the seat's `/tmp`. It is only a copy; the package now
   lives in the packaging database.

No reboot. The seat's desktop is a plain Wayland compositor, so a new package
needs nothing reloaded; if a client was running, stop it with the quit
shortcut (below) and start it again. Do not kill the desktop's own processes
(`weston`, `weston-keyboard`, `weston-desktop-shell`, `seatd`).


Quick start
-----------

The deb installs:

| Path | What it is |
| --- | --- |
| `/usr/bin/opennow-lite` | the client: Wayland + KMS + Vulkan/VAAPI + NVST + Opus + FFSMAF |
| `/usr/lib/libopennowlite_streamer_ffi.so` | the Rust NVST FFI library the client dlopens, built by the same recipe |
| `/etc/opennowlite/config.json` | editable defaults (every path and tunable) |

```bash
/usr/bin/opennow-lite --launch-app-id=100362311 --media-dead=180 --debug-input --fullscreen
```

A full round on the seat is launched into the desktop's own Wayland session,
because the client draws on the compositor the desktop is already running:

```bash
HOME=/home/weston XDG_RUNTIME_DIR=/home/weston WAYLAND_DISPLAY=wayland-1 \
  PULSE_SERVER=unix:/run/user/1000/pulse/native setsid nohup \
  /usr/bin/opennow-lite --launch-app-id=100362311 --debug-input --fullscreen \
  >/home/weston/.runs/lite.log 2>&1 </dev/null &
```

`--media-dead=N` ends the run after N seconds; without it the run lives until
you quit it. The client does not log to the journal — it is detached with
`setsid`, so the way to read it is its own log file, and a line is flushed only
every 64 events, so a quiet stretch is not a hang (only `fatal` / `quit` flush
right away).

**One client at a time.** OpenNOW and OpenNOWLite cannot stream at the same
moment: the core holds an exclusive lock over its data directory. Sign in with
OpenNOW, stop it, then start OpenNOWLite.

**The quit shortcut is Ctrl+Alt+Shift+O** (the `KEY_O` keycode 24, registered
through the compositor). Hold it for about 0.4 s and the client exits
properly: it releases the KMS framebuffer and disables the NVST DRM sender, so
nothing is left tearing on the next session. If the shortcut is unavailable,
`kill -TERM` the recorded PID pair (the client plus the `opennow-core` it
spawned); `kill -KILL` also works. Both leave the seat's compositor and
keyboard alone.


Picking a game by name
----------------------

The client takes a CloudMatch **app id**, not a game name. `opennowlite-games`
turns a name into that id:

```console
$ ~/Dokument/Github/OpenNOWLite/opennowlite-games 'dark and darker'
102303211   Dark and Darker

$ ~/Dokument/Github/OpenNOWLite/opennowlite-games --json |
    python3 -c 'import json,sys
    [print(f"{g[\"launchAppId\"]:<12} {g[\"title\"]}") for g in json.load(sys.stdin)]' |
    grep -i destiny
100362311    Destiny 2
```

It spawns the core that the OpenNOW package ships at the same pin and asks it
for the live library (`catalog.library.list`), so the list is exactly what the
core sees and nothing is cached here. It is standard library only, so it also
runs on the seat; it is **not** installed by the deb, so run it by absolute
path. The lookup takes ~20 s — that is the one reply, not a stall.


Credentials
-----------

**OpenNOWLite never reads, stores or asks for a credential.** No cookies, no
username, no password, no token. It asks the shipped core over JSON-lines RPC
for three things — `catalog.game.get`, `catalog.launch.inspect`,
`session.create` / `session.poll` — and the core owns sign-in, the catalog and
the CloudMatch consent, using the seat's existing signed-in account:

```
~/.config/OpenNOW/fallback-sessions/<hash>.json     ← read by the core, not by this client
```

* **Sign in once with OpenNOW** (the Qt client) before using OpenNOWLite. The
  account is then shared.
* If that record is missing or unreadable, the launch dies at
  `catalog.game.get authentication_required` after about 11 s. Put the record
  back or sign in again with OpenNOW — and never move that file to a backup:
  it is what makes a game launch at all. The only OpenNOW file ever moved to
  `backup/` is `unresolved-allocation.json`, which is a *failed allocation*,
  not a credential.
* Do not hand-edit the record. The v1.0.4 core logged
  `auth: migrated 1 Electron account session(s) into the OS credential store`
  when it accepted one, and the hash in the filename is not something to invent.
* `--data-dir DIR` makes the core use another data directory, which is how
  `--login` can sign in without disturbing the seat's account.


RAM and CPU while playing
-------------------------

Measured on the seat during a Destiny 2 round, 672 s in, from the client's own
`/proc` record:

```
VmRSS    36128 kB   (8600 kB anonymous + 27508 kB mapped files)
%CPU     33.0 of one core
threads  16
```

So the client itself costs about **36 MB resident and a third of one core**
while a 60 fps round plays. The rest of what the seat spends during a round
belongs to the shipped `opennow-core`, which does the catalog, auth and
signalling work. Read together with the compositor, the seat's own overlay
reported about **480 MB of its 1.7 GB pool used and roughly 70 % CPU** during a
round, while `top` on the whole seat read:

```
MiB Mem: 1768.3 total, 384.5 free, 544.2 used, 1020.4 buff/cache
%Cpu:      4.2 us,  8.3 sy, 87.5 id
```


Command line
------------

A switch may be written `--flag=value` or `--flag value`.

| Switch | Meaning |
| --- | --- |
| `--launch-app-id=N` **(required)** | CloudMatch app id (a positive integer). Not a game name or slug. |
| `--fullscreen` | Blit the whole screen through KMS instead of a window. This is what makes the picture appear on the desktop. |
| `--window=WxH` / `--position=X,Y` | Window size (default 1280x720) and position. |
| `--display=ID` | KMS scan-out device (default: the only one). |
| `--drm=<name>` | NVST DRM sender instead of the default `kms`. Ignored when the config file sets it. |
| `--media-dead=N` | Stop after N seconds. |
| `--debug-input` | Log every key, press, release and wheel **and** send them to the game. `--debug-input=0` stops sending them. |
| `--no-audio` | Do not create the audio sink. Audio is otherwise on by default (`--enable-audio` is a no-op kept for compatibility). |
| `--login` | Ask the core to sign in. Needs `--data-dir` and a desktop to type into. |
| `--data-dir=DIR` | Run the core against another data directory. |
| `--debug-egl=NAME`, `--debug-file=PATH`, `--debug-delay=NAME=MS`, `--debug-fd=N`, `--debug-dir=DIR` | Accepted no-ops, kept so an OpenNOW command line can be replayed unchanged. |

Environment: `OPENNOW_CONFIG_DIR` overrides the config directory. `PULSE_SERVER`
is not used by the client — it is ALSA-only.

Config file: `~/.config/opennowlite/config.json`, falling back to
`/etc/opennowlite/config.json`. It is read by the client, not the core, and
every path and tunable can be set there instead of on the command line.

The client writes nothing to stdout or stderr. All logging goes to one file
(`~/.local/share/opennowlite/log` unless the config or `OPENNOW_CONFIG_DIR`
says otherwise), one line per event with a timestamp, a level and a
subsystem.


Troubleshooting
---------------

| Symptom | What it is |
| --- | --- |
| exits at `media.no_allocation` | The core could not allocate a stream for that variant. The app id is wrong, or the game is not available to this account. |
| exits at `catalog.game.get authentication_required` | No signed-in account for the core to use. The variant id is fine — the catalog lookup itself needs the account. |
| exits at `session_conflict` | The core already holds a session. Another client is running, or the last one is still alive. Wait 80–120 s and retry; the core releases its lock. |
| exits at `session_allocation_unresolved` | The core could not resolve a session from its own record. It writes `unresolved-allocation.json` under `~/.config/OpenNOW/` and quits immediately. That file is what it saw and nothing else reads it, so move it to `backup/` before the next try. |
| `catalog.game.get` answers ~15 s in | Nothing is broken. `catalog.library.list` and `session.poll` routinely take 10–25 s. |
| no picture, log still looks successful | `--fullscreen` needs a KMS scan-out device. Without one (KMS not exposed, no Wayland session) the client falls back to `/dev/null` while Vulkan and VAAPI still run. |
| `audio.kernel` reports `unsupported/missing` | The kernel lacks FFSMAF or the sink cannot be opened. Fatal on purpose: the whole point of audio is that the kernel sink works. |
| `nvst.drm` reports an `unknown` | The sender is not the default `kms`. Ask for `--drm=<name>`. |


Building from source
--------------------

The build you want is the one in the OS tree, because that is where the pin and
the compile flags are right: `bitbake opennowlite`. There is **no build system
in this checkout** — no CMake, Meson or Makefile — and there does not need to
be one:

* `opennow-lite.c` is compiled by the recipe with `${CC}` (the arm64
  cross-gcc), plus three generated `-protocol.c` files made by upstream's own
  `wayland-scanner` from the `.xml` copies vendored under
  `wayland-protocols/`;
* the FFI library is built with
  `cargo build --offline --locked --lib --release --package opennow-streamer-ffi --target aarch64-unknown-linux-gnu`,
  which lands at `native/opennow-streamer-ffi/target/` and is installed by the
  recipe as `libopennowlite_streamer_ffi.so`.

The client never compiles upstream's C++ — it links to the shipped core — so
moving to a different upstream means changing the pin lines in the recipe:

```bitbake
SRC_URI = "git://github.com/McGuck/OpenNow.git;protocol=https;branch=main ..."
SRCREV  = "bec4a0b11fc77fb5bfe9584f012296333718e84b"
```

The one thing that can break is the ABI contract: if the new tree's
`OPENNOW_STREAMER_FFI_ABI_VERSION` is not what the patched
`opennow_streamer_ffi.h` and the `native/` tree carry, both have to move with
the pin. The log prints it (`FFI ABI 18`).


Layout
------

| Path | What it is |
| --- | --- |
| `README.md` | this document |
| `opennow-lite.c` | the whole client. C, one translation unit, plus three generated `-protocol.c` files |
| `opennowlite-games` | the name → CloudMatch app id helper. Standard library only, so it runs on the seat; it spawns the core and is not installed by the deb |
| `opennowlite-launch.sh` | a 4-line wrapper that starts the client detached for one app id and prints the log path |
| `0001-hand-encoded-media-to-the-host.patch` | the one patch applied on top of upstream, in the form `meta-embedded-apps` applies it |
| `native/` | upstream's reference tree **after** that patch: the NVST/RTP ACK fix (`nvst_sender_timing_tests.rs`, `rtp.rs`, `rtxBF`, `rtxfr`, plus the sender's README and tests), the cursor path `nvst_cursor.rs`, and the FFI header |
| `native/opennow-streamer/` | the upstream Rust package (`Cargo.toml` and all of its targets) with the patch applied, so the deltas read against the code they came from |
| `native/opennow-streamer-ffi-header.h` | a copy of the patched FFI header, which `#include`s the ABI contract into the client |


License
-------

This is a front end for a subscription service with a proprietary backend. It
carries no license of its own. **Use it only against an account you are
entitled to use.**
