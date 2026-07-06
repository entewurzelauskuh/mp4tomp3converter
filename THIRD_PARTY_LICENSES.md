# Third-party licenses

This app's own code is MIT-licensed (see `LICENSE` and decision D4 in
`docs/DECISIONS.md`). It bundles the following third-party components.

## LAME (LGPL-2.1)

- Component: **LAME** (LAME Ain't an MP3 Encoder), version **3.100**.
- License: **LGPL-2.1** — full text at [`third_party/lame/COPYING`](third_party/lame/COPYING).
- Homepage: <https://lame.sourceforge.io/>.
- Vendored (unmodified) MP3-encoder sources under
  [`third_party/lame/`](third_party/lame/); see
  [`third_party/lame/README.vendored.md`](third_party/lame/README.vendored.md) for
  provenance (source URL, tarball SHA-256, and exactly what was/wasn't copied).
- **How LGPL is satisfied:** LAME is built as a **separate, dynamically linked shared
  library** `liblame.so` (see `app/src/main/cpp/CMakeLists.txt`); the JNI bridge
  `liblame_jni.so` links against it and it is **never statically linked** into the app's
  own code. Users can replace the shared library, and the complete corresponding LAME
  source is included in this repository. This dynamic linking is what keeps LAME's LGPL
  compatible with this app's MIT license (decision D4).
