# MP4toMP3converter — Project Specification for Claude Code

**Status:** Draft v1.0 · **Audience:** Claude Code (and human contributors) · **Platform:** Android · **Build host:** macOS

This document is the single source of truth for building the app. Claude Code should read it fully before writing any code, resolve the **Decision Points (Section 3)** with the user first, then execute the **Implementation Plan (Section 8)** phase by phase.

---

## 1. Overview

### 1.1 What we are building
A small, free, open-source Android app that extracts the audio track from local `.mp4` video files and saves it as an `.mp3` file. It is a spiritual successor to [brarcher/video-transcoder](https://github.com/brarcher/video-transcoder) (GPL-3.0, FFmpeg-based, unmaintained since ~2019), but deliberately far narrower in scope.

### 1.2 Product goals
- Do one thing well: MP4 in → MP3 out.
- Zero network access. The app must not request the `INTERNET` permission (verifiable privacy claim for the README).
- Small APK, minimal dependencies, code a hobbyist can read in an afternoon.
- Distributed as APK via GitHub Releases (later via GitHub Actions). Play Store is **not** a target. F-Droid is a possible future target, so avoid prebuilt binary blobs where practical.

### 1.3 Non-goals (v1)
- No other input containers (no MKV/AVI/WebM) and no other output formats (no AAC/OGG/WAV).
- No video transcoding, trimming, or editing.
- No bitrate/quality settings UI (fixed encoder defaults, see 6.3).
- No job persistence across process death (see 6.6).
- No cloud, accounts, analytics, ads, or telemetry — ever.

### 1.4 Naming
- App name: **MP4toMP3converter** (display name may be styled "MP4 to MP3 Converter").
- A unit of work is called a **conversion job** ("job") throughout code and UI.
- The component that performs decoding/encoding is the **engine**.
- The component that writes the resulting file is the **output sink**.

---

## 2. Critical technical context (read before choosing the stack)

Two facts constrain the design. Claude Code must not "simplify" around them:

1. **Android has no system MP3 encoder.** `MediaCodec` can *decode* MP3 but cannot *encode* it (it can encode AAC, but our output must be MP3). Likewise, Jetpack **Media3 Transformer cannot output MP3** — do not attempt to use it as the engine. Any MP3 output therefore requires bundling an encoder: in practice **LAME** (LGPL, stable since v3.100/2017) or an FFmpeg build that includes libmp3lame.
2. **FFmpegKit is dead.** The de-facto standard FFmpeg wrapper (`com.arthenica:ffmpeg-kit`) was retired in January 2025 and its prebuilt binaries were removed from Maven Central/GitHub in 2025; builds that depend on it 404. Community forks exist but none is an established successor. Choosing FFmpeg today means self-building it or trusting a fork — a real maintenance cost for an app that only needs "decode audio, encode MP3".

This is why Decision Point **D1** exists and why the recommendation is the platform-decoder + LAME pipeline.

---

## 3. Decision Points — ASK THE USER FIRST

> **Instruction to Claude Code:** Before Phase 0 coding starts, present D1–D5 to the user (with the pros/cons below, condensed), record the answers in `docs/DECISIONS.md`, and treat them as fixed. Do not silently pick defaults; the recommendations below are only what to suggest.

### D1 — Conversion engine ⭐ most important decision
**Option A (recommended): `MediaExtractor` + `MediaCodec` decode → LAME encode (via NDK/JNI)**
- Pipeline: extract audio track from MP4 → hardware/software decode to PCM → LAME encodes PCM → MP3 bytes streamed to the output sink.
- Pros: tiny footprint (LAME `.so` ≈ a few hundred KB per ABI; total APK likely < 10 MB), no dependency on abandoned third-party artifacts, LAME source is vendored in-repo (F-Droid friendly, reproducible), fast (hardware decode), fully controllable progress/cancel.
- Cons: requires NDK + CMake + a small JNI bridge (one-time setup cost); handles only audio codecs the device can decode. In practice MP4 audio is almost always AAC (universally decodable) or MP3; exotic tracks (e.g., AC-3 on some devices) fail gracefully with a clear error.
- Implementation basis: vendor LAME 3.100 source under `third_party/lame/` with a minimal `CMakeLists.txt`; do **not** depend on old prebuilt "Android LAME" wrapper libraries from Maven (unmaintained).

**Option B: FFmpeg (self-built, or a fork of FFmpegKit)**
- Pros: bullet-proof demuxing/decoding of anything; conversion is one command; easiest path if the app later grows into a general transcoder.
- Cons: FFmpegKit retirement (Section 2) means either (a) building FFmpeg + libmp3lame from source for 4 ABIs and hosting the AARs ourselves, or (b) depending on a community fork of uncertain longevity; APK grows by roughly 6–12 MB (minimal audio-only build) to 25–70 MB (full builds); harder F-Droid story; slower CI.
- If chosen: build a **minimal** LGPL FFmpeg (demuxers/decoders for mp4/aac/mp3 + libmp3lame only), pin the exact FFmpeg commit, and commit the build script to the repo.

**Rejected:** Media3 Transformer (no MP3 output); executing an `ffmpeg` CLI binary via `Process` (fragile under modern Android exec restrictions, and the approach that rotted in the predecessor app).

### D2 — UI toolkit
- **Option A (recommended): Jetpack Compose + Material 3.** Modern default, ideal for a reactive job list, less boilerplate, first-class in current Android tooling.
- **Option B: XML Views + RecyclerView.** Slightly smaller APK and faster builds; more boilerplate; matches older codebases.

### D3 — Minimum supported Android version (`minSdk`)
`targetSdk` is fixed at the current stable API level at scaffold time (35 or newer) — that is best practice and does not limit which devices can install the app. "Target Android 12" from the original brief is interpreted as "must run flawlessly on Android 12"; the real choice is `minSdk`:
- **Option A (recommended): `minSdk 29` (Android 10).** Scoped storage only — a single, modern storage code path (no legacy `WRITE_EXTERNAL_STORAGE` handling). Covers the vast majority of active devices.
- **Option B: `minSdk 26` (Android 8).** Maximum reach for an OSS utility; requires a legacy storage branch and more testing.
- **Option C: `minSdk 31` (Android 12).** Simplest possible matrix; excludes many still-active devices.

### D4 — License
- **Option A (recommended): GPL-3.0.** Matches the predecessor project; unambiguously compatible with LAME (LGPL) and any FFmpeg build we'd ship.
- **Option B: Apache-2.0 or MIT** for the app's own code, with LAME built and shipped as a **dynamically linked** `liblame.so` to satisfy LGPL. Friendlier to reuse; slightly more licensing text to maintain (`THIRD_PARTY_LICENSES.md` required either way).

### D5 — Application ID / namespace
Needs the user's GitHub handle or preferred reverse-DNS, e.g. `io.github.<username>.mp4tomp3`. Also confirm the repository name (suggested: `MP4toMP3converter`).

### Fixed decisions (not up for debate unless the user objects)
- **Language:** Kotlin (+ coroutines/Flow). Java only inside generated/JNI glue if unavoidable.
- **Build:** Gradle with Kotlin DSL + version catalog (`gradle/libs.versions.toml`). Single `:app` module.
- **Architecture:** lightweight MVVM; no DI framework (manual constructor injection via a small `AppContainer`); no Room (in-memory job list); **DataStore Preferences** for settings.
- **Queue policy:** jobs run **sequentially**, one at a time, FIFO (parallelism is a future setting).
- **Formatting/lint:** ktlint via the Spotless Gradle plugin; Kotlin official code style.

---

## 4. Functional requirements

| ID | Requirement |
|----|-------------|
| F1 | **Main screen** shows the list of conversion jobs (newest first): source filename, state (Queued / Converting with % progress bar / Done / Failed / Cancelled), and for Done the output location. Empty state shows a short hint ("Tap ⋮ → Convert a file"). |
| F2 | **Overflow menu (top-right ⋮)** with exactly two items: **"Convert file…"** and **"Settings"**. |
| F3 | "Convert file…" opens the system document picker (`ACTION_OPEN_DOCUMENT`) filtered to MIME `video/mp4`, with **multi-select enabled**. Each picked file becomes a queued job immediately. |
| F4 | Conversion extracts the first audio track and encodes it to `.mp3` (defaults in 6.3). Video data is discarded. |
| F5 | **Default output location:** the device's public **Music** folder, written via `MediaStore` (`RELATIVE_PATH = "Music/"`). The file must appear in music players/file managers immediately after completion. |
| F6 | **Settings screen:** one setting in v1 — *Output folder*. Shows the current target ("Music (default)" or the chosen folder's human-readable path); "Choose folder…" launches `ACTION_OPEN_DOCUMENT_TREE` and persists the permission; "Reset to default" reverts to Music/MediaStore. |
| F7 | Output filename = source display name with extension swapped to `.mp3`; illegal filename characters sanitized; on collision append ` (1)`, ` (2)`, … |
| F8 | Running/queued jobs have a **Cancel** action (list item and notification). Cancelling deletes any partial output file. |
| F9 | Conversions continue with the app in background via a **foreground service** with a progress notification (per-job progress; tapping opens the app). |
| F10 | Failures never crash the app: the job shows Failed + a one-line human-readable reason (see 6.5). |
| F11 | Completed/failed jobs stay in the list for the current process lifetime and can be dismissed individually ("Clear" on the item). |

## 5. Non-functional requirements

- **N1 Permissions:** no `INTERNET`. Only `FOREGROUND_SERVICE`, the required FGS-type permissions (see 6.4), and `POST_NOTIFICATIONS` (runtime-requested on API 33+, app must still work if denied — the service just runs with a muted notification). No storage permissions (SAF + MediaStore make them unnecessary on `minSdk ≥ 29`).
- **N2 Performance:** converting a 10-minute AAC-in-MP4 file should be well under real time on a mid-range device; UI stays responsive (all engine work off the main thread).
- **N3 Robustness:** device rotation, backgrounding, and screen-off must not corrupt or abort a running job.
- **N4 Size:** release APK ≤ 15 MB with Option A engine (soft budget; document the actual size in the README).
- **N5 Accessibility & theming:** Material 3, follows system dark mode, content descriptions on interactive elements, minimum touch targets 48 dp.
- **N6 Localization-ready:** all user-facing strings in `strings.xml` (English only in v1).

---

## 6. Architecture & detailed behavior

### 6.1 Module & package layout (single `:app` module)
```
app/src/main/java/<applicationId>/
  App.kt                     // Application; owns AppContainer
  AppContainer.kt            // manual DI: wires repo, engine, sinks, settings
  ui/
    MainActivity.kt          // single-activity; hosts nav between Main & Settings
    main/  (MainScreen + ViewModel: job list, menu, picker launcher)
    settings/ (SettingsScreen + ViewModel)
    theme/
  jobs/
    ConversionJob.kt         // immutable data + JobState sealed interface
    JobRepository.kt         // in-memory queue; exposes StateFlow<List<ConversionJob>>
    ConversionService.kt     // foreground service; drains queue sequentially
    Notifications.kt
  engine/
    AudioConverter.kt        // interface (contract below)
    MediaCodecLameConverter.kt   // Option A impl (or FfmpegConverter.kt for B)
    jni/ LameEncoder.kt      // thin JNI wrapper (Option A)
  output/
    OutputSink.kt            // interface: open/finalize/abort an output target
    MediaStoreSink.kt        // default Music/ via MediaStore + IS_PENDING
    SafTreeSink.kt           // user-chosen folder via DocumentFile
    FileNaming.kt            // sanitize + collision logic (pure, unit-tested)
  settings/
    SettingsRepository.kt    // DataStore-backed; outputFolder: Default | SafTree(uri)
app/src/main/cpp/            // Option A only: lame_jni.c + CMakeLists.txt
third_party/lame/            // Option A only: vendored LAME 3.100 source (unmodified)
scripts/make_fixtures.sh     // generates test media (macOS, needs Homebrew ffmpeg)
docs/  ARCHITECTURE.md  DECISIONS.md  TESTING.md
```

### 6.2 Key contracts (freeze these in Phase 1 — they enable parallel sub-agents)
```kotlin
data class ConversionJob(
    val id: String,                 // UUID
    val sourceUri: Uri,
    val displayName: String,        // e.g. "Holiday.mp4"
    val state: JobState,
    val createdAt: Long,
)

sealed interface JobState {
    data object Queued : JobState
    data class Running(val progressPercent: Int) : JobState   // 0..100
    data class Done(val outputDescription: String) : JobState // e.g. "Music/Holiday.mp3"
    data class Failed(val reason: FailureReason) : JobState
    data object Cancelled : JobState
}

interface AudioConverter {
    /** Decode source audio and write MP3 bytes to [output]. Blocking; call off-main.
     *  Must poll [isCancelled] at least once per buffer and stop promptly. */
    fun convert(
        context: Context,
        sourceUri: Uri,
        output: OutputStream,
        onProgress: (percent: Int) -> Unit,
        isCancelled: () -> Boolean,
    ): ConverterResult   // Success | Failure(FailureReason)
}

interface OutputSink {
    /** Reserve the target (handles naming collisions) and return a writable stream. */
    fun open(desiredBaseName: String): OpenOutput      // stream + human path + handle
    fun finalize(handle: OutputHandle)                 // clear IS_PENDING / close
    fun abort(handle: OutputHandle)                    // delete partial file
}
```
`JobRepository` owns a `StateFlow<List<ConversionJob>>` (single source of truth for UI and notifications), an `enqueue(uris)` API, `cancel(id)`, `clear(id)`. `ConversionService` observes the queue and processes one Running job at a time on `Dispatchers.Default`.

### 6.3 Engine behavior (Option A specifics)
- `MediaExtractor` selects the **first** track whose MIME starts with `audio/`; if none → `Failed(NoAudioTrack)`.
- `MediaCodec` decoder produces 16-bit PCM; read sample rate/channel count from the **output** `MediaFormat` (it can differ from the input format).
- LAME config: **CBR 192 kbps**, `lame_set_quality(2)`, input sample rate = source (LAME resamples internally to a valid MP3 rate), channels = source. CBR is deliberate: it avoids the VBR Xing-header rewrite, which requires a *seekable* stream that `MediaStore` does not guarantee — do not switch to VBR in v1.
- Channel counts > 2 → `Failed(UnsupportedChannelLayout)` with a friendly message (downmix is a listed future enhancement).
- Progress = `bufferPresentationTimeUs / trackDurationUs`, throttled to ≥ 1 % steps.
- Optional nicety (small): write an ID3v2 title = source base name via LAME's `id3tag_*` API.
- JNI surface (keep it this small): `nativeInit(sampleRate, channels, bitrateKbps): Long`, `nativeEncode(handle, pcm: ShortArray, samplesPerChannel, out: ByteArray): Int`, `nativeFlush(handle, out: ByteArray): Int`, `nativeClose(handle)`.

### 6.4 Foreground service & notifications
- One notification channel ("Conversions"). While converting: ongoing notification with filename, determinate progress, Cancel action. On completion of the whole queue: stop foreground + service; post a short summary notification ("2 files converted").
- **FGS types (classic pitfall — implement exactly):** declare `android:foregroundServiceType="dataSync|mediaProcessing"` in the manifest with both `FOREGROUND_SERVICE_DATA_SYNC` and `FOREGROUND_SERVICE_MEDIA_PROCESSING` permissions. At runtime call `startForeground(...)` with `FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING` on API ≥ 35 and `FOREGROUND_SERVICE_TYPE_DATA_SYNC` on API 29–34. (mediaProcessing exists precisely for transcoding and carries a 6-hour budget — fine for our use.)
- Start the service with `startForegroundService()` from `enqueue()`; request `POST_NOTIFICATIONS` on first enqueue on API 33+.

### 6.5 Failure taxonomy (`FailureReason` → user string)
| Reason | Trigger | UI message |
|---|---|---|
| `NoAudioTrack` | MP4 has no audio track | "This video has no audio track." |
| `UnsupportedAudioCodec` | decoder cannot be created/configured | "The audio format in this file isn't supported by this device." |
| `UnsupportedChannelLayout` | > 2 channels | "Surround-sound audio isn't supported yet." |
| `SourceUnreadable` | URI open fails / permission revoked | "The file could not be read." |
| `OutputFolderUnavailable` | SAF tree missing/permission lost | "The output folder is no longer available — check Settings." (fallback: do **not** silently write elsewhere) |
| `StorageFull` | IOException ENOSPC | "Not enough storage space." |
| `Unknown` | anything else | "Conversion failed." + log via `Log.e` with stack trace |

### 6.6 Storage details
- **Default (Music):** insert into `MediaStore.Audio.Media` with `DISPLAY_NAME`, `MIME_TYPE=audio/mpeg`, `RELATIVE_PATH="Music/"`, `IS_PENDING=1`; stream MP3 bytes; on success set `IS_PENDING=0`; on failure/cancel `delete()` the row. Collision handling in `FileNaming` by querying existing display names first (MediaStore may otherwise auto-rename — we want our deterministic ` (n)` scheme).
- **Custom folder:** persist the tree URI string in DataStore; take `takePersistableUriPermission` (read+write) on selection; write via `DocumentFile.createFile("audio/mpeg", name)`; validate the permission still holds at job start, else `OutputFolderUnavailable`.
- **Accepted v1 limitation (document in README):** the job list is in-memory; if Android kills the process, active jobs stop and the list is empty on next launch (partial files are the only residue when a kill is truly abrupt — `abort()` handles all normal paths).

---

## 7. Repository & documentation deliverables

- `README.md` — what/why, screenshots (placeholder folder `metadata/en-US/images/phoneScreenshots/`, F-Droid-compatible layout), install instructions, **permissions rationale incl. "no INTERNET permission"**, build instructions for macOS, APK size, license.
- `docs/ARCHITECTURE.md` — the diagram/flow from Section 6, threading model, and "why not Media3 / why not FFmpegKit" (copy the reasoning from Section 2 so future contributors don't relitigate it).
- `docs/DECISIONS.md` — resolved D1–D5 with dates.
- `docs/TESTING.md` — how to run each test tier + the manual checklist (Section 9.4).
- `CLAUDE.md` — agent onboarding: build/test/lint commands, module map, contracts in 6.2, conventions ("never edit `third_party/lame`", "strings only via strings.xml", "run `./gradlew spotlessApply test` before finishing any task", "emulator-only: agents run `adb`/Gradle connected tasks against `emulator-*` serials only, never a physical device — see §10.2").
- `CONTRIBUTING.md`, `LICENSE`, `THIRD_PARTY_LICENSES.md` (LAME, and FFmpeg if D1=B), `PRIVACY.md` ("no data leaves the device; the app cannot access the network").
- KDoc on all public types; comments explain *why*, not *what*.

---

## 8. Implementation plan (phased; sub-agent friendly)

> **Ordering:** Phase 0 → 1 are sequential and blocking. Phases 2, 3, 4, 5 are independent once Phase 1's contracts compile, and **may be dispatched to parallel sub-agents** — each sub-agent gets this spec, `docs/DECISIONS.md`, and must not modify Phase 1 contracts (contract changes require returning to the orchestrator). Phases 6–9 are sequential.
> **Every phase ends with:** code compiling, its listed tests green (`./gradlew test` minimum), `spotlessApply` run, and a conventional-commit-style commit.

**Phase 0 — Kickoff & scaffold** *(sequential)*
1. Present D1–D5 to the user; write `docs/DECISIONS.md`.
2. Scaffold the Gradle project (Kotlin DSL, version catalog). Resolve the **latest stable** AGP/Kotlin/Compose-BOM at scaffold time and pin them in `libs.versions.toml`; pin an LTS `ndkVersion` (Option A). Commit the Gradle wrapper. `.gitignore`, `.editorconfig`, Spotless config, empty doc stubs, MIT/GPL license file per D4.
3. ✅ *Done when:* `./gradlew assembleDebug test` succeeds on a clean macOS checkout and an empty "Hello" activity launches on an emulator.

**Phase 1 — Domain core** *(sequential; defines the contracts)*
1. Implement `ConversionJob`, `JobState`, `FailureReason`, `JobRepository` (StateFlow, enqueue/cancel/clear, FIFO selection of next job), `FileNaming` (pure functions), interfaces `AudioConverter`/`OutputSink`, `SettingsRepository` with a `FakeSettings` for tests.
2. Unit tests: queue state transitions (incl. cancel-while-queued vs cancel-while-running), FIFO order, `FileNaming` sanitize + collision cases (`"a/b:c.mp4"`, unicode, 200-char names, collisions to `(3)`).
3. ✅ *Done when:* all Phase 1 classes have JVM unit tests passing; no Android dependencies in `jobs/` except `Uri`.

**Phase 2 — Engine (per D1)** *(parallelizable)*
- Option A: vendor LAME 3.100 + `CMakeLists.txt` + JNI bridge (6.3); implement `MediaCodecLameConverter`; wire `externalNativeBuild`; abis: `arm64-v8a, armeabi-v7a, x86_64` (x86_64 for the emulator).
- Instrumented tests against fixtures (Section 9.2): success (stereo + mono), `NoAudioTrack`, `UnsupportedChannelLayout`, cancellation mid-file leaves sink `abort()`ed, progress is monotonic 0→100.
- ✅ *Done when:* connected tests pass on an API-34/35 emulator on the mac.

**Phase 3 — Output sinks** *(parallelizable)*
- `MediaStoreSink` (IS_PENDING lifecycle, collision pre-query), `SafTreeSink`, both behind `OutputSink`.
- Instrumented tests: create→finalize appears with correct name/MIME; abort leaves no row/file; collision produces ` (1)`.
- ✅ *Done when:* connected tests pass; a smoke test writes a real file visible in the emulator's Files app.

**Phase 4 — Service & notifications** *(parallelizable)*
- `ConversionService` drains the repository using injected fake converter/sink; FGS typing per 6.4; notification with progress + Cancel `PendingIntent`; POST_NOTIFICATIONS flow.
- Tests: unit-test the drain loop with fakes (Robolectric optional); manual checks listed in `TESTING.md`.
- ✅ *Done when:* with a fake 5-second converter, enqueuing 3 jobs shows correct sequential notifications and survives Activity destruction.

**Phase 5 — UI** *(parallelizable)*
- Compose (or Views per D2): MainScreen list per F1 (states, progress, cancel/clear), menu per F2, picker per F3, SettingsScreen per F6. ViewModels talk only to `JobRepository`/`SettingsRepository`.
- Compose UI tests: empty state, one item per state renders correctly, menu navigation, settings shows "Music (default)".
- ✅ *Done when:* UI runs end-to-end against fakes with all F1/F2/F6 elements present.

**Phase 6 — Integration & edge cases** *(sequential)*
- Wire real engine + sinks + service + UI via `AppContainer`. Rotation, background/screen-off during conversion, permission-denied notification path, revoked SAF folder path, multi-select of 5 files, filenames with emoji/diacritics.
- ✅ *Done when:* full manual pass of the checklist (9.4) on an emulator **and** the user's Android 12 device.

**Phase 7 — Test hardening & CI-readiness** *(sequential)*
- Fill coverage gaps; add the end-to-end instrumented test (pick fixture → real MP3 in MediaStore, duration ±5 %, `MediaPlayer.prepare()` succeeds). Verify `./gradlew test connectedDebugAndroidTest lint spotlessCheck assembleRelease` all pass locally. Confirm no absolute paths/secrets are needed (release is debug-signed for now).
- ✅ *Done when:* the single command chain above is green from a fresh clone.

**Phase 8 — Documentation** *(sequential, can start earlier)*
- Write everything in Section 7; take emulator screenshots into the metadata folder; document actual APK size.
- ✅ *Done when:* a newcomer can clone → build → install using only the README.

**Phase 9 — Release prep (GitHub Actions come later, but design for them now)**
- Add `versionCode`/`versionName` scheme (start 1 / "0.1.0"); tag `v0.1.0`.
- Leave a written outline in `docs/RELEASING.md` for the future workflow: `ubuntu-latest`, JDK from catalog, `gradle/actions/setup-gradle`, `assembleRelease`, upload APK artifact to the tag; instrumented tests via an emulator action with KVM; APK signing (keystore via repo secrets) as a follow-up task.
- ✅ *Done when:* `docs/RELEASING.md` exists and a debug-signed release APK from `assembleRelease` installs and converts on the Android 12 device.

---

## 9. Testing plan

### 9.1 Unit tests (JVM, fast, run on every change)
Queue/state machine, `FileNaming`, settings serialization, service drain loop with fakes, progress throttling math. Target: everything in `jobs/`, `output/FileNaming`, `settings/` at ~90 %+ line coverage.

### 9.2 Fixtures
`scripts/make_fixtures.sh` (documented, requires `brew install ffmpeg` on the dev mac — ffmpeg is a *dev tool only*, never shipped) generates tiny committed assets in `app/src/androidTest/assets/`:
- `sine_stereo_aac_3s.mp4` (44.1 kHz stereo AAC, 3.0 s sine sweep)
- `sine_mono_aac_3s.mp4`
- `video_only_no_audio.mp4`
- `sine_6ch_aac_3s.mp4` (drives `UnsupportedChannelLayout`)
Keep total < 200 KB. The script header documents the exact ffmpeg commands for reproducibility.

### 9.3 Instrumented tests (emulator, macOS: ARM64 system images, API 31 and 34/35)
Run on API 31, 34/35 emulators. **API 31 is mandatory before any release** — it matches the primary target hardware (Android 12). Engine tests (Phase 2 list), sink tests (Phase 3 list), and the end-to-end test (Phase 7). MP3 validity assertions: output begins with `ID3` or MPEG frame sync (`0xFF Ex`), `MediaMetadataRetriever` duration within ±5 % of source, `MediaPlayer.prepare()` does not throw.

### 9.4 Manual checklist (docs/TESTING.md; run on Android 12 hardware before each release)
**The human runs this checklist on the physical Android 12 device; the agent only prepares the APK and the checklist and never touches the physical device (see §10.2).** Convert a real phone recording; convert 3 files queued; cancel mid-conversion (no stray file in Music); background the app mid-conversion; deny notifications and convert; change output folder to an SD-card/USB tree and convert; delete that folder and convert (expect the Settings hint error); airplane-mode sanity (app behaves identically — nothing uses the network); dark mode; TalkBack pass over the main screen.

### 9.5 Static checks
`lint` (fail build on new warnings in our code), `spotlessCheck`, and a tiny custom Gradle check asserting the merged manifest contains **no** `android.permission.INTERNET`.

---

## 10. Build environment (macOS)

- Android Studio (current stable) or CLI: `sdkmanager` platforms + build-tools per catalog, platform-tools, emulator, ARM64 system images (API 31, 34, 35); NDK + CMake only for Option A.
- JDK: the version required by the pinned AGP (17 or newer) — record it in the README and `.tool-versions` or `gradle.properties` (`org.gradle.java.home` left unset; document `JAVA_HOME`).
- `brew install ffmpeg` — fixtures only.
- Everything must build from a fresh clone with `./gradlew build` — no machine-local configuration beyond the SDK path in `local.properties` (git-ignored).

### 10.1 Emulator / AVD setup
Development and all automated testing run **exclusively on Android Studio AVDs** (ARM64 Google-APIs system images on Apple Silicon — hardware acceleration is built in). Two AVDs are expected:
- one **API 31 (Android 12)** — matches the primary target hardware;
- one **API 34 or 35**.

Create them via **Device Manager** (Android Studio) or `avdmanager` (CLI). `ANDROID_HOME` plus `platform-tools/` and `emulator/` must be on `PATH` so `adb`, `emulator`, and `sdkmanager` work from the CLI. For test runs, boot headless with:
```
emulator -avd <name> -no-window -no-boot-anim
```

### 10.2 Device policy — emulator only (hard rule)
**Agents may only ever run `adb`/Gradle connected tasks against emulator devices** (serials starting with `emulator-`). Agents must **never** install to, shell into, or otherwise modify a physical device. A physical Android 12 phone exists but is used **exclusively by the human** for the manual checklist in Section 9.4.

## 11. Future ideas (explicitly out of scope for v1 — keep a `docs/ROADMAP.md` stub)
Share-sheet integration (appear under "Open with"/Send for video files); more input containers and output formats; bitrate/VBR settings; >2-channel downmix; parallel jobs setting; persisted job history; F-Droid submission; localization; GitHub Actions release workflow with real APK signing.
