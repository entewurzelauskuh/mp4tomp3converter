package io.github.entewurzelauskuh.mp4tomp3.engine

/**
 * Turns a stream of raw percent readings (`bufferPresentationTimeUs / trackDurationUs`)
 * into a **monotonic**, throttled sequence for the UI: values are clamped to `0..100`,
 * never regress, and are suppressed unless they advance by at least [minStepPercent]
 * (spec §6.3). `100` is always allowed through so completion is never swallowed.
 *
 * Pure and stateful-per-instance; created fresh per conversion by the Phase 2 engine.
 * Not thread-safe — call from a single decode loop.
 */
class ProgressThrottler(private val minStepPercent: Int = 1) {
    private var lastEmitted = -1

    /**
     * @return the percent to report, or `null` to suppress this reading (unchanged, a
     *   regression, or below the [minStepPercent] threshold and not yet complete).
     */
    fun onProgress(rawPercent: Int): Int? {
        val clamped = rawPercent.coerceIn(0, 100)
        if (clamped <= lastEmitted) return null
        if (clamped < 100 && lastEmitted >= 0 && clamped - lastEmitted < minStepPercent) return null
        lastEmitted = clamped
        return clamped
    }
}
