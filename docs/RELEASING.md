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

## GitHub Actions workflows

Two workflows live under `.github/workflows/`:

- **`ci.yml`** (push to `main`, PRs). A `check` job on `ubuntu-latest` (JDK 21 Temurin,
  `gradle/actions/setup-gradle`) runs `./gradlew check` — Spotless, lint, unit tests, and the
  `assertNo*Internet` privacy check. It runs no native task, but AGP resolves `ndkVersion` at
  configuration time, so every job installs the NDK via the shared
  `./.github/actions/install-ndk` composite (the single source of truth for those pins). An
  `instrumented` job runs
  `connectedDebugAndroidTest` on `reactivecircus/android-emulator-runner` (KVM) across an API
  31 **and** 34 matrix, building only the `x86_64` native libs (`-Pabi=x86_64`) since that's the
  emulator ABI.
- **`release.yml`** (on a `v*` tag). Builds `./gradlew assembleRelease` (all ABIs) and attaches
  the APK to the tag's GitHub Release as `mp4tomp3converter-<tag>-debug-signed.apk`.

> **NDK/CMake pins are bleeding-edge.** The workflows `sdkmanager`-install the exact
> `ndkVersion` (**r30-beta1**, `30.0.14904198`) and CMake (`4.1.2`) the build requires. If a
> hosted runner can no longer fetch a beta package, bump those pins **deliberately** in
> `app/build.gradle.kts` (and the version catalog) rather than freezing stale copies in CI.

**APK signing** (follow-up): store a real keystore + passwords as encrypted repo secrets, add a
`release` `signingConfig` reading them, and drop the debug-signing fallback. Until then releases
are debug-signed and clearly marked as such (including in the CI-published asset name).

## F-Droid (future)

Screenshots live under `metadata/en-US/images/phoneScreenshots/` in the F-Droid-compatible
layout. Avoid prebuilt binary blobs — LAME is built from vendored source, which keeps an F-Droid
submission viable.
