package io.github.entewurzelauskuh.mp4tomp3.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressThrottlerTest {
    @Test
    fun emitsFirstReadingIncludingZero() {
        assertEquals(0, ProgressThrottler().onProgress(0))
    }

    @Test
    fun defaultStepEmitsEveryWholePercent() {
        val t = ProgressThrottler()
        assertEquals(0, t.onProgress(0))
        assertEquals(1, t.onProgress(1))
        assertEquals(2, t.onProgress(2))
    }

    @Test
    fun suppressesRepeatsAndRegressions() {
        val t = ProgressThrottler()
        assertEquals(50, t.onProgress(50))
        assertNull(t.onProgress(50)) // unchanged
        assertNull(t.onProgress(30)) // regression
        assertEquals(51, t.onProgress(51))
    }

    @Test
    fun honorsMinimumStep() {
        val t = ProgressThrottler(minStepPercent = 5)
        assertEquals(0, t.onProgress(0))
        assertNull(t.onProgress(2))
        assertNull(t.onProgress(4))
        assertEquals(5, t.onProgress(5))
        assertEquals(50, t.onProgress(50))
    }

    @Test
    fun alwaysEmitsCompletionEvenBelowStep() {
        val t = ProgressThrottler(minStepPercent = 10)
        assertEquals(0, t.onProgress(0))
        assertEquals(99, t.onProgress(99))
        assertEquals(100, t.onProgress(100)) // 100 - 99 < 10, still emitted
    }

    @Test
    fun clampsOutOfRangeInputs() {
        val t = ProgressThrottler()
        assertEquals(0, t.onProgress(-10))
        assertEquals(100, t.onProgress(9999))
        assertNull(t.onProgress(100)) // already at 100
    }

    @Test
    fun sequenceIsMonotonicNonDecreasing() {
        val t = ProgressThrottler()
        val raw = listOf(0, 0, 1, 1, 2, 1, 5, 4, 100, 100)
        val emitted = raw.mapNotNull { t.onProgress(it) }
        assertEquals(emitted.sorted(), emitted) // never decreases
        assertEquals(listOf(0, 1, 2, 5, 100), emitted)
    }
}
