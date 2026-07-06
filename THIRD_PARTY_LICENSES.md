# Third-party licenses

This app's own code is MIT-licensed (see `LICENSE` and decision D4 in
`docs/DECISIONS.md`). It bundles the following third-party components.

## LAME (LGPL-2.1)

> **Pending Phase 2.** LAME 3.100 will be vendored under `third_party/lame/`
> (unmodified) and shipped as a **dynamically linked** `liblame.so`. This dynamic
> linking is what keeps LAME's LGPL compatible with this app's MIT license.

When LAME lands, this section records:

- Component: **LAME** (LAME Ain't an MP3 Encoder), version **3.100**.
- License: **LGPL-2.1** — full text will be included at `third_party/lame/COPYING`.
- Homepage: <https://lame.sourceforge.io/>.
- How LGPL is satisfied: LAME is built as a separate, dynamically linked shared
  library (`liblame.so`); it is never statically linked into the app's own code.
  Users can replace the shared library. The complete corresponding LAME source is
  vendored in this repository under `third_party/lame/`.
