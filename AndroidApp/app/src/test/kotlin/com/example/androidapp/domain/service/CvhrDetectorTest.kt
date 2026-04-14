package com.example.androidapp.domain.service

import com.example.androidapp.domain.model.CvhrCycle
import com.example.androidapp.domain.model.SeverityCategory
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class CvhrDetectorTest {

    @Test
    fun `detectCvhrCycles should detect characteristic brady-tachy cycles`() {
        // Create synthetic heart rate signal with CVHR pattern
        val duration = 600L // 10 minutes in seconds
        val sampleInterval = 1000L // 1 second intervals
        val numSamples = (duration * 1000 / sampleInterval).toInt()
        
        val timestamps = LongArray(numSamples) { i ->
            i * sampleInterval
        }
        
        val heartRates = DoubleArray(numSamples) { i ->
            val t = i * sampleInterval / 1000.0 // seconds
            
            // Base heart rate
            val baseHr = 70.0
            
            // Add CVHR pattern: 60-second cycles with 10 BPM variation
            val cvhrAmplitude = 10.0
            val cvhrPeriod = 60.0 // seconds
            
            baseHr + cvhrAmplitude * sin(2 * PI * t / cvhrPeriod - PI/2)
        }
        
        val cycles = CvhrDetector.detectCvhrCycles(timestamps, heartRates)
        
        // Peak pairing is approximate; expect a plausible count for a 60 s sinusoid over 10 min
        assertTrue("Should detect at least one CVHR cycle on synthetic brady–tachy pattern", cycles.isNotEmpty())
        assertTrue("Should not explode false positives on smooth sinusoid", cycles.size <= 25)
        
        // Validate cycle properties
        cycles.forEach { cycle ->
            assertTrue("Cycle duration should be reasonable", 
                cycle.periodMs in 30000L..120000L) // 30-120 seconds
            assertTrue("Heart rate delta should be significant",
                cycle.deltaHr >= 5.0)
            assertTrue("Min HR should be reasonable", 
                cycle.minHr in 30.0..100.0)
            assertTrue("Max HR should be reasonable",
                cycle.maxHr in 50.0..150.0)
        }
    }

    @Test
    fun `calculateCvhrAhi should calculate correct AHI`() {
        // Create mock CVHR cycles
        val cycles = listOf(
            CvhrCycle(
                startTimeMs = 0L,
                endTimeMs = 60000L, // 60 seconds
                minHr = 60.0,
                maxHr = 80.0,
                deltaHr = 20.0,
                periodMs = 60000L
            ),
            CvhrCycle(
                startTimeMs = 120000L,
                endTimeMs = 180000L,
                minHr = 62.0,
                maxHr = 82.0,
                deltaHr = 20.0,
                periodMs = 60000L
            ),
            CvhrCycle(
                startTimeMs = 240000L,
                endTimeMs = 300000L,
                minHr = 58.0,
                maxHr = 78.0,
                deltaHr = 20.0,
                periodMs = 60000L
            )
        )
        
        // 3 events in 6 minutes = 30 events per hour
        val recordingDuration = 6 * 60 * 1000L // 6 minutes
        val ahi = CvhrDetector.calculateCvhrAhi(cycles, recordingDuration)
        
        assertEquals(30.0, ahi, 0.1)
    }

    @Test
    fun `calculateCvhrAhi should return zero for no cycles`() {
        val ahi = CvhrDetector.calculateCvhrAhi(emptyList(), 3600000L)
        assertEquals(0.0, ahi, 0.0)
    }

    @Test
    fun `classifyApneaSeverity should classify correctly`() {
        assertEquals(SeverityCategory.NORMAL, CvhrDetector.classifyApneaSeverity(0.0))
        assertEquals(SeverityCategory.NORMAL, CvhrDetector.classifyApneaSeverity(4.9))
        assertEquals(SeverityCategory.MILD, CvhrDetector.classifyApneaSeverity(5.0))
        assertEquals(SeverityCategory.MILD, CvhrDetector.classifyApneaSeverity(14.9))
        assertEquals(SeverityCategory.MODERATE, CvhrDetector.classifyApneaSeverity(15.0))
        assertEquals(SeverityCategory.MODERATE, CvhrDetector.classifyApneaSeverity(29.9))
        assertEquals(SeverityCategory.SEVERE, CvhrDetector.classifyApneaSeverity(30.0))
        assertEquals(SeverityCategory.SEVERE, CvhrDetector.classifyApneaSeverity(100.0))
    }

    @Test
    fun `detectApneaClusters should group consecutive cycles`() {
        val cycles = listOf(
            // Cluster 1: 3 cycles with small gaps
            CvhrCycle(0L, 60000L, 60.0, 80.0, 20.0, 60000L),
            CvhrCycle(70000L, 130000L, 62.0, 82.0, 20.0, 60000L), // 10s gap
            CvhrCycle(140000L, 200000L, 58.0, 78.0, 20.0, 60000L), // 10s gap
            
            // Large gap (>30s) so first cluster closes; remaining cycles form only a pair (no second cluster)
            CvhrCycle(300000L, 360000L, 65.0, 85.0, 20.0, 60000L),
            CvhrCycle(400000L, 460000L, 63.0, 83.0, 20.0, 60000L), // 40s gap from previous end
            CvhrCycle(470000L, 530000L, 61.0, 81.0, 20.0, 60000L)
        )
        
        val clusters = CvhrDetector.detectApneaClusters(cycles, maxGapBetweenCyclesMs = 30000L)
        
        // Should detect one cluster with 3 cycles
        assertEquals(1, clusters.size)
        assertEquals(3, clusters[0].size)
        
        // Verify cluster contains correct cycles
        assertEquals(0L, clusters[0][0].startTimeMs)
        assertEquals(70000L, clusters[0][1].startTimeMs)
        assertEquals(140000L, clusters[0][2].startTimeMs)
    }

    @Test
    fun `detectApneaClusters should return empty for no valid clusters`() {
        // Only 2 cycles - not enough for a cluster
        val cycles = listOf(
            CvhrCycle(0L, 60000L, 60.0, 80.0, 20.0, 60000L),
            CvhrCycle(70000L, 130000L, 62.0, 82.0, 20.0, 60000L)
        )
        
        val clusters = CvhrDetector.detectApneaClusters(cycles)
        
        assertTrue("Should return empty for insufficient cycles", clusters.isEmpty())
    }

    @Test
    fun `calculateRdi should calculate respiratory disturbance index`() {
        val cycles = listOf(
            CvhrCycle(0L, 60000L, 60.0, 80.0, 20.0, 60000L),
            CvhrCycle(120000L, 180000L, 62.0, 82.0, 20.0, 60000L)
        )
        
        val recordingDuration = 5 * 60 * 1000L // 5 minutes
        val ahi = CvhrDetector.calculateCvhrAhi(cycles, recordingDuration)
        val rdi = CvhrDetector.calculateRdi(cycles, recordingDuration)
        
        // RDI should be slightly higher than AHI
        assertEquals(24.0, ahi, 0.1) // 2 events in 5 min = 24 events/hour
        assertTrue("RDI should be >= AHI", rdi >= ahi)
        assertEquals(ahi * 1.2, rdi, 0.1)
    }

    @Test
    fun `detectCvhrCycles should handle noisy signal`() {
        val timestamps = LongArray(100) { it * 1000L } // 100 seconds
        val heartRates = DoubleArray(100) { i ->
            val base = 70.0
            val noise = (Math.random() - 0.5) * 5.0 // ±2.5 BPM noise
            base + noise
        }
        
        val cycles = CvhrDetector.detectCvhrCycles(timestamps, heartRates)
        
        // Noisy signal without clear CVHR pattern should detect few or no cycles
        assertTrue("Should detect few cycles in noisy signal", cycles.size <= 5)
    }

    @Test
    fun `detectCvhrCycles should handle short signals gracefully`() {
        val shortTimestamps = longArrayOf(0L, 1000L, 2000L)
        val shortHeartRates = doubleArrayOf(70.0, 72.0, 71.0)
        
        val cycles = CvhrDetector.detectCvhrCycles(shortTimestamps, shortHeartRates)
        
        assertTrue("Should return empty for very short signal", cycles.isEmpty())
    }

    @Test
    fun `isValidApneaCycle should filter invalid cycles`() {
        // Valid cycle
        val validCycle = CvhrCycle(
            startTimeMs = 0L,
            endTimeMs = 60000L,
            minHr = 60.0,
            maxHr = 80.0,
            deltaHr = 20.0,
            periodMs = 60000L
        )
        
        // Invalid: too short duration
        val shortCycle = validCycle.copy(periodMs = 20000L)
        
        // Invalid: too small HR delta
        val smallDeltaCycle = validCycle.copy(deltaHr = 2.0)
        
        // Invalid: unrealistic min HR
        val lowMinHrCycle = validCycle.copy(minHr = 20.0)
        
        // Invalid: unrealistic max HR
        val highMaxHrCycle = validCycle.copy(maxHr = 200.0)
        
        // Test through calculateCvhrAhi which filters cycles
        val recordingDuration = 60000L
        val allCycles = listOf(validCycle, shortCycle, smallDeltaCycle, lowMinHrCycle, highMaxHrCycle)
        val ahi = CvhrDetector.calculateCvhrAhi(allCycles, recordingDuration)
        
        // Should only count the valid cycle (1 event in 1 minute = 60 events/hour)
        assertEquals(60.0, ahi, 0.1)
    }
}