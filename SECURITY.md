# Security Policy

## Supported versions

This project is pre-1.0; only the latest release receives fixes.

| Version | Supported |
|---------|-----------|
| 0.1.x   | ✅        |
| < 0.1   | ❌        |

## Reporting a vulnerability

Please **do not** open a public issue for security problems.

Use GitHub's private vulnerability reporting instead — **Security → Report a
vulnerability**, or the [advisories page](https://github.com/entewurzelauskuh/mp4tomp3converter/security/advisories/new).
This opens a private channel with the maintainer. You'll get an acknowledgement as
soon as the report is seen. There is no bounty program — this is a small, free,
volunteer project.

## Threat model (what's in scope)

The app is deliberately minimal, which shrinks the attack surface:

- **No network access.** It never requests the `INTERNET` permission (enforced by a
  build-time check), collects no data, and talks to no server — network-based
  attacks do not apply.
- The realistic area of concern is **local media parsing**: a malicious `.mp4` fed
  through the platform decoder and the bundled native **LAME** encoder
  (`liblame.so` / `app/src/main/cpp/lame_jni.c`). Memory-safety issues in that
  native path are the most valuable thing to report.
- Output handling via `MediaStore` and the Storage Access Framework is also in scope.

Out of scope: anything requiring a rooted device or physical access, and the
debug-signing of current release APKs (a known, documented limitation — see
`docs/RELEASING.md`).
