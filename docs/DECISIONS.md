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
**Resolved 2026-07-06 → Jetpack Compose + Material 3** (spec Option A, the recommendation).

Reactive job list, minimal boilerplate, first-class in current Android tooling.

## D3 — Minimum supported Android version (`minSdk`)
**Resolved 2026-07-06 → `minSdk 31` (Android 12)** (spec Option C).

The user chose to match `minSdk` to the primary target hardware (Android 12) for the simplest
possible test matrix, rather than the spec's recommended `minSdk 29`. Consequences:
- Scoped storage only; no legacy `WRITE_EXTERNAL_STORAGE` branch (already true at API ≥ 29).
- `targetSdk` is still pinned to the latest stable API at scaffold time (does not limit installs).
- Foreground-service typing still needs the API 31–34 (`DATA_SYNC`) vs API ≥ 35
  (`MEDIA_PROCESSING`) split from spec §6.4.
- `POST_NOTIFICATIONS` runtime request still applies (API ≥ 33).

## D4 — License
**Resolved 2026-07-06 → MIT for the app's own code** (spec Option B).

The existing `LICENSE` (MIT, `Copyright (c) 2026 entewurzelauskuh`) stands; the spec's GPL-3.0
recommendation is declined. LGPL compliance for LAME is satisfied by:
1. shipping LAME as the **dynamically linked `liblame.so`** from D1, and
2. recording LAME's LGPL notice in `THIRD_PARTY_LICENSES.md`.

## D5 — Application ID / namespace
**Resolved 2026-07-06 → `applicationId = io.github.entewurzelauskuh.mp4tomp3`**, repository name
`MP4toMP3converter`.

Derived from the MIT `LICENSE` copyright handle `entewurzelauskuh` (confirmed by the user as the
GitHub handle). The Kotlin source root and `namespace` follow the same package.

## 1.0.0 feature decisions (implementation, not spec §3)

Smaller product choices made while implementing the 1.0.0 feature set. Recorded here so
future contributors know the intent.

- **ID3 field set → "extended".** Beyond title/artist/album we expose album artist, track,
  genre, year, composer, disc, and a free-text comment — the fields common tag editors show — not
  the full ID3v2.3 frame catalogue. Cover art is out of scope. Detected values are read from the
  source via `MediaMetadataRetriever` and written as ID3v2.3 (see `engine/Id3v2`).
- **Tag editor default → collapsed, "keep detected".** The tag fields sit behind an
  off-by-default "Edit tags" switch. Left closed, the file's own detected tags are written; opened,
  the edited fields are written. Keeps the common path short while staying useful.
- **Trim fields → labelled "From"/"To", `mm:ss`, second-granular.** Typed fields are two-way
  bound to the range slider and clamped to the clip. Labels are From/To (not Start/End) to avoid
  colliding with the "Start" action. An end at the clip's last whole second counts as full length
  (see `ui/options/resolveTrim`) so merely focusing the field can't shave off a sub-second tail.
- **Delete original → best-effort, honest wording.** The document picker usually grants
  read-only access, so deletion often can't happen; the UI says so plainly rather than implying a
  guarantee.
