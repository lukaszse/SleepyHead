package com.example.androidapp.domain.service

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class EdrExtractorTest {

    @Test
    fun `extractEdrSignal should extract respiratory signal from R-peaks`() {
        // Create synthetic ECG with respiratory modulation
        val sampleRate = 130.0
        val duration = 30.0 // seconds
        val samples = (0 until (duration * sampleRate).toInt()).map { i ->
            val t = i / sampleRate
            
            // Respiratory modulation frequency (0.25 Hz = 15 BPM)
            val respRate = 0.25
            val modulation = 0.3 * sin(2 * PI * respRate * t)
            
            // R-peaks with respiratory modulation
            val heartRate = 1.0 // Hz (60 BPM)
            val rPeakAmplitude = 1000.0 * (1.0 + modulation)
            
            if (abs((t % 1.0) - 0.1) < 0.02) {
                rPeakAmplitude * sin(2 * PI * 20.0 * (t % 0.04))
            } else {
                0.0
            }
        }.toDoubleArray()

        // Detect R-peaks
        val rPeaks = PanTompkinsDetector.detectRPeaks(samples)
        assertTrue("Should detect R-peaks", rPeaks.size >= 20)
        
        // Extract EDR signal
        val (edrTimes, edrValues) = EdrExtractor.extractEdrSignal(samples, rPeaks)
        
        // Should have regular 4 Hz sampling
        assertTrue("Should extract EDR signal", edrTimes.isNotEmpty())
        assertTrue("Should extract EDR signal", edrValues.isNotEmpty())
        assertEquals("Times and values should have same length", edrTimes.size, edrValues.size)
        
        // Check sampling rate (approximately 4 Hz = 250 ms intervals)
        if (edrTimes.size >= 2) {
            val avgInterval = (edrTimes.last() - edrTimes.first()) / (edrTimes.size - 1)
            assertEquals(250.0, avgInterval, 50.0) // Allow ±50 ms tolerance
        }
    }

    @Test
    fun `estimateRespiratoryRate should estimate correct breathing rate`() {
        // Create synthetic EDR signal with known respiratory rate
        val sampleRate = 4.0 // Hz
        val duration = 60.0 // seconds
        val numSamples = (duration * sampleRate).toInt()
        
        val timestamps = DoubleArray(numSamples) { i ->
            i * (1000.0 / sampleRate) // ms
        }
        
        // 0.25 Hz respiratory signal = 15 BPM
        val respiratoryRate = 0.25
        val values = DoubleArray(numSamples) { i ->
            val t = i / sampleRate
            sin(2 * PI * respiratoryRate * t)
        }
        
        val estimatedRate = EdrExtractor.estimateRespiratoryRate(timestamps, values)
        
        assertNotNull("Should estimate respiratory rate", estimatedRate)
        assertTrue(estimatedRate!! in 10.0..22.0) // ~15 BPM with heuristic tolerance
    }

    @Test
    fun `estimateRespiratoryRate should return null for insufficient data`() {
        val shortTimestamps = doubleArrayOf(0.0, 250.0, 500.0)
        val shortValues = doubleArrayOf(0.0, 1.0, 0.0)
        
        val rate = EdrExtractor.estimateRespiratoryRate(shortTimestamps, shortValues)
        
        assertNull("Should return null for insufficient data", rate)
    }

    @Test
    fun `calculateRespiratoryEffortIndex should calculate effort index`() {
        // Test with high amplitude signal (high effort)
        val highEffortSignal = DoubleArray(100) { i ->
            sin(2 * PI * 0.25 * i / 4.0) * 2.0 // Amplitude 2.0
        }
        
        val highEffortIndex = EdrExtractor.calculateRespiratoryEffortIndex(highEffortSignal)
        
        // Test with low amplitude signal (low effort)
        val lowEffortSignal = DoubleArray(100) { i ->
            sin(2 * PI * 0.25 * i / 4.0) * 0.1 // Amplitude 0.1
        }
        
        val lowEffortIndex = EdrExtractor.calculateRespiratoryEffortIndex(lowEffortSignal)
        
        assertTrue("High effort should have higher index", highEffortIndex > lowEffortIndex)
        assertTrue("Index should be in 0-1 range", highEffortIndex in 0.0..1.0)
        assertTrue("Index should be in 0-1 range", lowEffortIndex in 0.0..1.0)
    }

    @Test
    fun `detectRespiratoryPauses should detect pauses in breathing`() {
        // Create EDR signal with a 15-second pause
        val sampleRate = 4.0
        val duration = 120.0 // seconds
        val numSamples = (duration * sampleRate).toInt()
        
        val timestamps = DoubleArray(numSamples) { i ->
            i * (1000.0 / sampleRate)
        }
        
        val values = DoubleArray(numSamples) { i ->
            val t = i / sampleRate
            
            // Normal breathing for first 30s and last 30s
            // Pause from 30s to 45s (15 seconds)
            when {
                t < 30.0 -> sin(2 * PI * 0.25 * t) // Normal breathing
                t < 45.0 -> 0.01 * sin(2 * PI * 0.25 * t) // Very low amplitude (pause)
                else -> sin(2 * PI * 0.25 * t) // Normal breathing again
            }
        }
        
        val pauses = EdrExtractor.detectRespiratoryPauses(
            timestamps,
            values,
            minPauseDurationMs = 10000.0 // 10 seconds minimum
        )
        
        // Should detect one pause of ~15 seconds
        assertEquals("Should detect one pause", 1, pauses.size)
        
        val (start, end) = pauses[0]
        val durationMs = end - start
        
        assertTrue("Pause should be at least 10 seconds", durationMs >= 10000.0)
        assertTrue("Pause should be around 15 seconds", durationMs in 14000.0..16000.0)
        
        // Check pause timing (should be around 30-45 seconds)
        assertTrue("Pause should start around 30s", start in 29000.0..31000.0)
        assertTrue("Pause should end around 45s", end in 44000.0..46000.0)
    }

    @Test
    fun `detectRespiratoryPauses should return empty list for normal breathing`() {
        // Create normal breathing signal without pauses
        val sampleRate = 4.0
        val duration = 60.0
        val numSamples = (duration * sampleRate).toInt()
        
        val timestamps = DoubleArray(numSamples) { i ->
            i * (1000.0 / sampleRate)
        }
        
        val values = DoubleArray(numSamples) { i ->
            val t = i / sampleRate
            sin(2 * PI * 0.25 * t) // Normal breathing
        }
        
        val pauses = EdrExtractor.detectRespiratoryPauses(timestamps, values)
        
        assertTrue("Should not detect pauses in normal breathing", pauses.isEmpty())
    }

    @Test
    fun `extractEdrSignal should handle few R-peaks gracefully`() {
        val ecgSamples = DoubleArray(1000) { 100.0 } // Flat line
        val rPeaks = listOf(100, 230, 360) // Only 3 peaks
        
        val (times, values) = EdrExtractor.extractEdrSignal(ecgSamples, rPeaks)
        
        // With only 3 peaks, interpolation may fail or return empty
        // Either empty result or very short result is acceptable
        assertTrue("Should handle few peaks gracefully", times.size <= 3)
    }

    @Test
    fun `normalizeAmplitudes should handle zero variance signal`() {
        val zeroVarianceSignal = DoubleArray(10) { 5.0 } // All same value
        
        // This should not crash and should return zeros or same values
        val normalized = EdrExtractor::class.java.getDeclaredMethod(
            "normalizeAmplitudes", DoubleArray::class.java
        ).apply { isAccessible = true }
        .invoke(EdrExtractor, zeroVarianceSignal) as DoubleArray
        
        assertTrue("Should handle zero variance", normalized.all { it == 0.0 })
    }
}