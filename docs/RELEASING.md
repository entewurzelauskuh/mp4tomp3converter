# Releasing

## Versioning

- `versionCode` / `versionName` live in `app/build.gradle.kts`. v1 starts at **1** / **`0.1.0`**.
- Bump `versionCode` on every release; bump `versionName` per semver-ish intent.
- Tag releases `vX.Y.Z` (e.g. `v0.1.0`).

## Current state

- `assembleRelease` produces a **debug-signed**, R8-shrunk APK (~3 MB) that installs and
  converts. Proper release signing (a real keystore) is a follow-up (see below).
- Before tagging, the full gate must be green from a clean checkout:
  ```sh
  ./gradlew test connectedDebugAndroidTest lint spotlessCheck assembleRelease
  ```
  Instrumented tests run on an **emulator only** (API 31 is mandatory — it matches the primary
  target hardware). The human then runs the manual checklist (`docs/TESTING.md`, spec §9.4) on
  the physical Android 12 device.

## Planned GitHub Actions workflow (design outline)

Not yet implemented — designed for when it's added:

- Runner: `ubuntu-latest`, JDK 21 (Temurin) matching the build host, `gradle/actions/setup-gradle`.
- Build: `./gradlew assembleRelease`, upload the APK as a release artifact attached to the tag.
- Instrumented tests: an emulator action with KVM (e.g. `reactivecircus/android-emulator-runner`)
  on API 31 and 34/35.
- **APK signing** (follow-up): store a real keystore + passwords as encrypted repo secrets,
  add a `release` `signingConfig` reading them, and drop the debug-signing fallback. Until then
  releases are debug-signed and clearly marked as such.

## F-Droid (future)

Screenshots live under `metadata/en-US/images/phoneScreenshots/` in the F-Droid-compatible
layout. Avoid prebuilt binary blobs — LAME is built from vendored source, which keeps an F-Droid
submission viable.
