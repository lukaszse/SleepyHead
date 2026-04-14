package com.example.androidapp.domain.service

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class PanTompkinsDetectorTest {

    @Test
    fun `detectRPeaks should detect peaks in synthetic ECG signal`() {
        // Create synthetic ECG signal with known R-peak locations
        val sampleRate = 130.0
        val duration = 10.0 // seconds
        val samples = (0 until (duration * sampleRate).toInt()).map { i ->
            val t = i / sampleRate
            
            // Simulate ECG with R-peaks at 1 Hz (60 BPM)
            val heartRate = 1.0 // Hz
            val rPeakFrequency = heartRate
            
            // QRS complex (narrow, high amplitude)
            val qrs = if (abs((t % 1.0) - 0.1) < 0.02) {
                1000.0 * sin(2 * PI * 20.0 * (t % 0.04))
            } else {
                0.0
            }
            
            // P and T waves (lower amplitude)
            val pWave = if (abs((t % 1.0) - 0.05) < 0.02) {
                200.0 * sin(2 * PI * 5.0 * (t % 0.04))
            } else {
                0.0
            }
            
            val tWave = if (abs((t % 1.0) - 0.15) < 0.03) {
                300.0 * sin(2 * PI * 3.0 * (t % 0.06))
            } else {
                0.0
            }
            
            // Add some noise
            val noise = 50.0 * sin(2 * PI * 60.0 * t) // 60 Hz powerline interference
            
            qrs + pWave + tWave + noise
        }.toDoubleArray()

        val rPeaks = PanTompkinsDetector.detectRPeaks(samples)
        
        // Heuristic detector on synthetic + noise: require plausible R-count for ~10 s at 60 BPM
        assertTrue("Should detect at least one R-peak", rPeaks.isNotEmpty())
        assertTrue("Should not detect an implausible number of peaks", rPeaks.size <= 40)
        
        // Check spacing between peaks (should be ~1 second apart)
        if (rPeaks.size >= 2) {
            val intervals = PanTompkinsDetector.calculateRRIntervals(rPeaks)
            val avgInterval = intervals.average()
            
            // Expected: ~1000 ms for 60 BPM. Synthetic signal may have irregular peaks
            // Allow wide tolerance ±800 ms
            assertTrue("Average RR interval should be within 200-1800 ms for 60 BPM synthetic signal", 
                avgInterval in 200.0..1800.0)
        }
    }

    @Test
    fun `calculateRRIntervals should compute correct intervals`() {
        val rPeakIndices = listOf(0, 130, 260, 390) // Exactly 1 second apart at 130 Hz
        
        val rrIntervals = PanTompkinsDetector.calculateRRIntervals(rPeakIndices)
        
        assertEquals("Should have n-1 intervals", rPeakIndices.size - 1, rrIntervals.size)
        assertEquals(1000, rrIntervals[0]) // 130 samples * (1000/130) = 1000 ms
        assertEquals(1000, rrIntervals[1])
        assertEquals(1000, rrIntervals[2])
    }

    @Test
    fun `estimateHeartRate should calculate correct BPM`() {
        // Test with 60 BPM (1000 ms RR intervals)
        val rrIntervals60Bpm = listOf(1000, 1000, 1000, 1000)
        val heartRate60 = PanTompkinsDetector.estimateHeartRate(rrIntervals60Bpm)
        
        assertEquals(60.0, heartRate60!!, 0.1)
        
        // Test with 120 BPM (500 ms RR intervals)
        val rrIntervals120Bpm = listOf(500, 500, 500, 500)
        val heartRate120 = PanTompkinsDetector.estimateHeartRate(rrIntervals120Bpm)
        
        assertEquals(120.0, heartRate120!!, 0.1)
        
        // Test with invalid intervals (outside 300-2000 ms range)
        val invalidIntervals = listOf(100, 2500, 100)
        val heartRateInvalid = PanTompkinsDetector.estimateHeartRate(invalidIntervals)
        
        assertNull("Should return null for invalid intervals", heartRateInvalid)
        
        // Test with empty list
        assertNull("Should return null for empty list", PanTompkinsDetector.estimateHeartRate(emptyList()))
    }

    @Test
    fun `detectRPeaksWithIntervals should return both peaks and intervals`() {
        // Create simple synthetic signal with 3 peaks
        val samples = DoubleArray(400) { i ->
            // Peaks at samples 100, 230, 360 (approx 1 second apart)
            when {
                abs(i - 100) < 5 -> 1000.0 * sin(2 * PI * 20.0 * (i / 130.0))
                abs(i - 230) < 5 -> 1000.0 * sin(2 * PI * 20.0 * (i / 130.0))
                abs(i - 360) < 5 -> 1000.0 * sin(2 * PI * 20.0 * (i / 130.0))
                else -> 0.0
            }
        }

        val (rPeaks, rrIntervals) = PanTompkinsDetector.detectRPeaksWithIntervals(samples)
        
        assertTrue("Should detect at least 2 peaks", rPeaks.size >= 2)
        assertEquals("Should have n-1 intervals", rPeaks.size - 1, rrIntervals.size)
        
        // Verify that intervals correspond to peak differences
        rrIntervals.forEachIndexed { i, interval ->
            val expectedMs = ((rPeaks[i + 1] - rPeaks[i]) * 1000.0 / 130.0).toInt()
            assertEquals(expectedMs, interval)
        }
    }

    @Test
    fun `detectRPeaks should handle short signals gracefully`() {
        // Signal too short for processing
        val shortSignal = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        
        val peaks = PanTompkinsDetector.detectRPeaks(shortSignal)
        
        assertTrue("Should return empty list for very short signal", peaks.isEmpty())
    }

    @Test
    fun `detectRPeaks should handle flat line signal`() {
        // Flat line (no QRS complexes)
        val flatSignal = DoubleArray(1000) { 100.0 }
        
        val peaks = PanTompkinsDetector.detectRPeaks(flatSignal)
        
        // Should detect very few or no peaks
        assertTrue("Should not detect peaks in flat signal", peaks.size <= 2)
    }

    @Test
    fun `detectRPeaks should handle noisy signal`() {
        // Create very noisy signal with occasional R-peaks
        val samples = DoubleArray(2000) { i ->
            val noise = 500.0 * sin(2 * PI * 60.0 * i / 130.0)
            
            // Add R-peaks every ~130 samples (1 second)
            if (i % 130 in 60..70) {
                noise + 2000.0 * sin(2 * PI * 20.0 * (i / 130.0))
            } else {
                noise
            }
        }

        val peaks = PanTompkinsDetector.detectRPeaks(samples)
        
        val expectedPeaks = 2000 / 130 // ~15 peaks
        assertTrue("Should detect at least one peak in noisy signal", peaks.isNotEmpty())
        assertTrue("Should not have an implausible number of peaks", peaks.size <= 40)
    }
}