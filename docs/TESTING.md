# Testing

> **Stub.** Expanded as test tiers land (Phases 1–7). Full plan: spec §9.

## Test tiers

| Tier | Command | Runs on |
|------|---------|---------|
| JVM unit | `./gradlew test` | host JVM (fast) |
| Instrumented | `./gradlew connectedDebugAndroidTest` | **emulator only** (API 31 and 34/35) |
| Static | `./gradlew lint spotlessCheck` | host |

### Single unit-test class
```sh
./gradlew test --tests "*FileNamingTest"
```

### Full green-from-clean gate (spec §7 Phase 7)
```sh
./gradlew test connectedDebugAndroidTest lint spotlessCheck assembleRelease
```

## Emulator-only policy (hard rule, spec §10.2)

Automated `adb`/Gradle connected tasks run **only** against emulator devices (serials
starting with `emulator-`). Never against a physical device. Two AVDs are used:
API 31 (Android 12, primary target) and API 34/35. Boot headless:
```sh
emulator -avd <name> -no-window -no-boot-anim
```

## Fixtures

`scripts/make_fixtures.sh` (added in Phase 2; needs `brew install ffmpeg`, a dev tool
only — never shipped) generates the tiny committed media assets under
`app/src/androidTest/assets/`. See spec §9.2.

## Automated coverage (current)

- **JVM unit (42):** `JobRepository` queue/state machine, `QueueDrainer` drain loop (with
  fakes), `FileNaming` sanitise/collision, `ProgressThrottler`, settings serialization,
  in-memory settings.
- **Instrumented (13, emulator):** engine conversion (stereo/mono → valid MP3 with monotonic
  progress, `NoAudioTrack`, `UnsupportedChannelLayout`, cancellation), `MediaStoreSink`
  (create/finalize/abort/collision), Compose UI (empty state, per-state rendering, menu
  navigation, settings default), and the end-to-end test (fixture → real MP3 in MediaStore,
  duration within ±5%, `MediaPlayer.prepare()` succeeds).

## Manual checklist (physical Android 12 device — run by the human only)

The human runs this on the physical phone before each release; the agent only prepares the APK
and this checklist and never touches the physical device (spec §9.4, §10.2).

- [ ] Convert a real phone recording (`.mp4` with AAC audio) → plays back correctly.
- [ ] Queue 3 files → they convert sequentially with correct notifications.
- [ ] Cancel mid-conversion → job shows Cancelled and **no** stray file remains in Music.
- [ ] Background the app mid-conversion → it keeps going and finishes.
- [ ] Deny the notification permission, then convert → still works (muted notification).
- [ ] Change the output folder to an SD-card/USB tree and convert → file lands there.
- [ ] Delete that chosen folder, then convert → shows the "output folder unavailable" error
      (does **not** silently write elsewhere).
- [ ] Airplane mode → app behaves identically (nothing uses the network).
- [ ] Dark mode looks correct.
- [ ] TalkBack pass over the main screen (labels/content descriptions read sensibly).
