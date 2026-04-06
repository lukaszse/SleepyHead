package com.example.androidapp.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sqrt

class HrvCalculatorTest {

    // ── Constants ─────────────────────────────────────────────────────

    @Test
    fun `WINDOW_MS is 5 minutes in milliseconds`() {
        assertEquals(300_000L, HrvCalculator.WINDOW_MS)
    }

    @Test
    fun `MIN_INTERVALS is 20`() {
        assertEquals(20, HrvCalculator.MIN_INTERVALS)
    }

    // ── Edge cases returning null ────────────────────────────────────

    @Test
    fun `rmssd returns null for empty list`() {
        assertNull(HrvCalculator.rmssd(emptyList()))
    }

    @Test
    fun `rmssd returns null for single interval`() {
        assertNull(HrvCalculator.rmssd(listOf(800)))
    }

    @Test
    fun `rmssd returns null for fewer than MIN_INTERVALS`() {
        val intervals = List(19) { 800 }
        assertNull(HrvCalculator.rmssd(intervals))
    }

    @Test
    fun `rmssd returns null for exactly MIN_INTERVALS minus one`() {
        val intervals = List(HrvCalculator.MIN_INTERVALS - 1) { 800 }
        assertNull(HrvCalculator.rmssd(intervals))
    }

    // ── Valid computations ──────────────────────────────────────────

    @Test
    fun `rmssd returns value for exactly MIN_INTERVALS intervals`() {
        val intervals = List(HrvCalculator.MIN_INTERVALS) { 800 + it }
        val result = HrvCalculator.rmssd(intervals)
        // All successive differences are 1 ms → RMSSD = √(1²/1) = 1.0
        assertEquals(1.0, result!!, 0.001)
    }

    @Test
    fun `rmssd of constant intervals is zero`() {
        val intervals = List(30) { 800 }
        val result = HrvCalculator.rmssd(intervals)
        assertEquals(0.0, result!!, 0.001)
    }

    @Test
    fun `rmssd computed correctly for known values`() {
        // Intervals: 800, 810, 790, 830, 780, ... (20 values)
        val intervals = listOf(
            800, 810, 790, 830, 780,
            820, 795, 815, 805, 810,
            790, 800, 825, 785, 810,
            800, 815, 790, 805, 810
        )
        assertEquals(20, intervals.size)

        // Manually compute expected RMSSD
        val diffs = intervals.zipWithNext { a, b -> (b - a).toDouble() }
        val expectedRmssd = sqrt(diffs.map { it * it }.average())

        val result = HrvCalculator.rmssd(intervals)
        assertEquals(expectedRmssd, result!!, 0.0001)
    }

    @Test
    fun `rmssd handles large dataset correctly`() {
        // Simulate 300 RR intervals (~5 min at ~60 bpm)
        val intervals = (1..300).map { 800 + (it % 10) * 5 }
        val result = HrvCalculator.rmssd(intervals)

        // Verify manually
        val diffs = intervals.zipWithNext { a, b -> (b - a).toDouble() }
        val expected = sqrt(diffs.map { it * it }.average())

        assertEquals(expected, result!!, 0.0001)
    }

    @Test
    fun `rmssd handles alternating intervals`() {
        // Alternating 800 and 850 → all diffs are ±50
        val intervals = List(30) { if (it % 2 == 0) 800 else 850 }
        val result = HrvCalculator.rmssd(intervals)

        // All |diff| = 50, so RMSSD = √(2500) = 50.0
        assertEquals(50.0, result!!, 0.001)
    }

    @Test
    fun `rmssd handles linearly increasing intervals`() {
        // 800, 802, 804, ..., 838 (20 values, step = 2)
        val intervals = List(20) { 800 + it * 2 }
        val result = HrvCalculator.rmssd(intervals)

        // All successive differences are 2 → RMSSD = √(4) = 2.0
        assertEquals(2.0, result!!, 0.001)
    }
}

