# Architecture

> **Stub.** Filled out in Phase 8. The authoritative design lives in
> `MP4toMP3converter-SPEC.md` §6 until then.

## Overview

Single `:app` module, lightweight MVVM, manual DI via `AppContainer`, in-memory job
queue, DataStore for settings. Kotlin + coroutines/Flow throughout.

## Why not Media3 Transformer / FFmpegKit

(Copied here in Phase 8 from spec §2 so contributors don't relitigate it.)

- **Media3 Transformer cannot output MP3** — Android has no system MP3 encoder.
- **FFmpegKit is retired** (Jan 2025, prebuilt artifacts removed) — do not depend on it.

The engine is therefore `MediaExtractor` + `MediaCodec` decode → **LAME** encode via
NDK/JNI (decision D1; see `docs/DECISIONS.md`).

## Threading model

Engine work runs off the main thread on `Dispatchers.Default`; the UI observes a
single `StateFlow<List<ConversionJob>>` from `JobRepository`.

## Component map

See the module/package layout in `MP4toMP3converter-SPEC.md` §6.1.
