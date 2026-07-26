package io.github.entewurzelauskuh.mp4tomp3.ui.options

import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionOptions

/**
 * The trim window that will actually be encoded, resolved from the raw slider/field start & end.
 *
 * @property startMs clamped start of the range.
 * @property endMs actual end in ms — equal to the clip duration when the range runs to the end.
 * @property isFull true when the range covers the whole clip (no end trim).
 */
internal data class ResolvedTrim(val startMs: Long, val endMs: Long, val isFull: Boolean) {
    /** Length of the resolved window in ms (never negative) — the basis for the size estimate. */
    val spanMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

    /** The value for [ConversionOptions.trimEndMs]: the sentinel when full, else the real end. */
    val optionEndMs: Long get() = if (isFull) ConversionOptions.TRIM_END_FULL else endMs
}

/**
 * Resolve the user's raw start & end (ms) against the clip [durationMs] into the window that will be
 * encoded — the single source of truth shared by the size estimate and the built [ConversionOptions],
 * so the two can never disagree.
 *
 * Because the `mm:ss` fields are second-granular, an end at or past the clip's **last whole second**
 * counts as "to the end" (full length): otherwise merely focusing the End field would re-commit the
 * floored time and silently shave the sub-second tail off every non-round-length clip (flipping
 * [ConversionOptions.isTrimmed] on). A degenerate or reversed selection (end ≤ start) likewise means
 * full-from-start rather than a zero-length trim.
 */
internal fun resolveTrim(startMs: Long, endMs: Long, durationMs: Long): ResolvedTrim {
    val start = startMs.coerceIn(0L, (durationMs - 1L).coerceAtLeast(0L))
    val lastWholeSecond = durationMs - (durationMs % 1000L)
    return if (endMs in (start + 1L) until lastWholeSecond) {
        ResolvedTrim(start, endMs, isFull = false)
    } else {
        ResolvedTrim(start, durationMs, isFull = true)
    }
}
