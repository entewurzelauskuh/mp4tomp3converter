# Architecture

Single `:app` module, lightweight MVVM, Kotlin + coroutines/Flow, manual DI (no framework),
in-memory job queue, DataStore for settings. The authoritative spec is
`MP4toMP3converter-SPEC.md` §6.

## Data flow

```
 UI (Compose)                jobs/                         engine/ + output/
 ┌───────────────┐  enqueue  ┌────────────────────┐  drains ┌───────────────────────┐
 │ MainScreen /  │──────────▶│ JobRepository       │◀───────│ ConversionService     │
 │ MainViewModel │  cancel   │  StateFlow<List<Job>>│ start  │  └ QueueDrainer        │
 │               │◀──────────│  (single source of  │───────▶│      converter.convert │
 │ collectAsState│  observe  │   truth)            │        │      sink.open/finalize│
 └───────────────┘           └────────────────────┘        └───────────────────────┘
        │                                                        │            │
        │ SettingsViewModel ──▶ SettingsRepository (DataStore)   │            ▼
        │                                                        ▼    OutputSink
        └──────────────────────────────────────────▶ AudioConverter   ├ MediaStoreSink (Music/)
                                                      (MediaCodec+LAME) └ SafTreeSink (chosen folder)
```

- **`JobRepository`** owns a `StateFlow<List<ConversionJob>>` — the single source of truth for
  both the UI and the notification. `enqueue`/`cancel`/`clear` for the UI; `nextQueued` +
  `markRunning`/`updateProgress`/`markDone`/`markFailed`/`markCancelled` for the service.
- **`ConversionService`** is a foreground service that hosts the unit-tested **`QueueDrainer`**,
  which processes one job at a time (FIFO) on `Dispatchers.Default`: pick the sink for the
  current `OutputTarget`, `open` it, run the blocking `AudioConverter`, then `finalize` on
  success or `abort` on failure/cancel.
- **`AppContainer`** (owned by `App`) wires the real implementations and implements
  `ConversionDependencies`, which the service reads via `application as ConversionDependencies`
  (Android instantiates services, so there is no constructor injection).

## Threading

All engine work is off the main thread (`Dispatchers.Default`); the UI only ever observes the
repository's `StateFlow`. The service survives Activity destruction, so rotation/backgrounding
does not abort a conversion.

## The engine (decision D1)

`MediaExtractor` selects the first `audio/*` track → `MediaCodec` decodes to 16-bit PCM →
**LAME** encodes MP3 (CBR 192, quality 2), streamed to the output sink. LAME 3.100 is vendored
under `third_party/lame/` and built by CMake into `liblame.so`; `lame_jni.c` is a tiny JNI
bridge (`liblame_jni.so`) that links dynamically against it.

## Why not Media3 Transformer / FFmpeg (do not relitigate — spec §2)

1. **Android has no system MP3 encoder.** `MediaCodec` and Jetpack **Media3 Transformer cannot
   output MP3** (only AAC). MP3 output *requires* bundling an encoder — hence LAME + NDK/JNI.
2. **FFmpegKit is dead** (retired Jan 2025; prebuilt artifacts removed from Maven/GitHub). A
   `com.arthenica:ffmpeg-kit` dependency 404s. FFmpeg would mean self-building for 4 ABIs (much
   larger APK, harder F-Droid story) or trusting an unmaintained fork.

Also rejected: executing an `ffmpeg` CLI binary via `Process` (fragile under modern exec
restrictions — the approach that rotted in the predecessor app).

## Accepted v1 limitations

The job list is **in-memory**: if Android kills the process, active jobs stop and the list is
empty on next launch. `abort()` cleans up partial output on all normal paths; a partial file is
the only residue after a truly abrupt kill.
