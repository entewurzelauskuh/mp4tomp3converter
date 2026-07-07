## What & why

<!-- A short description of the change and the motivation behind it. -->

Fixes #<!-- issue number, if applicable -->

## How it was tested

<!-- Commands run, emulators/devices used. -->

- [ ] `./gradlew spotlessApply check` passes
- [ ] `./gradlew connectedDebugAndroidTest` passes on an emulator (if touching `engine/`, `output/`, or the UI)

## Checklist

- [ ] No new `INTERNET` permission or any network access (hard rule)
- [ ] User-facing strings are in `strings.xml` (no hardcoded UI text)
- [ ] Follows the conventions in `CLAUDE.md` and the spec
- [ ] Frozen §6.2 contracts (`ConversionJob`, `AudioConverter`, `OutputSink`, `JobRepository`, …) are unchanged — or the change is explained above
- [ ] Vendored LAME source under `third_party/lame/` is untouched
