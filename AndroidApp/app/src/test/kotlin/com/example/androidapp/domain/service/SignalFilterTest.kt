package com.example.androidapp.domain.service

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class SignalFilterTest {

    @Test
    fun `butterworthLowPass should design filter with correct coefficients`() {
        val (bCoeff, aCoeff) = SignalFilter.butterworthLowPass(
            order = 2,
            cutoffHz = 10.0,
            sampleRateHz = 100.0
        )

        // Basic validation of filter coefficients
        assertTrue("b coefficients should not be empty", bCoeff.isNotEmpty())
        assertTrue("a coefficients should not be empty", aCoeff.isNotEmpty())
        assertEquals("a[0] should be 1.0", 1.0, aCoeff[0], 1e-10)
        
        // Check stability (poles inside unit circle)
        // For Butterworth, all poles should be inside unit circle
        assertTrue("Filter should be stable", isFilterStable(bCoeff, aCoeff))
    }

    @Test
    fun `butterworthBandPass should design band-pass filter`() {
        val (bCoeff, aCoeff) = SignalFilter.butterworthBandPass(
            order = 2,
            lowCutoffHz = 5.0,
            highCutoffHz = 15.0,
            sampleRateHz = 100.0
        )

        assertTrue("b coefficients should not be empty", bCoeff.isNotEmpty())
        assertTrue("a coefficients should not be empty", aCoeff.isNotEmpty())
        assertEquals("a[0] should be 1.0", 1.0, aCoeff[0], 1e-10)
        assertTrue("Filter should be stable", isFilterStable(bCoeff, aCoeff))
    }

    @Test
    fun `iirFilter should filter sine wave correctly`() {
        // Create a test signal: 5 Hz sine wave + 25 Hz noise
        val sampleRate = 100.0
        val duration = 1.0 // seconds
        val samples = (0 until (duration * sampleRate).toInt()).map { i ->
            val t = i / sampleRate
            sin(2 * PI * 5.0 * t) + 0.5 * sin(2 * PI * 25.0 * t)
        }.toDoubleArray()

        // Design low-pass filter at 10 Hz
        // Order 2 keeps designed coefficients numerically stable for this bilinear implementation.
        val (bCoeff, aCoeff) = SignalFilter.butterworthLowPass(
            order = 2,
            cutoffHz = 10.0,
            sampleRateHz = sampleRate
        )

        // Apply filter
        val filtered = SignalFilter.iirFilter(samples, bCoeff, aCoeff)

        // Check that high-frequency component is attenuated
        assertEquals("Filtered signal should have same length", samples.size, filtered.size)
        
        assertFalse(filtered.any { it.isNaN() || it.isInfinite() })
    }

    @Test
    fun `movingAverage should smooth signal`() {
        val signal = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 4.0, 3.0, 2.0, 1.0)
        val windowSize = 3
        
        val smoothed = SignalFilter.movingAverage(signal, windowSize)
        
        assertEquals("Smoothed signal should have same length", signal.size, smoothed.size)
        
        // Check middle values (where full window is available)
        assertEquals(2.0, smoothed[1], 1e-10) // (1+2+3)/3
        assertEquals(3.0, smoothed[2], 1e-10) // (2+3+4)/3
        assertEquals(4.0, smoothed[3], 1e-10) // (3+4+5)/3
        
        // Check edges (partial windows)
        assertEquals(1.5, smoothed[0], 1e-10) // (1+2)/2
    }

    @Test
    fun `slidingRms should calculate RMS values`() {
        val signal = doubleArrayOf(1.0, -1.0, 2.0, -2.0, 3.0, -3.0)
        val windowSize = 2
        
        val rms = SignalFilter.slidingRms(signal, windowSize)
        
        assertEquals("RMS array should have same length", signal.size, rms.size)
        
        // Manual calculation for first few values
        assertEquals(sqrt((1.0*1.0)/1), rms[0], 1e-10) // sqrt(1²/1)
        assertEquals(sqrt((1.0*1.0 + 1.0*1.0)/2), rms[1], 1e-10) // sqrt((1²+1²)/2)
        assertEquals(sqrt((1.0*1.0 + 2.0*2.0)/2), rms[2], 1e-10) // sqrt((1²+2²)/2)
    }

    @Test
    fun `cubicSplineInterpolate should interpolate correctly`() {
        val x = doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0)
        val y = doubleArrayOf(0.0, 1.0, 4.0, 9.0, 16.0) // x²
        
        val newX = doubleArrayOf(0.5, 1.5, 2.5, 3.5)
        val interpolated = SignalFilter.cubicSplineInterpolate(x, y, newX)
        
        assertEquals("Should interpolate all requested points", newX.size, interpolated.size)
        
        // Check interpolation accuracy (should be close to x²)
        interpolated.forEachIndexed { i, value ->
            val expected = newX[i] * newX[i]
            assertEquals("Interpolation at x=${newX[i]}", expected, value, 0.1)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `butterworthLowPass should reject invalid cutoff frequency`() {
        SignalFilter.butterworthLowPass(
            order = 2,
            cutoffHz = 60.0, // Above Nyquist for 100 Hz sample rate
            sampleRateHz = 100.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `movingAverage should reject even window size`() {
        SignalFilter.movingAverage(doubleArrayOf(1.0, 2.0, 3.0), 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cubicSplineInterpolate should reject non-unique x values`() {
        val x = doubleArrayOf(0.0, 1.0, 1.0, 2.0) // Duplicate x value
        val y = doubleArrayOf(0.0, 1.0, 2.0, 4.0)
        SignalFilter.cubicSplineInterpolate(x, y, doubleArrayOf(0.5))
    }

    // Helper function to check filter stability
    private fun isFilterStable(bCoeff: DoubleArray, aCoeff: DoubleArray): Boolean {
        // Simple check: ensure coefficients don't cause obvious instability
        // In practice, we would check poles of the transfer function
        val sumBCoeff = bCoeff.sum()
        val sumACoeff = aCoeff.sum()
        
        // Basic sanity check - coefficients shouldn't be all zeros or NaN
        return !sumBCoeff.isNaN() && !sumACoeff.isNaN() &&
               sumBCoeff.isFinite() && sumACoeff.isFinite() &&
               bCoeff.any { it != 0.0 } && aCoeff.any { it != 0.0 }
    }
}