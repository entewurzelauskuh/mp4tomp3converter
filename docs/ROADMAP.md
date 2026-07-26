# Roadmap

Explicitly **out of scope for v1** (spec §11). Kept here so v1 stays narrow.

> Shipped in 1.0.0 (previously roadmap/enhancement items): share-sheet integration, trim to a
> time range, ID3 tags, estimated output size, delete original, "Clear all",
> About screen, tap-empty-to-convert. Bitrate selection shipped in 0.2.0.

- More input containers (MKV/AVI/WebM) and output formats (AAC/OGG/WAV).
- VBR quality (v1 is CBR-only — a Xing header needs a seekable stream MediaStore doesn't guarantee).
- \> 2-channel (surround) downmix.
- Parallel-jobs setting (v1 is strictly sequential, FIFO).
- Persisted job history across process death.
- F-Droid submission.
- Localization (v1 is English-only).
- Real APK **release signing** (a proper keystore). The release workflow itself exists but ships a
  debug-signed APK for now — see `docs/RELEASING.md`.
