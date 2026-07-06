# Privacy Policy

**MP4 to MP3 Converter collects nothing and sends nothing. It cannot.**

- The app does **not** request the `INTERNET` permission, so it has no ability to access the
  network. Nothing you convert — or any other data — ever leaves your device. A build-time
  check fails the build if `INTERNET` ever appears in the merged manifest.
- No accounts, no analytics, no ads, no telemetry, no crash reporting.
- Files you pick are read locally to produce the `.mp3`; the output is written only to the
  location you choose (the public Music folder by default, or a folder you select).
- The app keeps a list of conversion jobs **in memory only** for the current session; it is not
  persisted and is gone when the app process ends.

Permissions requested, and why:

- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` / `FOREGROUND_SERVICE_MEDIA_PROCESSING`
  — to keep converting while the app is in the background.
- `POST_NOTIFICATIONS` (Android 13+) — to show conversion progress. The app works fine if you
  deny it.

Questions: open an issue on the project's repository.
