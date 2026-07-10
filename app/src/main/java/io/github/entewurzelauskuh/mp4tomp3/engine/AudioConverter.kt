package io.github.entewurzelauskuh.mp4tomp3.engine

import android.content.Context
import android.net.Uri
import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionOptions
import io.github.entewurzelauskuh.mp4tomp3.jobs.FailureReason
import java.io.OutputStream

/**
 * Decodes the audio track of an MP4 source and writes MP3 bytes to an [OutputStream].
 *
 * Originally a frozen §6.2 contract; the [options] parameter was added deliberately (issue #5) as
 * the forward-compatible carrier for per-conversion encoding settings, so future features add
 * fields to [ConversionOptions] without re-touching this signature. The default implementation is
 * `MediaExtractor` + `MediaCodec` decode → LAME encode (decision D1).
 */
interface AudioConverter {
    /**
     * Decode [sourceUri]'s audio and write MP3 bytes to [output] using [options]. **Blocking** —
     * call off the main thread. Implementations must poll [isCancelled] at least once per buffer
     * and stop promptly (leaving the caller to abort the output). [onProgress] receives an
     * integer percent `0..100`, throttled to ≥ 1% steps.
     *
     * Never throws for expected failures — returns [ConverterResult.Failure] with a
     * categorised [FailureReason] instead.
     *
     * @param output the destination stream; the converter writes but does not close it.
     * @param options per-conversion encoding options (e.g. bitrate); defaults to standard settings.
     */
    fun convert(
        context: Context,
        sourceUri: Uri,
        output: OutputStream,
        options: ConversionOptions = ConversionOptions.Default,
        onProgress: (percent: Int) -> Unit,
        isCancelled: () -> Boolean,
    ): ConverterResult
}

/** Outcome of [AudioConverter.convert]. */
sealed interface ConverterResult {
    /** The MP3 was fully written. */
    data object Success : ConverterResult

    /** Conversion failed for the given [reason]. */
    data class Failure(val reason: FailureReason) : ConverterResult
}
