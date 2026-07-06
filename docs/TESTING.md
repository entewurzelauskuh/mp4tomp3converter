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

## Manual checklist (physical Android 12 device — run by the human only)

The human runs the spec §9.4 checklist on the physical phone before each release; the
agent only prepares the APK and this checklist and never touches the physical device.
