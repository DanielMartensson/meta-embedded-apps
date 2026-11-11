# Discordo - the terminal Discord client, and its record

Detail file for `recipes-apps/discordo/discordo_git.bb`, kept out of the
layer's own README so that README stays one screen long: why the pin is not
HEAD, how the dependency set gets into the recipe, and what the client is on
the seat.

## Why this pin and not HEAD

The pin is `31905f3e790e63cd0f2366526afe41fb278c226e`, 2 Jul 2024, and it is
a hard requirement of this board, not a preference: oe-core ships
`go_1.22.12`, and upstream raised its `go.mod` language line to `go 1.23.0`
at `b35aaf42` (17 Aug 2024) and to `go 1.27.0` at `e70162b2` (21 Aug 2026);
from the first bump on, the language level this cross tool can compile is
not enough and `do_compile` dies. The newest commit still at `go 1.22.4` is
the one right before the bump, which is the pin. The license follows the pin,
too: the MIT `LICENSE` checksummed in the recipe is what this commit carries,
while upstream's current HEAD has since been changed to GPL-3.0 - so the
recipe builds the pin, and saying "use HEAD" for this application would
change the license AND break the build. See the running log in
`../opennow/README.md` for the round that measured those go.mod bumps.

## The 21 modules, as the manifest inside the recipe says

`go mod vendor` was run once at the pin with this tree's staged go 1.22.12 and
packed as one fixed-mode/mtime tarball, `files/discordo-vendor-31905f3e79.tar.gz`
(3618082 B), so the build needs no network at all. Its own `vendor/modules.txt`
lists exactly these 21 modules, and the recipe's comment counts them:

```
BurntSushi/toml 1.4.0            alessio/shellescape 1.4.2
atotto/clipboard 0.1.4           danieljoos/wincred 1.2.1
diamondburned/arikawa/v3 3.3.6   gdamore/encoding 1.0.1
gdamore/tcell/v2 2.7.4           godbus/dbus/v5 5.1.0
gorilla/schema 1.4.1             gorilla/websocket 1.5.1
lucasb-eyer/go-colorful 1.2.0    mattn/go-runewidth 0.0.15
rivo/tview (rolling pin)         rivo/uniseg 0.4.7
skratchdot/open-golang (rolling) zalando/go-keyring 0.2.5
golang.org/x/net 0.26.0          golang.org/x/sys 0.21.0
golang.org/x/term 0.21.0         golang.org/x/text 0.16.0
golang.org/x/time 0.5.0
```

`tcell` is what draws the TUI and `tview` what renders the markdown;
`arikawa` is the Discord wire and `gorilla/websocket` its transport;
`go-keyring` (plus `dbus`) is where a token can live instead of the
command line.

## How the build gets them, and the failure it prevents

bitbake runs `do_compile` in a sandbox without network, and a plain `go
build` that fetches its dependencies dies there with `go: github.com/...:
network is unreachable`. Two things make this build provably network-free:
the `addtask vendor` that symlinks `${WORKDIR}/vendor` into the app's own
source directory (the same trick upstream's go-vendor class uses - a
`file://` archive lands in `${WORKDIR}` and the `destsuffix` only the git/hg
fetchers honour is ignored by it), and `export GOPROXY = "off"`, so an
accidental dependency fetch fails loudly at `do_compile` instead of reaching
the internet. `GO_INSTALL = "${GO_IMPORT}"` keeps the class from walking the
vendored modules too, which would otherwise drop their stray binaries into
`/usr/bin`; and `do_install:append` removes the vendor symlink from the
`-dev` package's `src/` tree so it carries this project's sources only.

## What the seat has

`discordo_0.1+git0+31905f3e79-r0.01_arm64.deb`, 1212420 B, installs
`/usr/bin/discordo` (6452560 B, md5 `231b5d9a542ac1a6f95e5a2458e77e2e`, `dpkg
-s discordo` answers `install ok installed`). The client runs in the seat's
`weston-terminal`: the sign-in token comes from the `-t <token>` flag or,
through `go-keyring`, from the seat's keyring helper, and what renders there
is the TUI and Discord-flavoured markdown. Inline pictures are a different
story: the client shows images through the kitty graphics protocol, which
the seat's terminal does not speak, so on this board Discordo is a text,
markdown and navigation client rather than a picture one.
