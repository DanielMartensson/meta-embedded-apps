# Discordo - the record behind the recipe

Detail file for `recipes-apps/discordo/discordo_git.bb`: why the pin is not HEAD,
how the dependency set gets into the recipe, and what the seat has. The load and
use half lives in [README.md](README.md).

## Why this pin and not HEAD

The pin is `31905f3e790e63cd0f2366526afe41fb278c226e`, pushed 2 Jul 2024, and it is
a hard requirement of this board, not a preference:

- oe-core ships `go_1.22.12`. Upstream raised its `go.mod` language line to
  `go 1.23.0` at `b35aaf42` (17 Aug 2024) and to `go 1.27.0` at `e70162b2`
  (21 Aug 2026). From the first bump on, the language level this cross tool can
  compile is not enough and `do_compile` dies.
- the newest commit still at `go 1.22.4` is the one right before that bump, which
  is the pin.
- the license follows the pin: the MIT `LICENSE` checksummed in the recipe is what
  this commit carries, while upstream's current HEAD has since been changed to
  GPL-3.0.

So building HEAD would change the license AND break the build. The round that
measured those `go.mod` bumps is in the games layer's running log
(`../../meta-good-old-games/recipes-games/opennow/PROOF.md`).

## The 21 modules, as the manifest inside the recipe says

`go mod vendor` was run once at the pin with this tree's staged go 1.22.12 and
packed as one fixed-mode/mtime tarball, `files/discordo-vendor-31905f3e79.tar.gz`
(3,618,082 B), so the build needs no network at all. Its own `vendor/modules.txt`
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

`tcell` is what draws the TUI and `tview` what renders the markdown; `arikawa` is
the Discord wire and `gorilla/websocket` its transport; `go-keyring` (plus `dbus`)
is where a token can live instead of the command line.

## How the build gets them, and the failure it prevents

bitbake runs `do_compile` in a sandbox without network, and a plain `go build`
that fetches its dependencies dies there with `go: github.com/...: network is
unreachable`. Two things make this build provably network-free:

- the `addtask vendor` that symlinks `${WORKDIR}/vendor` into the app's own source
  directory - the same trick upstream's go-vendor class uses, and note that a
  `file://` archive lands in `${WORKDIR}` while the `destsuffix` only the git/hg
  fetchers honour is ignored by it;
- `export GOPROXY = "off"`, so an accidental dependency fetch fails loudly at
  `do_compile` instead of reaching the internet.

`GO_INSTALL = "${GO_IMPORT}"` keeps the class from walking the vendored modules
too, which would otherwise drop their stray binaries into `/usr/bin`; and
`do_install:append` removes the vendor symlink from the `-dev` package's `src/`
tree so it carries this project's sources only.

## What the seat has

`discordo_0.1+git0+31905f3e79-r0.01_arm64.deb`, 1,212,420 B, installs
`/usr/bin/discordo` (6,452,560 B, md5 `231b5d9a542ac1a6f95e5a2458e77e2e`, and
`dpkg -s discordo` answers `install ok installed`).

Inline pictures: the client draws images through the kitty graphics protocol, and
the seat's terminal does not speak it, so on this board the client is a text,
markdown and navigation client. That is the whole reason the seat's record says
use rather than watch.
