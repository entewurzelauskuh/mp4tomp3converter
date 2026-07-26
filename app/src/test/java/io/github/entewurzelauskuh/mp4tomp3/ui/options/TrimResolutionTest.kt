package io.github.entewurzelauskuh.mp4tomp3.ui.options

import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trim-window resolution shared by the options screen's size estimate and its built options. */
class TrimResolutionTest {
    @Test
    fun untouchedFullLengthStaysFull() {
        // A whole-second duration, end left at the duration → full (the sentinel).
        val r = resolveTrim(startMs = 0L, endMs = 10_000L, durationMs = 10_000L)
        assertTrue(r.isFull)
        assertEquals(ConversionOptions.TRIM_END_FULL, r.optionEndMs)
        assertEquals(10_000L, r.spanMs)
    }

    @Test
    fun endAtLastWholeSecondOfNonRoundClipIsFullNotASubSecondTrim() {
        // Regression (review F1): 32:42.345 clip; the End field shows/commits the floored 32:42.
        // That must resolve to full length, not a trim that shaves the last 0.345 s.
        val r = resolveTrim(startMs = 0L, endMs = 1_962_000L, durationMs = 1_962_345L)
        assertTrue(r.isFull)
        assertEquals(ConversionOptions.TRIM_END_FULL, r.optionEndMs)
    }

    @Test
    fun interiorEndIsARealTrim() {
        val r = resolveTrim(startMs = 5_000L, endMs = 60_000L, durationMs = 1_962_345L)
        assertFalse(r.isFull)
        assertEquals(5_000L, r.startMs)
        assertEquals(60_000L, r.optionEndMs)
        assertEquals(55_000L, r.spanMs)
    }

    @Test
    fun equalThumbsConvertFullFromStartWithMatchingSpan() {
        // Regression (review F2): stacking both thumbs must not read as a 0-length window; it
        // converts start→end-of-file, and the span reflects that (so the estimate matches output).
        val r = resolveTrim(startMs = 50_000L, endMs = 50_000L, durationMs = 100_000L)
        assertTrue(r.isFull)
        assertEquals(50_000L, r.startMs)
        assertEquals(50_000L, r.spanMs)
    }

    @Test
    fun startIsClampedInsideTheClip() {
        val r = resolveTrim(startMs = 999_999L, endMs = 0L, durationMs = 100_000L)
        assertEquals(99_999L, r.startMs)
        assertTrue(r.isFull) // end ≤ start → full-from-start
    }
}
