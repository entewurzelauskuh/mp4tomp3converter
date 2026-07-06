package io.github.entewurzelauskuh.mp4tomp3.jobs

import io.github.entewurzelauskuh.mp4tomp3.engine.AudioConverter
import io.github.entewurzelauskuh.mp4tomp3.output.OutputSink
import io.github.entewurzelauskuh.mp4tomp3.settings.OutputTarget
import io.github.entewurzelauskuh.mp4tomp3.settings.SettingsRepository

/**
 * The collaborators [ConversionService] needs to drain the queue, obtained from the
 * [android.app.Application].
 *
 * The service has no constructor injection point (Android instantiates it), so instead of
 * the Phase 6 `AppContainer` it reads its dependencies from `application as
 * ConversionDependencies`. **Phase 6's `App`/`AppContainer` implements this interface** and
 * wires the real [JobRepository], the LAME-backed [AudioConverter], the [DataStoreSettingsRepository]
 * and a [sinkProvider] that returns `MediaStoreSink` or `SafTreeSink` per the given
 * [OutputTarget]. Until then the interface is the seam that lets Phase 4 compile and be
 * tested without depending on those Phase 2/3/6 classes.
 *
 * Kept in `jobs/` (not `app/`) so the service can reference it without an upward package
 * dependency; it deliberately exposes only interface types.
 */
interface ConversionDependencies {
    /** The single source of truth for jobs (also observed by the UI). */
    val repository: JobRepository

    /** The engine that decodes MP4 audio and encodes MP3 (Phase 2). */
    val converter: AudioConverter

    /**
     * Resolves the [OutputSink] for a given [OutputTarget]: the MediaStore sink for
     * [OutputTarget.Default], a SAF-tree sink for [OutputTarget.SafTree] (Phase 3/6).
     * A factory rather than a single sink because the target can change between jobs.
     */
    val sinkProvider: (OutputTarget) -> OutputSink

    /** Persisted settings; the drainer reads the current [OutputTarget] per job. */
    val settings: SettingsRepository
}
