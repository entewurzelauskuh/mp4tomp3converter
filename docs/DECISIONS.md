# Decision Points

Resolutions of the Decision Points defined in `MP4toMP3converter-SPEC.md` §3. Resolved
decisions are fixed; changing one requires updating this file and any dependent code/docs.

## D1 — Conversion engine
**Resolved 2026-07-06 → Option A: `MediaExtractor` + `MediaCodec` decode → LAME encode (NDK/JNI).**

LAME 3.100 is vendored under `third_party/lame/` (unmodified) and built as a **separate,
dynamically linked shared library `liblame.so`**. It must **never** be statically linked into
the app's own code — dynamic linking is what keeps LAME's LGPL compatible with the MIT app
license (see D4). FFmpeg (Option B) is declined.

## D2 — UI toolkit
**Open.** Recommendation: Jetpack Compose + Material 3.

## D3 — Minimum supported Android version (`minSdk`)
**Open.** Recommendation: `minSdk 29` (Android 10) — scoped storage only, single modern
storage code path.

## D4 — License
**Resolved 2026-07-06 → MIT for the app's own code** (spec Option B).

The existing `LICENSE` (MIT, `Copyright (c) 2026 entewurzelauskuh`) stands; the spec's GPL-3.0
recommendation is declined. LGPL compliance for LAME is satisfied by:
1. shipping LAME as the **dynamically linked `liblame.so`** from D1, and
2. recording LAME's LGPL notice in `THIRD_PARTY_LICENSES.md`.

## D5 — Application ID / namespace
**Open.** Likely `io.github.entewurzelauskuh.mp4tomp3` (from the license copyright handle) —
confirm the GitHub handle and repository name with the user before scaffolding.
