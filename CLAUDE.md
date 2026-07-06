# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current repository state

This project is **pre-scaffold**. There is no Gradle project, no source code, and no build yet — only:

- `MP4toMP3converter-SPEC.md` — the **single source of truth**. Read it fully before writing any code.
- `README.md` (stub), `LICENSE` (MIT), `.gitignore` (Android/Gradle-ready), `.idea/` (IntelliJ, JDK 21).

Do not treat the build/test commands below as runnable until Phase 0 scaffolding exists.

## What this app is

A small, free, offline Android app that extracts the audio track from a local `.mp4` and writes it as `.mp3`. Spiritual successor to `brarcher/video-transcoder`, deliberately narrower. Single `:app` module, Kotlin + coroutines/Flow, lightweight MVVM, no DI framework, in-memory job queue.

## Before writing code: resolve the Decision Points

`MP4toMP3converter-SPEC.md` §3 defines **D1–D5**. **D1 and D4 are resolved (2026-07-06, see `docs/DECISIONS.md`); D2, D3, D5 are still open** — present those to the user and record answers before Phase 0. Do **not** silently pick defaults.

**Resolved:**
- **D1 — engine → Option A (LAME).** `MediaExtractor`+`MediaCodec` decode → **LAME** encode via NDK/JNI. LAME must be built as a **separate, dynamically linked shared library (`liblame.so`)** — never statically linked into app code. This is what keeps it compatible with the MIT app license (see D4). Option B (FFmpeg) is declined.
- **D4 — license → MIT for the app's own code.** The repo's existing MIT `LICENSE` (`Copyright (c) 2026 entewurzelauskuh`) stands; the spec's GPL-3.0 suggestion is declined. LAME's LGPL is satisfied by the dynamic linking above **plus** its LGPL notice in `THIRD_PARTY_LICENSES.md`.

**Still open:** **D2** UI toolkit (Compose+M3 recommended), **D3** `minSdk` (29 recommended), **D5** `applicationId` (likely `io.github.entewurzelauskuh.mp4tomp3` from the license handle — confirm the GitHub handle first).

## Two hard technical constraints (do not "simplify" around these)

1. **Android has no system MP3 encoder.** `MediaCodec` and Media3 Transformer cannot output MP3. MP3 output *requires* bundling an encoder (LAME, or an FFmpeg build with libmp3lame). This is why the engine needs NDK/JNI.
2. **FFmpegKit is dead** (retired Jan 2025, prebuilt artifacts removed from Maven/GitHub). Do not add a `com.arthenica:ffmpeg-kit` dependency — it will 404. Option B means self-building FFmpeg or trusting a fork.

Rejected approaches (do not revive): Media3 Transformer as the engine; executing an `ffmpeg` CLI binary via `Process`.

## Fixed conventions (spec §3, §7)

- **Language/build:** Kotlin only (Java only for unavoidable JNI glue); Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`); single `:app` module.
- **Architecture:** lightweight MVVM; manual constructor injection via a small `AppContainer` (no Hilt/Koin); no Room (in-memory job list); **DataStore Preferences** for settings.
- **Queue policy:** jobs run **sequentially**, one at a time, FIFO.
- **`third_party/lame/`** holds **vendored, unmodified** LAME 3.100 source — **never edit it**. LAME must ship as a **dynamically linked `liblame.so`** (never statically linked into app code), and its LGPL notice must appear in `THIRD_PARTY_LICENSES.md` — this is what makes the MIT app license compatible with LAME's LGPL.
- **All user-facing strings go in `strings.xml`** (English only in v1). No hardcoded UI strings.
- **No `INTERNET` permission** — ever. A static check must assert the merged manifest omits `android.permission.INTERNET`. Only foreground-service and `POST_NOTIFICATIONS` permissions are allowed (see spec §6.4).
- **Emulator-only device policy (hard rule, spec §10.2).** Agents may only ever run `adb`/Gradle connected tasks against **emulator devices** (serials starting with `emulator-`). **Never** install to, shell into, or otherwise modify a physical device. A physical Android 12 phone exists but is used **exclusively by the human** for the manual checklist (spec §9.4). Before any `adb`/connected task, confirm the target serial starts with `emulator-`.
- **Engine defaults are fixed:** CBR 192 kbps, `lame_set_quality(2)`, source sample rate/channels. Do **not** switch to VBR in v1 (Xing header needs a seekable stream MediaStore doesn't guarantee).
- KDoc on public types; comments explain *why*, not *what*.

## Frozen contracts

The core types and interfaces are specified verbatim in `MP4toMP3converter-SPEC.md` §6.2 (`ConversionJob`, `JobState`, `AudioConverter`, `OutputSink`, `JobRepository`). Freeze these in Phase 1. Sub-agents working Phases 2–5 must **not** change them — contract changes return to the orchestrator.

## Commands (available only after Phase 0 scaffolding)

Build host is **macOS**. Everything must build from a fresh clone via the Gradle wrapper with no machine-local config beyond `local.properties` (SDK path, git-ignored).

```sh
# Finish any task with this before committing (spec convention):
./gradlew spotlessApply test

# Common:
./gradlew assembleDebug            # build debug APK
./gradlew test                     # JVM unit tests (fast; jobs/, FileNaming, settings)
./gradlew test --tests "*FileNamingTest"   # single test class
./gradlew connectedDebugAndroidTest        # instrumented tests (needs emulator)
./gradlew lint spotlessCheck               # static checks (ktlint via Spotless)
./gradlew assembleRelease                  # release APK (debug-signed for now)

# Full green-from-clean gate (spec §7 Phase 7):
./gradlew test connectedDebugAndroidTest lint spotlessCheck assembleRelease
```

Instrumented tests run on **ARM64 emulators only, API 31 and 34/35** (API 31 is mandatory before any release — it matches the primary target hardware; see spec §9.3, §10.1). Test fixtures are generated by `scripts/make_fixtures.sh` (requires `brew install ffmpeg` — a **dev tool only, never shipped**) into `app/src/androidTest/assets/`.

## Implementation ordering (spec §8)

Phase 0 (scaffold) → Phase 1 (domain core + contracts) are **sequential and blocking**. Once Phase 1 contracts compile, Phases 2 (engine), 3 (output sinks), 4 (service/notifications), 5 (UI) are **independent and parallelizable**. Phases 6–9 (integration, test hardening, docs, release) are sequential. Every phase ends with: code compiling, its tests green, `spotlessApply` run, and a conventional-commit-style commit.
