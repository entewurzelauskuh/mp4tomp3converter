# Vendored LAME 3.100

This directory contains the **unmodified** MP3-encoder sources of the LAME project,
version **3.100**, vendored per decision **D1** (`docs/DECISIONS.md`): the app decodes
MP4 audio with `MediaExtractor`/`MediaCodec` and encodes MP3 with LAME.

## Provenance

- **Version:** LAME 3.100 (released 2017-10-13; stable, the last LAME release).
- **Source URL:** <https://downloads.sourceforge.net/project/lame/lame/3.100/lame-3.100.tar.gz>
- **Tarball SHA-256:** `ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e`
- **License:** GNU LGPL 2.1 — see `COPYING` (the "Library General Public License") and
  `LICENSE`, both copied verbatim from the tarball.

## What was vendored, and what was **not**

Copied verbatim from the tarball (no edits — see "Do not edit" below):

- `libmp3lame/*.c`, `libmp3lame/*.h` — the encoder library sources.
- `libmp3lame/vector/*` — the SSE intrinsics helpers (built only where `HAVE_XMMINTRIN_H`).
- `include/lame.h` — the public API header.
- `COPYING`, `LICENSE` — the LGPL 2.1 notice.

Deliberately **omitted** (not needed for a pure MP3 *encoder*):

- `mpglib/` — LAME's built-in MP3 *decoder*. We decode with Android `MediaCodec`, so the
  build defines neither `HAVE_MPGLIB` nor `DECODE_ON_THE_FLY`; `libmp3lame/mpglib_interface.c`
  is entirely wrapped in `#ifdef HAVE_MPGLIB` and compiles to nothing.
- `frontend/`, `Dll/`, `ACM/`, `dshow/`, `mac/`, `macosx/`, `misc/`, `doc/`, autotools/MSVC
  build machinery — command-line tool, Windows DLL/codec wrappers, and packaging, none of
  which ship in an Android app.
- `libmp3lame/i386/*` — hand-written NASM/x86 asm (`HAVE_NASM` is off; the portable C paths
  are used on every ABI).

## Do not edit

These files are an upstream import and must stay byte-for-byte identical to the release, so
the vendored source stays auditable and F-Droid-friendly. The NDK build configuration that
LAME normally generates with autotools (`config.h`) is **not** placed here — it lives in
`app/src/main/cpp/config.h` (hand-written for the NDK), alongside `CMakeLists.txt` and the
JNI bridge, precisely so that nothing under `third_party/lame/` is modified.

LAME is built as a **separate, dynamically linked shared library `liblame.so`** and is never
statically linked into the app's own code — this is what keeps LAME's LGPL compatible with
the app's MIT license (D1/D4).
