# The 987ab3da patch series (reference copy, not built)

These are the thirty-eight patches that produced the image revision `r0.44`,
applied to upstream commit `987ab3da` (OpenNOW 1.0.4, before the
`v1.0.4-nightly.1515.1` restructure). They are kept **as documentation only**:
`SRC_URI` does not reference this directory, and bitbake never applies anything
from it.

Why keep them:

* They are the measured history of the board work - which patch introduced the
  stream window, how F11 flips between the windowed and the fullscreen stream
  surface, how the relative pointer is forwarded to the host, how the decoder's
  DMA-BUF is exported to the compositor, and what each experiment cost
  (`fps=`, `committed=`, `flushFailed=` in the wire logs quoted by the README
  table).
* The working series in `files/` is a fresh, smaller series rebased onto
  `v1.0.4-nightly.1515.1`. When a behaviour has to be re-derived (for example
  the F11 press handling in `0023`/`0032`, or the relative-pointer forwarding in
  `0031`/`0034`), the old patch shows exactly what the previous implementation
  did and why.

Nothing here should be applied on top of the new series: the two overlap and
would conflict.
