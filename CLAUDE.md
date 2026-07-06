# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current repository state

**Phases 0 (scaffold) and 1 (domain core + frozen contracts) are complete.** The project builds
and 37 JVM unit tests pass. Next: Phases 2–5 (engine, sinks, service, UI) — independent and
parallelizable against the Phase 1 contracts. `MP4toMP3converter-SPEC.md` remains the **single
source of truth**; `docs/DECISIONS.md` records the resolved D1–D5.

Phase 1 froze these (in `app/src/main/java/io/github/entewurzelauskuh/mp4tomp3/`): `jobs/`
(`ConversionJob`, `JobState`, `FailureReason`, `JobRepository`), `engine/` (`AudioConverter`,
`ConverterResult`, `ProgressThrottler`), `output/` (`OutputSink`, `OpenOutput`, `OutputHandle`,
`FileNaming`), `settings/` (`SettingsRepository`, `OutputTarget`, `OutputTargetSerialization`,
`DataStoreSettingsRepository`, `InMemorySettingsRepository`). **Do not change the §6.2 contract
shapes** — contract changes return to the orchestrator.

**Pinned toolchain (2026-07-06, latest stable at scaffold time — see `gradle/libs.versions.toml`):**
Gradle **9.5.0** (wrapper) · AGP **9.1.1** · Kotlin **2.3.21** · Compose BOM **2026.06.00** ·
Spotless **8.4.0**; build host JDK **21** (Temurin). `compileSdk`/`targetSdk` **36**, `minSdk` **31**.

> **AGP 9 has built-in Kotlin.** Do **not** apply `org.jetbrains.kotlin.android` (it errors).
> Kotlin comes from AGP; the root `build.gradle.kts` bumps AGP's built-in KGP to the pinned
> Kotlin via a `buildscript` classpath, and only the Compose compiler plugin is applied
> explicitly (its version must match Kotlin). Configure Kotlin via the nested
> `android { kotlin { compilerOptions { … } } }` DSL.

## What this app is

A small, free, offline Android app that extracts the audio track from a local `.mp4` and writes it as `.mp3`. Spiritual successor to `brarcher/video-transcoder`, deliberately narrower. Single `:app` module, Kotlin + coroutines/Flow, lightweight MVVM, no DI framework, in-memory job queue.

## Decision Points — all resolved (2026-07-06, see `docs/DECISIONS.md`)

`MP4toMP3converter-SPEC.md` §3 defines **D1–D5**. All five are now resolved and fixed:

- **D1 — engine → Option A (LAME).** `MediaExtractor`+`MediaCodec` decode → **LAME** encode via NDK/JNI. LAME must be built as a **separate, dynamically linked shared library (`liblame.so`)** — never statically linked into app code. This is what keeps it compatible with the MIT app license (see D4). Option B (FFmpeg) is declined.
- **D2 — UI toolkit → Jetpack Compose + Material 3.**
- **D3 — `minSdk` → 31 (Android 12).** Stricter than the spec's recommended 29, chosen to match the primary target hardware and simplify the storage/FGS matrix. `targetSdk` is still the latest stable (36). FGS typing still needs the API 31–34 (`DATA_SYNC`) vs ≥35 (`MEDIA_PROCESSING`) split; `POST_NOTIFICATIONS` still applies (API ≥33).
- **D4 — license → MIT for the app's own code.** The repo's existing MIT `LICENSE` (`Copyright (c) 2026 entewurzelauskuh`) stands; the spec's GPL-3.0 suggestion is declined. LAME's LGPL is satisfied by the dynamic linking above **plus** its LGPL notice in `THIRD_PARTY_LICENSES.md`.
- **D5 — `applicationId`/namespace → `io.github.entewurzelauskuh.mp4tomp3`**, repo `MP4toMP3converter`.

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

## Commands

Build host is **macOS**. Everything builds from a fresh clone via the Gradle wrapper (`./gradlew`)
with no machine-local config beyond `local.properties` (SDK path, git-ignored).

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
