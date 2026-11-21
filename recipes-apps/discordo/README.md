# Discordo - the terminal Discord client, and how to use it on the seat

## Load it

```bash
bitbake discordo
```

The `arm64` deb goes to the seat with `dpkg -i`. It installs one binary:

```
/usr/bin/discordo    the whole client: a terminal TUI, no data directory
```

The client is **pinned to a commit, not HEAD**, and that is a hard requirement of
this board rather than a preference - the reason is a build failure and a license
change, both of them in [PROOF.md](PROOF.md).

## Use it

It is a terminal client, so it runs inside the seat's `weston-terminal`, and it
needs a Discord token:

```sh
discordo -t <token>          # the sign-in token
discordo                     # or let go-keyring read it from the seat's keyring helper
```

What the seat shows is the text TUI and Discord-flavoured markdown. **Inline
pictures are not this board's:** the client draws images through the kitty
graphics protocol, and the seat's terminal does not speak it, so on this board
Discordo is a text, markdown and navigation client rather than a picture one.

## Files

```
discordo_git.bb   the client: its pin, its vendored Go modules, GOPROXY off
README.md         this file: load and use
PROOF.md          the pin's history, the modules, the debs, the seat's install
```
